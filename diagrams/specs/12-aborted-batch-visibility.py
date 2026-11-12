"""The output log while a crashed transaction is open and after the restart: what each isolation level reads."""

from generate_diagram import emit

emit(
    "12-aborted-batch-visibility",
    960,
    520,
    bands=[
        {"x": 20, "y": 50, "w": 920, "h": 210, "fill": "#fafafa", "label": "Service stopped inside the transaction"},
        {"x": 20, "y": 290, "w": 920, "h": 210, "fill": "#fafafa", "label": "After the restart"},
    ],
    nodes=[
        {"x": 40, "y": 90, "w": 170, "h": 60, "style": "box", "lines": ["O-5001 to O-5005", "committed"]},
        {"x": 230, "y": 90, "w": 90, "h": 60, "style": "ink", "lines": ["commit", "marker"]},
        {"x": 340, "y": 90, "w": 180, "h": 60, "style": "ghost", "lines": ["O-5101 to O-5107", "transaction open"]},
        {"x": 40, "y": 180, "w": 480, "h": 40, "style": "accent", "lines": ["last stable offset: read_committed stops before the open records"]},
        {"x": 580, "y": 90, "w": 340, "h": 60, "style": "box", "lines": ["read_uncommitted", "5 + 7 records"]},
        {"x": 580, "y": 170, "w": 340, "h": 60, "style": "accent", "lines": ["read_committed", "5 records, nothing of O-5101 to O-5110"]},
        {"x": 40, "y": 330, "w": 170, "h": 60, "style": "box", "lines": ["O-5001 to O-5005", "committed"]},
        {"x": 225, "y": 330, "w": 85, "h": 60, "style": "ink", "lines": ["commit", "marker"]},
        {"x": 325, "y": 330, "w": 170, "h": 60, "style": "ghost", "lines": ["O-5101 to O-5107", "aborted copies"]},
        {"x": 510, "y": 330, "w": 85, "h": 60, "style": "ink", "lines": ["abort", "marker"]},
        {"x": 610, "y": 330, "w": 170, "h": 60, "style": "box", "lines": ["O-5101 to O-5110", "committed"]},
        {"x": 795, "y": 330, "w": 85, "h": 60, "style": "ink", "lines": ["commit", "marker"]},
        {"x": 40, "y": 420, "w": 430, "h": 60, "style": "accent", "lines": ["read_committed", "5 + 10 records: the aborted copies are skipped"]},
        {"x": 490, "y": 420, "w": 430, "h": 60, "style": "box", "lines": ["read_uncommitted", "5 + 7 + 10 records: every copy"]},
    ],
    edges=[
        {"x1": 520, "y1": 120, "x2": 580, "y2": 120},
        {"x1": 520, "y1": 200, "x2": 580, "y2": 200, "amber": True},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "An aborted transaction stays in the log and is invisible to read_committed"},
    ],
)
