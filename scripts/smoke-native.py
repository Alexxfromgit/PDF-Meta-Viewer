#!/usr/bin/env python3
"""Exercise PDF extraction through the app's bundled runtime and native launcher."""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("launcher", type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
with tempfile.TemporaryDirectory(prefix="pdf-meta-native-") as folder:
    output = Path(folder) / "export with spaces.json"
    subprocess.run([str(args.launcher.resolve()), str(root / "src/test/resources/Amazon-S3-Tutorial.pdf"), str(output)],
                   check=True, timeout=60)
    document = json.loads(output.read_text(encoding="utf-8"))
    if document["metadata"].get("Content-Type") != "application/pdf" or "content" in document:
        raise SystemExit("Native runtime did not produce the expected metadata export.")
print("Native launcher and bundled runtime passed PDF extraction/export smoke test.")
