"""Event time against lateness: a record 7 s behind the newest is inside the bound and counted, a record for a closed window is dropped by all three engines."""

from generate_diagram import emit

emit(
    "ac-window-and-late-record",
    980,
    470,
    bands=[
        {"x": 20, "y": 60, "w": 940, "h": 150, "fill": "#fff8ef", "label": "Lateness is 10 s: a window closes when the newest event time passes its end plus 10 s"},
        {"x": 20, "y": 240, "w": 940, "h": 200, "fill": "#fafafa", "label": "Later, after window 4 opened"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 200, "h": 90, "style": "box", "lines": ["Window 1: 0 to 60 s", "FASTLINE 5, 20, 40 s", "AIRCO 30, 50 s"]},
        {"x": 270, "y": 100, "w": 200, "h": 90, "style": "box", "lines": ["62 s AIRCO", "opens window 2", "newest event time 62 s"]},
        {"x": 500, "y": 100, "w": 200, "h": 90, "style": "accent", "lines": ["55 s FASTLINE", "7 s behind the newest", "inside 10 s: window 1 = 4"]},
        {"x": 730, "y": 100, "w": 210, "h": 90, "style": "ink", "lines": ["195 s FASTLINE", "opens window 4", "windows 1 to 3 are closed"]},
        {"x": 40, "y": 280, "w": 280, "h": 100, "style": "box", "lines": ["58 s FASTLINE arrives late", "window 1 ended at 60 s", "and closed at 70 s"]},
        {"x": 370, "y": 280, "w": 270, "h": 100, "style": "kernel", "lines": ["Dropped by all three", "Kafka Streams logs the skip", "Flink and RisingWave: below the watermark"]},
        {"x": 690, "y": 280, "w": 250, "h": 100, "style": "accent", "lines": ["Window 1 stays at 4", "nothing is revised or sent", "in any of the three"]},
        {"x": 370, "y": 395, "w": 570, "h": 36, "style": "ghost", "lines": ["A smaller bound in one engine (grace 0) counts window 1 as 3 there and 4 elsewhere"]},
    ],
    edges=[
        {"x1": 240, "y1": 145, "x2": 270, "y2": 145},
        {"x1": 470, "y1": 145, "x2": 500, "y2": 145},
        {"x1": 700, "y1": 145, "x2": 730, "y2": 145},
        {"x1": 320, "y1": 330, "x2": 370, "y2": 330, "dashed": True},
        {"x1": 640, "y1": 330, "x2": 690, "y2": 330, "amber": True},
    ],
    notes=[
        {"x": 490, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The same bound in all three engines gives the same closed windows"},
    ],
)
