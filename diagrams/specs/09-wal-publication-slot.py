"""Writes land in the WAL; the publication selects tables; the slot remembers how far the reader has confirmed."""

from generate_diagram import emit

emit(
    "09-wal-publication-slot",
    960,
    470,
    nodes=[
        {"x": 30, "y": 70, "w": 210, "h": 70, "style": "box", "lines": ["inventory_item", "legacy table, captured"]},
        {"x": 30, "y": 170, "w": 210, "h": 70, "style": "ghost", "lines": ["legacy_audit", "busy table, not captured"]},
        {"x": 30, "y": 270, "w": 210, "h": 70, "style": "box", "lines": ["dbz_heartbeat", "written by the action query"]},
        {"x": 300, "y": 140, "w": 190, "h": 100, "style": "kernel", "lines": ["write-ahead log", "every committed change", "from every table"]},
        {"x": 550, "y": 70, "w": 190, "h": 100, "style": "user", "lines": ["publication", "edp_inventory", "three tables"]},
        {"x": 550, "y": 220, "w": 190, "h": 100, "style": "accent", "lines": ["replication slot", "edp_inventory", "confirmed_flush_lsn"]},
        {"x": 780, "y": 140, "w": 160, "h": 100, "style": "box", "lines": ["Debezium", "connector", "decodes pgoutput"]},
        {"x": 300, "y": 360, "w": 640, "h": 70, "style": "ink", "lines": ["WAL kept on disk = current position minus confirmed_flush_lsn", "a slot nobody advances keeps every segment after its position"]},
    ],
    edges=[
        {"x1": 240, "y1": 105, "x2": 300, "y2": 170},
        {"x1": 240, "y1": 205, "x2": 300, "y2": 200},
        {"x1": 240, "y1": 305, "x2": 300, "y2": 230},
        {"x1": 490, "y1": 170, "x2": 550, "y2": 120, "amber": True},
        {"x1": 740, "y1": 120, "x2": 780, "y2": 175, "amber": True},
        {"x1": 780, "y1": 210, "x2": 740, "y2": 260, "dashed": True, "label": "confirm", "lx": -10, "ly": 14},
        {"x1": 645, "y1": 320, "x2": 645, "y2": 360, "dashed": True},
    ],
    notes=[
        {"x": 480, "y": 24, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The log holds every table's changes; the slot holds the reader's place"},
        {"x": 480, "y": 455, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "The publication filters what is sent. It does not stop the log from growing while the slot stays behind."},
    ],
)
