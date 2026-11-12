"""A crash between the commit and the acknowledgement redelivers the record; the inbox row turns the redelivery into a skip."""

from generate_diagram import emit

emit(
    "11-crash-before-ack",
    960,
    420,
    bands=[
        {"x": 20, "y": 60, "w": 450, "h": 310, "fill": "#fff8ef", "label": "Before the crash"},
        {"x": 490, "y": 60, "w": 450, "h": 310, "fill": "#fafafa", "label": "After the restart"},
    ],
    nodes=[
        {"x": 50, "y": 100, "w": 390, "h": 56, "style": "box", "lines": ["1. read the record for C at offset 3", "committed offset of the group: 3"]},
        {"x": 50, "y": 180, "w": 390, "h": 56, "style": "box", "lines": ["2. commit: inbox row C and stock - 4", "both rows are durable"]},
        {"x": 50, "y": 260, "w": 390, "h": 56, "style": "accent", "lines": ["3. the JVM halts with exit 137", "the record is not acknowledged"]},
        {"x": 520, "y": 100, "w": 390, "h": 56, "style": "box", "lines": ["4. the group member rejoins", "after the dead member's session expires"]},
        {"x": 520, "y": 180, "w": 390, "h": 56, "style": "box", "lines": ["5. the record for C is delivered again", "the offset was never committed past it"]},
        {"x": 520, "y": 260, "w": 390, "h": 56, "style": "accent", "lines": ["6. the insert returns 0 rows: skipped", "stock stays at 91, lag reaches 0"]},
    ],
    edges=[
        {"x1": 245, "y1": 156, "x2": 245, "y2": 180},
        {"x1": 245, "y1": 236, "x2": 245, "y2": 260},
        {"x1": 440, "y1": 288, "x2": 520, "y2": 128, "amber": True},
        {"x1": 715, "y1": 156, "x2": 715, "y2": 180},
        {"x1": 715, "y1": 236, "x2": 715, "y2": 260},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "At-least-once delivery turns a crash into a duplicate"},
    ],
)
