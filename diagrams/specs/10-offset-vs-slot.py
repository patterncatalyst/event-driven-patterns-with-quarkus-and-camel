"""Two positions: the offset Connect stores and the slot's confirmed_flush_lsn. PostgreSQL streams from the greater."""

from generate_diagram import emit

emit(
    "10-offset-vs-slot",
    960,
    500,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 190, "fill": "#fafafa", "label": "Forward: an offset ahead of the slot skips changes"},
        {"x": 20, "y": 280, "w": 920, "h": 190, "fill": "#fff8ef", "label": "Backward: an offset behind the slot is ignored"},
    ],
    nodes=[
        {"x": 40, "y": 110, "w": 250, "h": 70, "style": "box", "lines": ["slot confirmed_flush_lsn", "the server's position"]},
        {"x": 355, "y": 110, "w": 250, "h": 70, "style": "accent", "lines": ["stored offset lsn", "altered to the current WAL end"]},
        {"x": 670, "y": 110, "w": 250, "h": 70, "style": "ink", "lines": ["streaming starts at the offset", "changes in between are skipped"]},
        {"x": 40, "y": 330, "w": 250, "h": 70, "style": "box", "lines": ["slot confirmed_flush_lsn", "the server's position"]},
        {"x": 355, "y": 330, "w": 250, "h": 70, "style": "accent", "lines": ["stored offset lsn", "altered to an older position"]},
        {"x": 670, "y": 330, "w": 250, "h": 90, "style": "ink", "lines": ["streaming starts at the slot", "nothing older than the slot;", "the last change may repeat once"]},
    ],
    edges=[
        {"x1": 290, "y1": 145, "x2": 355, "y2": 145},
        {"x1": 605, "y1": 145, "x2": 670, "y2": 145, "amber": True},
        {"x1": 290, "y1": 365, "x2": 355, "y2": 365},
        {"x1": 605, "y1": 365, "x2": 670, "y2": 365, "amber": True},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The server streams from whichever position is greater"},
    ],
)
