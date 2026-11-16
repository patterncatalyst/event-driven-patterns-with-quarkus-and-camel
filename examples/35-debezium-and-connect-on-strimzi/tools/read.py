"""Reads kafka-console-consumer output or Connect offset JSON from stdin and prints one answer.

  read.py <command> [args]
  keys                  the key of every record, one per line
  header <name>         the value of that header on every record, one per line
  ops <op> [qty]        how many envelopes carry op <op>, optionally with after.qty_on_hand == qty
  doc <sku>             quantityOnHand of the latest document for the key <sku>
  lsn                   the lsn of the first offset in a Connect offsets body
"""

import json
import sys


def out(value):
    sys.stdout.write(f"{value}\n")


def records(text):
    """(headers, key, value) of each record line; the consumer prints notices without tabs, which are skipped."""
    for line in text.splitlines():
        parts = line.split("\t")
        if len(parts) >= 3:
            yield parts[0], parts[1], "\t".join(parts[2:])


def loads(value):
    try:
        return json.loads(value)
    except ValueError:
        return None


def main(argv):
    command, args, text = argv[1], argv[2:], sys.stdin.read()
    if command == "keys":
        out("\n".join(key for _, key, _ in records(text)))
    elif command == "header":
        for headers, _, _ in records(text):
            pairs = dict(h.split(":", 1) for h in headers.split(",") if ":" in h)
            if args[0] in pairs:
                out(pairs[args[0]])
    elif command == "ops":
        count = 0
        for _, _, value in records(text):
            envelope = loads(value)
            if not isinstance(envelope, dict) or envelope.get("op") != args[0]:
                continue
            if len(args) > 1 and (envelope.get("after") or {}).get("qty_on_hand") != int(args[1]):
                continue
            count += 1
        out(count)
    elif command == "doc":
        latest = ""
        for _, key, value in records(text):
            document = loads(value)
            if key == args[0] and isinstance(document, dict):
                latest = str(document.get("quantityOnHand", ""))
        out(latest)
    elif command == "lsn":
        body = loads(text)
        offsets = body.get("offsets", []) if isinstance(body, dict) else []
        out(offsets[0]["offset"]["lsn"] if offsets else "")
    else:
        sys.exit("unknown command: " + command)


main(sys.argv)
