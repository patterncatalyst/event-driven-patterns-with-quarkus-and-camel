"""mm2_state.py <connector>: reads a KafkaMirrorMaker2 as JSON on stdin and prints the state of the named connector
followed by the state of each of its tasks, for example `RUNNING RUNNING`. Prints nothing when the status does not
list the connector. Standard library only; run as `python3 -I`."""
import json
import sys


def main() -> int:
    name = sys.argv[1]
    for connector in json.load(sys.stdin).get("status", {}).get("connectors", []):
        if connector.get("name") == name:
            states = [connector.get("connector", {}).get("state", "")]
            states += [task.get("state", "") for task in connector.get("tasks", [])]
            sys.stdout.write(" ".join(states) + "\n")
            return 0
    return 0


if __name__ == "__main__":
    sys.exit(main())
