"""Reads the output of kafka-metadata-quorum.sh on stdin and prints one summary line.

usage: python3 -I quorum.py status        -> leader=<id> voters=<ids> observers=<ids>
       python3 -I quorum.py replication   -> <id>=<Leader|Follower|Observer> ... (by id)
"""
import re
import sys


def emit(*parts, file=sys.stdout):
    """Write one line of output for the calling shell script."""
    file.write(" ".join(str(p) for p in parts) + "\n")


def ids(line):
    return ",".join(sorted(re.findall(r'"id"\s*:\s*(\d+)', line), key=int))


def status(text):
    leader = re.search(r"^LeaderId:\s*(\d+)", text, re.M)
    voters = re.search(r"^CurrentVoters:(.*)$", text, re.M)
    observers = re.search(r"^CurrentObservers:(.*)$", text, re.M)
    emit(f"leader={leader.group(1) if leader else ''} "
          f"voters={ids(voters.group(1)) if voters else ''} "
          f"observers={ids(observers.group(1)) if observers else ''}")


def replication(text):
    rows = []
    for line in text.splitlines():
        cols = line.split()
        if cols and cols[0].isdigit() and cols[-1] in ("Leader", "Follower", "Observer"):
            rows.append((int(cols[0]), cols[-1]))
    emit(" ".join(f"{i}={s}" for i, s in sorted(rows)))


if __name__ == "__main__":
    {"status": status, "replication": replication}[sys.argv[1]](sys.stdin.read())
