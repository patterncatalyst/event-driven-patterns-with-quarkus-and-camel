"""A stalled relay: orders keep arriving, the backlog grows and the alert fires while nothing reaches the topic, then the relay resumes."""

from generate_diagram import emit

emit(
    "26-stall-timeline",
    1000,
    380,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 140, "fill": "#fff8ef", "label": "Connector paused"},
    ],
    nodes=[
        {"x": 40, "y": 95, "w": 170, "h": 90, "style": "box", "lines": ["Orders placed", "table: 0 rows", "topic: no new record"]},
        {"x": 235, "y": 95, "w": 170, "h": 90, "style": "box", "lines": ["Backlog rises", "slot holds the", "unread changes"]},
        {"x": 430, "y": 95, "w": 170, "h": 90, "style": "accent", "lines": ["Alert: Alerting", "backlog over threshold", "for the pending period"]},
        {"x": 625, "y": 95, "w": 170, "h": 90, "style": "ghost", "lines": ["Consumer is idle", "no age sample,", "group lag stays 0"]},
        {"x": 625, "y": 235, "w": 170, "h": 90, "style": "box", "lines": ["Connector resumes", "the slot is read", "records are written"]},
        {"x": 820, "y": 235, "w": 160, "h": 90, "style": "accent", "lines": ["Events arrive old", "event age and", "record age both high"]},
        {"x": 430, "y": 235, "w": 170, "h": 90, "style": "box", "lines": ["Backlog falls", "alert: Normal"]},
    ],
    edges=[
        {"x1": 210, "y1": 140, "x2": 235, "y2": 140},
        {"x1": 405, "y1": 140, "x2": 430, "y2": 140, "amber": True},
        {"x1": 600, "y1": 140, "x2": 625, "y2": 140, "dashed": True},
        {"x1": 710, "y1": 185, "x2": 710, "y2": 235},
        {"x1": 795, "y1": 280, "x2": 820, "y2": 280, "amber": True},
        {"x1": 625, "y1": 280, "x2": 600, "y2": 280},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Only the backlog moves while the flow is stuck; age is measured on arrival"},
        {"x": 500, "y": 355, "anchor": "middle", "size": 13, "color": "#555555",
         "text": "An alert on age fires after the resume, when the damage is done"},
    ],
)
