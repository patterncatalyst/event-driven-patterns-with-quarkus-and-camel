"""Writes land in the WAL; Debezium translates a captured row change into an event; the slot remembers how far the reader has confirmed."""

from generate_diagram import emit

emit(
    "09-wal-publication-slot",
    1000,
    520,
    nodes=[
        {"x": 30, "y": 70, "w": 210, "h": 70, "style": "box", "lines": ["inventory_item", "legacy table, captured"]},
        {"x": 30, "y": 170, "w": 210, "h": 70, "style": "ghost", "lines": ["legacy_audit", "busy table, not captured"]},
        {"x": 30, "y": 270, "w": 210, "h": 70, "style": "box", "lines": ["dbz_heartbeat", "written by the action query"]},
        {"x": 290, "y": 130, "w": 160, "h": 100, "style": "kernel", "lines": ["write-ahead log", "every committed change"]},
        {"x": 290, "y": 260, "w": 160, "h": 80, "style": "accent",
         "lines": ["replication slot, edp_inventory", "the reader's place, confirmed_flush_lsn"]},
        {"x": 290, "y": 400, "w": 660, "h": 70, "style": "ink",
         "lines": ["WAL kept on disk = current position minus confirmed_flush_lsn",
                   "a slot nobody advances keeps every segment after its position"]},
    ],
    icons=[
        {"x": 470, "y": 130, "w": 140, "h": 100, "kind": "filter", "label": "publication: three tables"},
        {"x": 630, "y": 130, "w": 150, "h": 100, "kind": "translator",
         "label": "Debezium: row to event"},
        {"x": 800, "y": 130, "w": 150, "h": 100, "kind": "channel", "label": "captured changes"},
    ],
    edges=[
        {"x1": 240, "y1": 105, "x2": 290, "y2": 160},
        {"x1": 240, "y1": 205, "x2": 290, "y2": 180, "dashed": True},
        {"x1": 240, "y1": 305, "x2": 290, "y2": 210},
        {"x1": 450, "y1": 180, "x2": 470, "y2": 180},
        {"x1": 610, "y1": 180, "x2": 630, "y2": 180},
        {"x1": 780, "y1": 180, "x2": 800, "y2": 180, "amber": True},
        {"x1": 370, "y1": 230, "x2": 370, "y2": 260},
        {"x1": 700, "y1": 230, "x2": 370, "y2": 260, "dashed": True, "label": "confirm", "lx": 40, "ly": -10},
        {"x1": 370, "y1": 340, "x2": 370, "y2": 400, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 24, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The log holds every table's changes; the slot holds the reader's place"},
        {"x": 500, "y": 505, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "The publication filters what is sent. It does not stop the log from growing while the slot stays behind."},
    ],
)
