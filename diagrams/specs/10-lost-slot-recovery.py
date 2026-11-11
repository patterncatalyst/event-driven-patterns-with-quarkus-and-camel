"""A lost slot leaves the connector RUNNING with no task; a dropped slot with offsets kept fails the task. Both recover the same way."""

from generate_diagram import emit

emit(
    "10-lost-slot-recovery",
    960,
    520,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 110, "fill": "#fafafa", "label": "How the two failures look"},
        {"x": 20, "y": 200, "w": 920, "h": 290, "fill": "#fff8ef", "label": "Recovery"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 420, "h": 60, "style": "box", "lines": ["slot lost (wal_status = lost)", "connector RUNNING, tasks empty, retries about 30 minutes"]},
        {"x": 500, "y": 100, "w": 420, "h": 60, "style": "box", "lines": ["slot dropped, offsets kept", "task FAILED: position no longer available"]},
        {"x": 40, "y": 250, "w": 190, "h": 60, "style": "accent", "lines": ["1. PUT /stop", "wait for active = f"]},
        {"x": 260, "y": 250, "w": 190, "h": 60, "style": "box", "lines": ["2. drop the slot", "pg_drop_replication_slot"]},
        {"x": 480, "y": 250, "w": 190, "h": 60, "style": "box", "lines": ["3. DELETE offsets", "reset the stored position"]},
        {"x": 700, "y": 250, "w": 220, "h": 60, "style": "box", "lines": ["4. PUT /resume", "new slot, snapshot again"]},
        {"x": 260, "y": 360, "w": 410, "h": 60, "style": "ink", "lines": ["restart with onlyFailed fixes neither:", "the stored position is still unusable"]},
        {"x": 700, "y": 360, "w": 220, "h": 60, "style": "ghost", "lines": ["current state re-delivered", "changes in the gap are lost"]},
    ],
    edges=[
        {"x1": 230, "y1": 280, "x2": 260, "y2": 280, "amber": True},
        {"x1": 450, "y1": 280, "x2": 480, "y2": 280, "amber": True},
        {"x1": 670, "y1": 280, "x2": 700, "y2": 280, "amber": True},
        {"x1": 810, "y1": 310, "x2": 810, "y2": 360, "dashed": True},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A slot the server cannot use is replaced, not repaired"},
    ],
)
