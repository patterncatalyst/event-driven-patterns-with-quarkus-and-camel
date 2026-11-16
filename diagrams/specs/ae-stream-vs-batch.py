"""The same six arrivals through the stream form on a global sequence and through the batch form, with the batch boundary."""

from generate_diagram import emit

emit(
    "ae-stream-vs-batch",
    980,
    420,
    bands=[
        {"x": 20, "y": 60, "w": 940, "h": 150, "fill": "#fff8ef", "label": "Stream form: one global sequence, 1 to 12"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 200, "h": 80, "style": "box", "lines": ["Arrival", "6, 9, 3, 5, 1, 2 ...", "three topics, nine partitions"]},
        {"x": 300, "y": 100, "w": 200, "h": 80, "style": "accent", "lines": ["resequence(seq).stream()", "holds until the predecessor", "is released, or the timeout"]},
        {"x": 560, "y": 100, "w": 200, "h": 80, "style": "ink", "lines": ["Applied 1, 2, 3, 4 ...", "exact global order"]},
        {"x": 40, "y": 260, "w": 200, "h": 100, "style": "box", "lines": ["Arrival, batch 1", "O-1 #2, O-2 #1, O-2 #2,", "O-3 #1, O-3 #2, O-4 #1"]},
        {"x": 300, "y": 260, "w": 200, "h": 100, "style": "accent", "lines": ["resequence(key).batch()", "sorts each batch of six", "by order, then sequence"]},
        {"x": 560, "y": 260, "w": 200, "h": 100, "style": "kernel", "lines": ["Batch 2 holds O-1 #1", "applied after O-1 #2", "the inversion crosses batches"]},
    ],
    edges=[
        {"x1": 240, "y1": 140, "x2": 300, "y2": 140, "amber": True},
        {"x1": 500, "y1": 140, "x2": 560, "y2": 140, "amber": True},
        {"x1": 240, "y1": 310, "x2": 300, "y2": 310, "amber": True},
        {"x1": 500, "y1": 310, "x2": 560, "y2": 310, "dashed": True},
    ],
    notes=[
        {"x": 490, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The stream form orders everything it sees; the batch form orders only inside a batch"},
    ],
)
