"""Reads `kubectl get pods -o json` on stdin and checks every pod against the restricted Pod Security Standard.

usage: kubectl get pods -o json | python3 -I restricted.py <minimum pods>

Every pod must be Running, and every container and init container must disallow privilege escalation, drop ALL
capabilities and run as non-root with a RuntimeDefault or Localhost seccomp profile, set on the pod or the
container. Exits 1 with the violations on stderr, or when fewer pods than the minimum were found.
"""
import json
import sys


def emit(*parts, file=sys.stdout):
    """Write one line of output for the calling shell script."""
    file.write(" ".join(str(p) for p in parts) + "\n")


def effective(pod_sc, ctr_sc, key):
    return ctr_sc[key] if key in ctr_sc else pod_sc.get(key)


def violations(pod):
    name = pod["metadata"]["name"]
    spec = pod["spec"]
    pod_sc = spec.get("securityContext") or {}
    found = []
    if pod["status"].get("phase") != "Running":
        found.append(f"{name}: phase is {pod['status'].get('phase')}")
    for ctr in spec.get("containers", []) + spec.get("initContainers", []):
        sc = ctr.get("securityContext") or {}
        who = f"{name}/{ctr['name']}"
        if sc.get("allowPrivilegeEscalation") is not False:
            found.append(f"{who}: allowPrivilegeEscalation is not false")
        if "ALL" not in (sc.get("capabilities") or {}).get("drop", []):
            found.append(f"{who}: capabilities do not drop ALL")
        if effective(pod_sc, sc, "runAsNonRoot") is not True:
            found.append(f"{who}: runAsNonRoot is not true")
        seccomp = effective(pod_sc, sc, "seccompProfile") or {}
        if seccomp.get("type") not in ("RuntimeDefault", "Localhost"):
            found.append(f"{who}: seccompProfile is {seccomp.get('type')}")
    return found


def main():
    minimum = int(sys.argv[1])
    pods = json.load(sys.stdin)["items"]
    # Pods that finished (a completed Job) are not running workloads
    pods = [p for p in pods if p["status"].get("phase") not in ("Succeeded",)]
    problems = [v for p in pods for v in violations(p)]
    if len(pods) < minimum:
        problems.append(f"found {len(pods)} pods, expected at least {minimum}")
    for line in problems:
        emit(line, file=sys.stderr)
    return 1 if problems else 0


sys.exit(main())
