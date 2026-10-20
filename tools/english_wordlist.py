"""Shared read-only validation of the English-centred twelve-column wordlist.

Limits retain the importer safety envelope: UTF-8, 32 MiB/file, 32 KiB/CSV
record, 20,000 rows, bounded UTF-16 fields and JSON depth/collection sizes.
Headword identity is NFC and case-sensitive; CSV IPA is never rewritten.
"""
from __future__ import annotations
import csv
import io
import json
from pathlib import Path
import re
import unicodedata

ROOT = Path(__file__).resolve().parents[1]
CSV = ROOT / "wordlist.csv"
COLUMNS = ("年级", "英语词条", "音标", "词条ID", "中文释义", "中文释义拼音", "词性",
           "例句JSON", "干扰词ID", "教材来源JSON", "发音说明", "来源说明")
GRADES = {f"{grade}.{half}" for grade in range(3, 7) for half in (0, 5)}
ID = re.compile(r"[A-Za-z0-9_]{1,92}")
HAN = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff\U00020000-\U000323af]")


def text(value: object, maximum: int, context: str, optional: bool = False) -> str:
    if (not isinstance(value, str) or "\0" in value
            or len(value.encode("utf-16-le", errors="surrogatepass")) // 2 > maximum
            or not optional and not value.strip()):
        raise ValueError(f"Blank, oversized or invalid text: {context}")
    return value


def english(value: object, maximum: int, context: str) -> str:
    value = text(value, maximum, context)
    if not re.search(r"[A-Za-z]", value) or HAN.search(value):
        raise ValueError(f"English requires Latin letters and no Han text: {context}")
    return value


def array(value: str, maximum: int, context: str) -> list:
    text(value, maximum, context)
    depth, quoted, escaped = 0, False, False
    for char in value:
        if quoted:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                quoted = False
        elif char == '"':
            quoted = True
        elif char in "[{":
            depth += 1
            if depth > 4:
                raise ValueError(f"JSON nesting exceeds four levels: {context}")
        elif char in "]}":
            depth -= 1
            if depth < 0:
                raise ValueError(f"Unbalanced JSON: {context}")
    if depth or quoted:
        raise ValueError(f"Unclosed JSON: {context}")
    result = json.loads(value)
    if not isinstance(result, list):
        raise ValueError(f"Expected a JSON array: {context}")
    return result


def keys(value: object, required: set[str], optional: set[str], context: str) -> None:
    if not isinstance(value, dict) or not required <= value.keys() or value.keys() - required - optional:
        raise ValueError(f"Unexpected or missing JSON keys: {context}")


