"""Step states: a step waits for quota suspended, runs, and ends DONE or FAILED; an eviction and a replay both return it to QUEUED."""

from generate_diagram import emit

emit(
    "ai-step-states",
    1000,
    340,
    nodes=[
        {"x": 40, "y": 70, "w": 170, "h": 70, "style": "box", "lines": ["PENDING", "no Job yet"]},
        {"x": 270, "y": 70, "w": 160, "h": 70, "style": "box", "lines": ["QUEUED", "Job exists, suspended"]},
        {"x": 500, "y": 70, "w": 160, "h": 100, "style": "accent", "lines": ["RUNNING", "admitted, pods run"]},
        {"x": 770, "y": 70, "w": 190, "h": 60, "style": "ink", "lines": ["DONE", "Job Complete"]},
        {"x": 770, "y": 220, "w": 190, "h": 60, "style": "kernel", "lines": ["FAILED", "Job Failed"]},
    ],
    edges=[
        {"x1": 210, "y1": 105, "x2": 270, "y2": 105, "label": "create Job", "ly": -12},
        {"x1": 430, "y1": 105, "x2": 500, "y2": 105, "amber": True, "label": "admitted", "ly": -12},
        {"x1": 660, "y1": 100, "x2": 770, "y2": 100, "label": "complete", "ly": -12},
        {"x1": 660, "y1": 150, "x2": 770, "y2": 240, "label": "failed", "lx": 10, "ly": 0},
        {"x1": 520, "y1": 170, "x2": 410, "y2": 140, "dashed": True, "label": "evicted", "lx": -25, "ly": 20},
        {"x1": 770, "y1": 250, "x2": 350, "y2": 140, "dashed": True, "label": "replay", "lx": 0, "ly": 40},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "An eviction is not a failure; a replay starts a new attempt"},
    ],
)
