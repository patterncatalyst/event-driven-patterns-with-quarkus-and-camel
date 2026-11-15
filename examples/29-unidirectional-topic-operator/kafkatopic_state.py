"""Reads `kubectl get kafkatopic -o json` on stdin and prints one fact per line.

  names                  the KafkaTopic names, sorted
  not-ready              names whose Ready condition is not True
  spec <name>            "<partitions> <retention.ms or ->" from the spec
  ready <name>           the status of the Ready condition
  reason <name>          "<reason>|<message>" of the Ready condition
  unmanaged <name>       the status of the Unmanaged condition
  finalizers <name>      the finalizers, space separated
  names-topic <topic>    KafkaTopics whose topic name (spec.topicName or metadata.name) is <topic>
"""
import json
import sys


def emit(*parts, file=sys.stdout):
    """Write one line of output for the calling shell script."""
    file.write(" ".join(str(p) for p in parts) + "\n")

items = json.load(sys.stdin)["items"]
by_name = {i["metadata"]["name"]: i for i in items}


def cond(item, kind):
    for c in item.get("status", {}).get("conditions", []):
        if c["type"] == kind:
            return c
    return {}


cmd = sys.argv[1]
arg = sys.argv[2] if len(sys.argv) > 2 else ""
if cmd == "names":
    emit("\n".join(sorted(by_name)))
elif cmd == "not-ready":
    emit("\n".join(sorted(n for n, i in by_name.items() if cond(i, "Ready").get("status") != "True")))
elif cmd == "spec":
    spec = by_name[arg]["spec"]
    emit(spec.get("partitions"), spec.get("config", {}).get("retention.ms", "-"))
elif cmd == "ready":
    emit(cond(by_name[arg], "Ready").get("status", "none"))
elif cmd == "reason":
    c = cond(by_name[arg], "Ready")
    emit(f'{c.get("reason", "")}|{c.get("message", "")}')
elif cmd == "unmanaged":
    emit(cond(by_name[arg], "Unmanaged").get("status", "none"))
elif cmd == "finalizers":
    emit(" ".join(by_name[arg]["metadata"].get("finalizers", [])))
elif cmd == "names-topic":
    emit("\n".join(sorted(n for n, i in by_name.items() if i["spec"].get("topicName", n) == arg)))
