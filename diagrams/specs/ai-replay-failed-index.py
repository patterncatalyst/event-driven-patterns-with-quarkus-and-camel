"""A poison partition fails its index; the replay Job skips the finished partitions and does only the failed one."""

from generate_diagram import emit

emit(
    "ai-replay-failed-index",
    1000,
    460,
    bands=[
        {"x": 20, "y": 60, "w": 450, "h": 280, "fill": "#fafafa", "label": "Attempt 1: Job Failed"},
        {"x": 500, "y": 60, "w": 480, "h": 280, "fill": "#fff8ef", "label": "Replay: a new Job, attempt 2"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 410, "h": 60, "style": "ink", "lines": ["index 0", "DONE, counts written"]},
        {"x": 40, "y": 180, "w": 410, "h": 60, "style": "kernel", "lines": ["index 1", "exit 42, FailIndex, one pod"]},
        {"x": 40, "y": 260, "w": 410, "h": 60, "style": "ink", "lines": ["index 2", "DONE, counts written"]},
        {"x": 520, "y": 100, "w": 440, "h": 60, "style": "sub", "lines": ["index 0", "row is DONE: exit 0, no Kafka read"]},
        {"x": 520, "y": 180, "w": 440, "h": 60, "style": "accent", "lines": ["index 1", "reads partition 1, row becomes DONE"]},
        {"x": 520, "y": 260, "w": 440, "h": 60, "style": "sub", "lines": ["index 2", "row is DONE: exit 0, no Kafka read"]},
        {"x": 40, "y": 380, "w": 920, "h": 60, "style": "box", "lines": ["aggregate runs once, after all three partitions are DONE", "the failed attempt's Job had completedIndexes 0,2 and failedIndexes 1"]},
    ],
    edges=[
        {"x1": 470, "y1": 195, "x2": 500, "y2": 195, "amber": True},
        {"x1": 740, "y1": 340, "x2": 740, "y2": 380},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Only the failed partition is read again"},
    ],
)
