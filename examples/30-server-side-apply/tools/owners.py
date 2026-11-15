"""Print "<manager> <operation>" for every managedFields entry that owns a field path.

Reads `kubectl get ... --show-managed-fields -o json` on stdin. Each argument is one path segment; a segment
that does not start with `k:` or `v:` is looked up as `f:<segment>`.
"""
import json
import sys


def owns(tree, segments):
    for segment in segments:
        key = segment if segment.startswith(("k:", "v:")) else "f:" + segment
        if not isinstance(tree, dict) or key not in tree:
            return False
        tree = tree[key]
    return True


def main(segments):
    doc = json.load(sys.stdin)
    for entry in doc["metadata"].get("managedFields", []):
        if owns(entry.get("fieldsV1", {}), segments):
            print(entry["manager"], entry["operation"])


if __name__ == "__main__":
    main(sys.argv[1:])
