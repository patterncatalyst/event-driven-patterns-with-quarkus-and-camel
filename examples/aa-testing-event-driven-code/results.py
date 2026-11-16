"""Summarise the Surefire reports of one test class: how many tests ran and which of them failed or errored.

usage: python3 -I results.py <surefire-reports-dir> <test class simple name>
prints: ran=<n> failed=<name,name,...>
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

reports, cls = Path(sys.argv[1]), sys.argv[2]
ran, failed = 0, []
for report in sorted(reports.glob(f"TEST-*.{cls}.xml")):
    for case in ET.parse(report).getroot().iter("testcase"):
        ran += 1
        if case.find("failure") is not None or case.find("error") is not None:
            failed.append(case.get("name").removesuffix("()"))
print(f"ran={ran} failed={','.join(sorted(failed))}")
