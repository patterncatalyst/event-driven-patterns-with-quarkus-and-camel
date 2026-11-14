"""The decision table: reason by attempt 1 to 3, each cell RESHIP or REFUND with the rule that makes it. The cells are generated from the same table the tests and verify.sh use as their oracle."""

from generate_diagram import emit

# reason -> (decision for attempt 1, 2, 3) with the attempt limit at 3
TABLE = [
    ("DAMAGED", ["RESHIP", "REFUND", "REFUND"], ["first attempt", "later attempt", "later attempt"]),
    ("LOST", ["RESHIP", "REFUND", "REFUND"], ["first attempt", "later attempt", "later attempt"]),
    ("RECIPIENT_ABSENT", ["RESHIP", "RESHIP", "REFUND"], ["under limit", "under limit", "at limit"]),
    ("DELAYED", ["RESHIP", "RESHIP", "REFUND"], ["under limit", "under limit", "at limit"]),
    ("ADDRESS_PROBLEM", ["REFUND"] * 3, ["address or refused"] * 3),
    ("REFUSED", ["REFUND"] * 3, ["address or refused"] * 3),
    ("UNKNOWN", ["REFUND"] * 3, ["unknown reason"] * 3),
]

X0, Y0 = 250, 90
CELL_W, CELL_H, GAP = 220, 44, 8

nodes = []
for col, attempt in enumerate((1, 2, 3)):
    nodes.append({"x": X0 + col * (CELL_W + GAP), "y": 40, "w": CELL_W, "h": 34, "style": "ink", "lines": [f"Attempt {attempt}"]})
for row, (reason, decisions, rules) in enumerate(TABLE):
    y = Y0 + row * (CELL_H + GAP)
    nodes.append({"x": 30, "y": y, "w": 200, "h": CELL_H, "style": "box", "lines": [reason]})
    for col, (decision, rule) in enumerate(zip(decisions, rules)):
        nodes.append({"x": X0 + col * (CELL_W + GAP), "y": y, "w": CELL_W, "h": CELL_H,
                      "style": "accent" if decision == "RESHIP" else "ghost", "lines": [decision, rule]})

emit(
    "24-rules-table",
    960,
    90 + len(TABLE) * (CELL_H + GAP) + 40,
    bands=[],
    nodes=nodes,
    edges=[],
    notes=[
        {"x": 480, "y": 90 + len(TABLE) * (CELL_H + GAP) + 20, "anchor": "middle", "bold": False, "size": 12, "color": "#555555",
         "text": "Exactly one rule fires per cell; the attempt limit is the one parameter, 3 here"},
    ],
)
