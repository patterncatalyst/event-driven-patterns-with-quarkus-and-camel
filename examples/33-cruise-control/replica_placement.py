"""Reads `kafka-topics.sh --describe` on stdin and prints one fact about the edp.* topics.

  counts            "<id>=<replicas>" per broker id, sorted, space separated
  count <id>        replicas held by the broker
  partitions        partitions seen
  bad-rf <n>        partitions whose replica list does not have n entries
  urp               partitions whose in-sync replicas differ from their replicas
  outside "<ids>"   replica entries on a broker that is not in the space separated id list
"""
import collections
import re
import sys



def emit(value):
    """Write one line for the calling shell script."""
    sys.stdout.write(f"{value}\n")


LINE = re.compile(r"Topic: (\S+)\s+Partition: (\d+)\s+Leader: (-?\d+)\s+Replicas: ([\d,]+)\s+Isr: ([\d,]*)")


def partitions(text):
    for line in text.splitlines():
        m = LINE.search(line)
        if m and m.group(1).startswith("edp."):
            yield m.group(1), m.group(4).split(","), set(m.group(5).split(","))


rows = list(partitions(sys.stdin.read()))
held = collections.Counter(b for _, replicas, _ in rows for b in replicas)
cmd = sys.argv[1]
if cmd == "counts":
    emit(" ".join(f"{b}={held[b]}" for b in sorted(held, key=int)))
elif cmd == "count":
    emit(held[sys.argv[2]])
elif cmd == "partitions":
    emit(len(rows))
elif cmd == "bad-rf":
    emit(sum(1 for _, replicas, _ in rows if len(replicas) != int(sys.argv[2])))
elif cmd == "urp":
    emit(sum(1 for _, replicas, isr in rows if set(replicas) != isr))
elif cmd == "outside":
    allowed = set(sys.argv[2].split())
    emit(sum(1 for _, replicas, _ in rows for b in replicas if b not in allowed))
else:
    sys.exit(f"unknown command {cmd}")
