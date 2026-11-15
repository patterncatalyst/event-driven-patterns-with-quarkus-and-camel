"""Compares the environment variable names of a compose service with those of a Kubernetes Deployment.

usage: python3 -I envcompare.py <compose-config.json> <deployment.json> <compose-service> <deployment>

Exits 0 when the two sets of names are equal; prints the names that differ otherwise.
Prints the names whose values differ (the addresses) on stdout either way.
"""
import json
import sys


def emit(*parts, file=sys.stdout):
    """Write one line of output for the calling shell script."""
    file.write(" ".join(str(p) for p in parts) + "\n")


def main():
    compose_file, deploy_file, service, deployment = sys.argv[1:5]
    with open(compose_file, encoding="utf-8") as f:
        compose = json.load(f)["services"][service].get("environment", {})
    with open(deploy_file, encoding="utf-8") as f:
        d = json.load(f)
    if d["metadata"]["name"] != deployment:
        emit(f"unexpected deployment {d['metadata']['name']}")
        return 1
    containers = d["spec"]["template"]["spec"]["containers"]
    env = {e["name"]: e.get("value", "<secret>") for c in containers for e in c.get("env", [])}
    only_compose = sorted(set(compose) - set(env))
    only_kube = sorted(set(env) - set(compose))
    changed = sorted(n for n in set(compose) & set(env) if str(compose[n]) != env[n])
    emit("values that change:", ", ".join(changed))
    if only_compose or only_kube:
        emit("only in compose:", ", ".join(only_compose))
        emit("only in the Deployment:", ", ".join(only_kube))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
