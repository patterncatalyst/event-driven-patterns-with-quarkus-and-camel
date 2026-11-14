"""The 09:00 slot: records arrive out of order, stream time moves with the newest event time, the window closes at slot end plus grace, one final list leaves, and a record past the grace is dropped and counted."""

from generate_diagram import emit

emit(
    "21-window-close-and-grace",
    960,
    440,
    bands=[
        {"x": 20, "y": 60, "w": 620, "h": 150, "fill": "#fff8ef", "label": "Slot 09:00 to 09:15 is open"},
        {"x": 660, "y": 60, "w": 280, "h": 150, "fill": "#fafafa", "label": "Window closed"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 180, "h": 80, "style": "box", "lines": ["09:01 to 09:07", "7 FASTLINE records", "stream time 09:07"]},
        {"x": 240, "y": 100, "w": 180, "h": 80, "style": "box", "lines": ["09:16 FASTLINE", "opens slot 09:15", "stream time 09:16"]},
        {"x": 440, "y": 100, "w": 180, "h": 80, "style": "accent", "lines": ["09:10 FASTLINE", "out of order", "inside grace: joins 09:00"]},
        {"x": 680, "y": 100, "w": 240, "h": 80, "style": "ink", "lines": ["09:21 tick", "stream time 09:21", "slot end + grace 09:20 passed"]},
        {"x": 680, "y": 240, "w": 240, "h": 70, "style": "accent", "lines": ["One list leaves", "FASTLINE 09:00, count 8", "closedBy window"]},
        {"x": 440, "y": 240, "w": 200, "h": 70, "style": "kernel", "lines": ["Suppression buffer", "holds the list", "until the window closes"]},
        {"x": 680, "y": 340, "w": 240, "h": 70, "style": "box", "lines": ["09:05 FASTLINE, late", "past grace: dropped", "dropped-records counter + 1"]},
        {"x": 40, "y": 240, "w": 380, "h": 70, "style": "ghost", "lines": ["Stream time is the newest event time seen", "the wall clock never closes a window"]},
    ],
    edges=[
        {"x1": 220, "y1": 140, "x2": 240, "y2": 140},
        {"x1": 420, "y1": 140, "x2": 440, "y2": 140},
        {"x1": 620, "y1": 140, "x2": 680, "y2": 140},
        {"x1": 800, "y1": 180, "x2": 800, "y2": 240},
        {"x1": 640, "y1": 275, "x2": 680, "y2": 275, "amber": True},
        {"x1": 800, "y1": 310, "x2": 800, "y2": 340, "dashed": True},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The list leaves once, when stream time passes the end of the slot plus the grace"},
    ],
)
