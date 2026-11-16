"""Reads `kubectl get pods -o json` on stdin and prints one line per Kafka pod: name, uid, restart count.

Sorted by name, so two snapshots compare with a string test.
"""
import json
import sys

for pod in sorted(json.load(sys.stdin)["items"], key=lambda p: p["metadata"]["name"]):
    restarts = sum(c["restartCount"] for c in pod.get("status", {}).get("containerStatuses", []))
    sys.stdout.write(f'{pod["metadata"]["name"]} {pod["metadata"]["uid"]} {restarts}\n')
