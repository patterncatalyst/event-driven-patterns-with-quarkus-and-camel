"""Prints the topic names in Topics.ALL of the domain model, one per line, resolved from the constants."""
import re
import sys


def emit(*parts, file=sys.stdout):
    """Write one line of output for the calling shell script."""
    file.write(" ".join(str(p) for p in parts) + "\n")

source = open(sys.argv[1], encoding="utf-8").read()
constants = dict(re.findall(r'public static final String (\w+)\s*=\s*"([^"]+)";', source))
body = re.search(r"ALL\s*=\s*List\.of\((.*?)\);", source, re.S).group(1)
for name in re.findall(r"[A-Z][A-Z0-9_]+", body):
    emit(constants[name])
