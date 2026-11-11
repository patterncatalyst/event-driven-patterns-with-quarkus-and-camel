"""The producer keeps one open batch per partition and sends batches that are full or have waited long enough."""

from generate_diagram import emit

emit(
    "05-batch-fill",
    960,
    470,
    bands=[
        {"x": 300, "y": 60, "w": 330, "h": 330, "fill": "#fafafa", "label": "Producer accumulator"},
    ],
    nodes=[
        {"x": 30, "y": 130, "w": 220, "h": 70, "style": "box", "lines": ["records, keyed by order id", "from many callers"]},
        {"x": 320, "y": 100, "w": 290, "h": 66, "style": "accent", "lines": ["open batch, partition 0", "full at batch.size or after linger.ms"]},
        {"x": 320, "y": 195, "w": 290, "h": 66, "style": "box", "lines": ["open batch, partition 1", "still filling"]},
        {"x": 320, "y": 290, "w": 290, "h": 66, "style": "accent", "lines": ["open batch, partition 2", "full at batch.size or after linger.ms"]},
        {"x": 690, "y": 130, "w": 250, "h": 70, "style": "ink", "lines": ["one request to the broker", "ready batches for partitions 0 and 2"]},
        {"x": 690, "y": 290, "w": 250, "h": 66, "style": "box", "lines": ["broker appends each batch", "and acknowledges the request"]},
    ],
    edges=[
        {"x1": 250, "y1": 165, "x2": 320, "y2": 133, "amber": True},
        {"x1": 250, "y1": 165, "x2": 320, "y2": 228},
        {"x1": 250, "y1": 165, "x2": 320, "y2": 323, "amber": True},
        {"x1": 610, "y1": 133, "x2": 690, "y2": 155, "amber": True},
        {"x1": 610, "y1": 323, "x2": 690, "y2": 180, "amber": True},
        {"x1": 815, "y1": 200, "x2": 815, "y2": 290},
    ],
    notes=[
        {"x": 480, "y": 24, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Records wait in a per-partition batch; a request carries every batch that is ready"},
        {"x": 480, "y": 430, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "Compression applies to the whole batch, so a fuller batch compresses better."},
    ],
)
