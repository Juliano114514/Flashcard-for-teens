"""Read-only current English CSV validation; historical Chinese rebuilding retired."""
from __future__ import annotations
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
from english_wordlist import CSV, COLUMNS, validate


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", required=True)
    parser.parse_args()
    content = CSV.read_bytes()
    data = validate(content)
    examples = [example for row in data for example in json.loads(row["例句JSON"])]
    actual = {"schemaVersion": 3, "words": len(data), "columns": list(COLUMNS),
        "grades": dict(sorted(Counter(row["年级"] for row in data).items())),
        "examples": len(examples), "chunks": sum(len(example["chunks"]) for example in examples),
        "bytes": len(content), "sha256": hashlib.sha256(content).hexdigest()}
    snapshot = Path(__file__).resolve().parents[1] / "docs/wordlist-validation.json"
    if actual != json.loads(snapshot.read_text(encoding="utf-8")):
        raise ValueError("Current CSV differs from its snapshot manifest")
    print(json.dumps(dict(actual, status="PASS"), ensure_ascii=False))


if __name__ == "__main__":
    main()
