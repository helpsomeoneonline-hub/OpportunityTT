#!/usr/bin/env python3
"""Validate manually reviewed image-based classified records before packaging."""
from collections import Counter
from pathlib import Path
import json

path = Path(__file__).resolve().parents[1] / "app/src/main/assets/classified_jobs_20261017.json"
data = json.loads(path.read_text(encoding="utf-8"))
assert data["edition"] == "2026-10-17"
items = data["jobs"]
assert len(items) >= 80, f"Unexpected extraction count: {len(items)}"
ids = [x["id"] for x in items]
assert len(ids) == len(set(ids)), "Duplicate listing ID"
for x in items:
    assert x["sourcePage"] in (22, 23, 24), x
    assert x["sourceDate"] == "2026-10-17", x
    assert x["title"].strip(), x
    assert x["contact"].strip(), "No advertiser contact: " + x["id"]
    assert x["employer"].strip(), x
    assert x["category"].strip(), x
    assert x["activeStatus"] == "Unconfirmed; verify with advertiser", x
    assert not any(t in x["title"].lower() for t in ("sample job", "demo candidate")), x
print("Validated", len(items), "newspaper-advertised positions")
print("Pages:", dict(sorted(Counter(x["sourcePage"] for x in items).items())))
print("Review-needed:", sum(x["needsReview"] for x in items))
