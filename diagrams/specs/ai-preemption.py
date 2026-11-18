"""A high-priority run evicts a low-priority reconcile Job; the finished indexes are kept and only the missing index runs again."""

from generate_diagram import emit

emit(
    "ai-preemption",
    1000,
    440,
    bands=[
        {"x": 20, "y": 60, "w": 300, "h": 320, "fill": "#fafafa", "label": "Low-priority run, batch-low"},
        {"x": 350, "y": 60, "w": 300, "h": 320, "fill": "#fff8ef", "label": "High-priority run, batch-high, submitted"},
        {"x": 680, "y": 60, "w": 300, "h": 320, "fill": "#fafafa", "label": "High run done, low run admitted again"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 260, "h": 50, "style": "ink", "lines": ["index 0 done"]},
        {"x": 40, "y": 170, "w": 260, "h": 50, "style": "ink", "lines": ["index 1 done"]},
        {"x": 40, "y": 240, "w": 260, "h": 60, "style": "accent", "lines": ["index 2 running", "slow partition"]},
        {"x": 370, "y": 100, "w": 260, "h": 60, "style": "kernel", "lines": ["Low run Workload evicted", "reason Preempted"]},
        {"x": 370, "y": 180, "w": 260, "h": 60, "style": "box", "lines": ["Low run Job suspended", "completedIndexes 0,1 kept"]},
        {"x": 370, "y": 260, "w": 260, "h": 60, "style": "user", "lines": ["High run reconcile admitted", "3 pods run"]},
        {"x": 700, "y": 100, "w": 260, "h": 60, "style": "ink", "lines": ["High run reconcile complete", "quota released"]},
        {"x": 700, "y": 180, "w": 260, "h": 60, "style": "box", "lines": ["Low run admitted again", "the step was QUEUED, not FAILED"]},
        {"x": 700, "y": 260, "w": 260, "h": 60, "style": "accent", "lines": ["one new pod, index 2", "indexes 0 and 1 not run again"]},
        {"x": 40, "y": 395, "w": 920, "h": 35, "style": "sub", "lines": ["The evicted pod's failure count stays 0: an eviction is not a failed attempt"]},
    ],
    edges=[
        {"x1": 320, "y1": 200, "x2": 350, "y2": 200, "amber": True},
        {"x1": 650, "y1": 200, "x2": 680, "y2": 200, "amber": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Preemption suspends the Job; it does not fail it"},
    ],
)
