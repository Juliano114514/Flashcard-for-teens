"""Prepare a JSON matrix for artifact-tool authoring; never writes the CSV.

Only the three approved Chinese pinyin fields are added. Original content and
word-level IPA remain exact values. Mixed text is retained for system fallback.
"""
from __future__ import annotations
import argparse
import csv
import hashlib
import json
from pathlib import Path
import re
import sys

from english_wordlist import COLUMNS


def prepare(source: Path, output: Path) -> None:
    import evaluate_melo_tts as runtime
    runtime.prepare_runtime()
    from pypinyin import Style, lazy_pinyin
    overrides = json.loads(Path(__file__).with_name("pinyin_phrase_overrides.json").read_text(encoding="utf-8"))
    if overrides["schemaVersion"] != 1:
        raise ValueError("Unsupported pinyin correction schema")
    phrases = sorted(overrides["phrases"].items(), key=lambda item: len(item[0]), reverse=True)
    cache = {}
    han_run = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff\U00020000-\U000323af]+|[^\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff\U00020000-\U000323af]+")
    def reading(text: str) -> str:
        if text not in cache:
            # Citation tones are recorded here; the existing Melo frontend owns
            # contextual sandhi. Phrase dictionaries sometimes pre-apply 一/不
            # sandhi, so restore those two citation tones after contextual lookup.
            # Keep non-Han runs exactly, including ASCII case, for system fallback.
            pieces = []
            for match in han_run.finditer(text):
                run = match.group()
                if not re.match(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff\U00020000-\U000323af]", run):
                    pieces.append(run)
                    continue
                syllables = lazy_pinyin(run, style=Style.TONE, errors="default", strict=True)
                if len(syllables) != len(run):
                    raise ValueError("Contextual pinyin no longer aligns one syllable per Han character")
                # Apply reviewed phrases by character offset, so shorter adjacent
                # phrases cannot greedily capture part of a different word.
                used = set()
                for phrase, replacement in phrases:
                    values = replacement.split()
                    if len(values) != len(phrase):
                        # Non-Han phrases are handled on the whole text below.
                        continue
                    start = 0
                    while (start := run.find(phrase, start)) >= 0:
                        positions = set(range(start, start + len(phrase)))
                        if not positions & used:
                            syllables[start:start + len(phrase)] = values
                            used.update(positions)
                        start += len(phrase)
                pieces.extend("yī" if char == "一" else "bù" if char == "不" else syllable
                              for char, syllable in zip(run, syllables, strict=True))
            cache[text] = " ".join(pieces)
            if text in overrides["exactTexts"]:
                cache[text] = overrides["exactTexts"][text]
            # The only reviewed phrase crossing punctuation is a verb gloss.
            if "教，讲授" in text:
                cache[text] = cache[text].replace("jiào ， jiǎng shòu", "jiāo ， jiǎng shòu")
            if "不了，谢谢" in text:
                cache[text] = cache[text].replace("bù liǎo ， xiè", "bù le ， xiè")
            if "剪，切，割" in text:
                cache[text] = cache[text].replace("jiǎn ， qiè ， gē", "jiǎn ， qiē ， gē")
        return cache[text]
    with source.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        old_columns = tuple(reader.fieldnames or ())
        original = list(reader)
    if old_columns not in (COLUMNS, tuple(name for name in COLUMNS if name != "中文释义拼音")):
        raise ValueError("Expected the current twelve-column or protected previous eleven-column CSV")
    if old_columns == COLUMNS:
        from english_wordlist import rows
        rows(source)
    matrix = [list(COLUMNS)]
    mixed = []
    count = 0
    for old in original:
        row = dict(old)
        row["中文释义拼音"] = reading(row["中文释义"])
        count += 1
        texts = [("definition", row["中文释义"])]
        examples = json.loads(row["例句JSON"])
        for index, example in enumerate(examples):
            example["chinesePinyin"] = reading(example["chinese"])
            count += 1
            texts.append((f"example[{index}]", example["chinese"]))
            for part, chunk in enumerate(example["chunks"]):
                chunk["chinesePinyin"] = reading(chunk["chinese"])
                count += 1
                texts.append((f"example[{index}].chunk[{part}]", chunk["chinese"]))
        row["例句JSON"] = json.dumps(examples, ensure_ascii=False, separators=(",", ":"))
        for field, text in texts:
            if re.search(r"[A-Za-z0-9]", text):
                mixed.append({"id": row["词条ID"], "field": field, "text": text})
        matrix.append([row[name] for name in COLUMNS])
    output.write_text(json.dumps(matrix, ensure_ascii=False), encoding="utf-8")
    output.with_suffix(".report.json").write_text(json.dumps({"words": len(original),
        "pinyinValues": count, "distinctChineseTexts": len(cache), "mixedFields": mixed,
        "protectedSourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "method": "pypinyin 0.55.0 with reviewed contextual phrase corrections; 一/不 citation tones; retained mixed non-Han text; no Android dependency",
        "correctionSha256": hashlib.sha256(Path(__file__).with_name("pinyin_phrase_overrides.json").read_bytes()).hexdigest(),
        "limitations": "Automatic contextual pinyin is not individual human pronunciation certification."},
        ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"words": len(original), "pinyinValues": count, "mixedFields": len(mixed)}))


def verify(source: Path, candidate: Path) -> None:
    from english_wordlist import rows
    with source.open(encoding="utf-8-sig", newline="") as stream:
        original = list(csv.DictReader(stream))
    actual = rows(candidate)
    if len(original) != len(actual):
        raise ValueError("Row count changed")
    for before, after in zip(original, actual, strict=True):
        for name, value in before.items():
            if name == "中文释义拼音":
                continue
            if name != "例句JSON":
                if after[name] != value:
                    raise ValueError(f"Protected field changed: {before['词条ID']} {name}")
            else:
                def teaching_only(encoded: str) -> list:
                    clean = json.loads(encoded)
                    for example in clean:
                        example.pop("chinesePinyin", None)
                        for chunk in example["chunks"]:
                            chunk.pop("chinesePinyin", None)
                    return clean
                if teaching_only(after[name]) != teaching_only(value):
                    raise ValueError(f"Protected example content changed: {before['词条ID']}")
    print(f"Protected ID/order/content/IPA verification passed: {len(actual)} rows")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--matrix", type=Path)
    action.add_argument("--verify", type=Path)
    args = parser.parse_args()
    prepare(args.source, args.matrix) if args.matrix else verify(args.source, args.verify)