def validate(content: bytes) -> list[dict]:
    if len(content) > 32 * 1024 * 1024:
        raise ValueError("CSV exceeds 32 MiB")
    decoded = content.decode("utf-8-sig")
    # Enforce the app parser's quote rules, beyond csv.reader(strict=True).
    state = 0
    for char in decoded:
        if char in "\r\n":
            if state != 2:
                state = 0
        elif state == 0:
            if char == '"':
                state = 2
            elif char != ",":
                state = 1
        elif state == 1:
            if char == '"':
                raise ValueError("A CSV quote must start a quoted field")
            if char == ",":
                state = 0
        elif state == 2:
            if char == '"':
                state = 3
        elif char == '"':
            state = 2
        elif char == ",":
            state = 0
        else:
            raise ValueError("Only a comma or newline may follow a closing CSV quote")
    if state == 2:
        raise ValueError("Unclosed CSV quote")
    physical_lines = re.findall(r"[^\r\n]*(?:\r\n|\r|\n|$)", decoded)
    reader = csv.reader(io.StringIO(decoded, newline=""), strict=True)
    if tuple(next(reader, ())) != COLUMNS:
        raise ValueError("Expected the English twelve-column CSV contract")
    previous = reader.line_num
    if len("".join(physical_lines[:previous]).encode("utf-8")) > 32 * 1024:
        raise ValueError("CSV header exceeds 32 KiB")
    result = []
    for fields in reader:
        raw = "".join(physical_lines[previous:reader.line_num])
        previous = reader.line_num
        if len(raw.encode("utf-8")) > 32 * 1024:
            raise ValueError("CSV record exceeds 32 KiB")
        if not fields or len(fields) == 1 and not fields[0].strip():
            continue
        if len(fields) != len(COLUMNS):
            raise ValueError("CSV width mismatch")
        result.append(dict(zip(COLUMNS, fields)))
        if len(result) > 20_000:
            raise ValueError("CSV exceeds 20,000 rows")
    if not result:
        raise ValueError("Empty vocabulary")
    by_id = {row["词条ID"]: row for row in result}
    identities = {unicodedata.normalize("NFC", row["英语词条"].strip()) for row in result}
    if len(by_id) != len(result) or len(identities) != len(result):
        raise ValueError("Duplicate ID or case-sensitive English headword identity")
    for row in result:
        context = row["词条ID"]
        for name, maximum in (("年级", 3), ("英语词条", 300), ("音标", 1000), ("词条ID", 92),
                              ("中文释义", 1000), ("中文释义拼音", 4000), ("词性", 80), ("来源说明", 2000)):
            text(row[name], maximum, f"{context} {name}")
        text(row["发音说明"], 2000, context, optional=True)
        if row["年级"] not in GRADES or not ID.fullmatch(context):
            raise ValueError(f"Invalid grade or ID: {context}")
        english(row["英语词条"], 300, context)
        # Multiple slash-delimited pronunciations are permitted verbatim.
        if not row["音标"].startswith("/") or not row["音标"].endswith("/"):
            raise ValueError(f"Invalid word IPA delimiters: {context}")
        distractors = array(row["干扰词ID"], 400, context)
        if (len(distractors) != 3 or any(not isinstance(item, str) or not ID.fullmatch(item) for item in distractors)
                or len(set(distractors)) != 3 or any(item not in by_id or item == context for item in distractors)):
            raise ValueError(f"Invalid distractors: {context}")
        if len({unicodedata.normalize("NFC", by_id[item]["中文释义"].strip()) for item in [context, *distractors]}) != 4:
            raise ValueError(f"Repeated Chinese choice text: {context}")
        examples = array(row["例句JSON"], 16000, context)
        if not 1 <= len(examples) <= 16:
            raise ValueError(f"Expected 1–16 examples: {context}")
        for example in examples:
            keys(example, {"english", "literalChinese", "chinese", "chinesePinyin", "chunks"}, {"wordIpa"}, context)
            english(example["english"], 2000, context)
            for name, maximum in (("literalChinese", 4000), ("chinese", 1000), ("chinesePinyin", 4000)):
                text(example[name], maximum, context)
            if "wordIpa" in example:
                text(example["wordIpa"], 1000, context)
            chunks = example["chunks"]
            if not isinstance(chunks, list) or not 1 <= len(chunks) <= 64:
                raise ValueError(f"Invalid example chunks: {context}")
            for chunk in chunks:
                keys(chunk, {"english", "chinese", "chinesePinyin"}, set(), context)
                english(chunk["english"], 2000, context)
                text(chunk["chinese"], 1000, context)
                text(chunk["chinesePinyin"], 4000, context)
            if " ".join(chunk["english"] for chunk in chunks) != example["english"]:
                raise ValueError(f"English chunks do not reconstruct sentence: {context}")
            if " | ".join(chunk["chinese"] for chunk in chunks) != example["literalChinese"]:
                raise ValueError(f"Chinese literal line differs from chunks: {context}")
        sources = array(row["教材来源JSON"], 16000, context)
        if not 1 <= len(sources) <= 64:
            raise ValueError(f"Invalid provenance count: {context}")
        for source in sources:
            keys(source, {"grade", "file", "line", "page", "section", "module", "original", "chinese", "starred"}, set(), context)
            if source["grade"] not in GRADES or type(source["line"]) is not int or not 0 <= source["line"] <= 1_000_000 or type(source["starred"]) is not bool:
                raise ValueError(f"Invalid provenance metadata types: {context}")
            for name in ("file", "page", "section", "module", "original", "chinese"):
                text(source[name], 2000, context, optional=name in {"file", "module"})
    return result


def rows(path: Path = CSV) -> list[dict]:
    return validate(path.read_bytes())


if __name__ == "__main__":
    data = rows()
    examples = [example for row in data for example in json.loads(row["例句JSON"])]
    print(json.dumps({"words": len(data), "columns": len(COLUMNS), "examples": len(examples),
                      "chunks": sum(len(example["chunks"]) for example in examples)}, ensure_ascii=False))
