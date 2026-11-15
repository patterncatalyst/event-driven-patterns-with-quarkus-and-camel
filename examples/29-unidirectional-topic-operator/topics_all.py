"""Prints the topic names in Topics.ALL of the domain model, one per line, resolved from the constants."""
import re
import sys

source = open(sys.argv[1], encoding="utf-8").read()
constants = dict(re.findall(r'public static final String (\w+)\s*=\s*"([^"]+)";', source))
body = re.search(r"ALL\s*=\s*List\.of\((.*?)\);", source, re.S).group(1)
for name in re.findall(r"[A-Z][A-Z0-9_]+", body):
    print(constants[name])
