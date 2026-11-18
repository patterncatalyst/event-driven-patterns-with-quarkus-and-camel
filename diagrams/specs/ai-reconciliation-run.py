"""One reconciliation run: the runner creates three Jobs in order, the middle one fans out over partitions, and every step records its state in PostgreSQL."""

from generate_diagram import emit

emit(
    "ai-reconciliation-run",
    1000,
    420,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 190, "fill": "#fff8ef", "label": "One run, three Jobs, each admitted by the queue"},
        {"x": 20, "y": 270, "w": 960, "h": 130, "fill": "#fafafa", "label": "PostgreSQL"},
    ],
    nodes=[
        {"x": 40, "y": 110, "w": 160, "h": 90, "style": "accent", "lines": ["batch-runner", "Process Manager", "POST /runs"]},
        {"x": 235, "y": 110, "w": 150, "h": 90, "style": "box", "lines": ["plan", "1 pod", "fixes the offsets"]},
        {"x": 420, "y": 110, "w": 190, "h": 90, "style": "box", "lines": ["reconcile", "Indexed Job, 3 pods", "index i reads partition i"]},
        {"x": 645, "y": 110, "w": 150, "h": 90, "style": "box", "lines": ["aggregate", "1 pod", "sums the partitions"]},
        {"x": 830, "y": 110, "w": 130, "h": 90, "style": "ink", "lines": ["output topic", "reconciliations", "key run:carrier"]},
        {"x": 40, "y": 310, "w": 160, "h": 70, "style": "sub", "lines": ["run", "step"]},
        {"x": 235, "y": 310, "w": 150, "h": 70, "style": "sub", "lines": ["run_partition", "offsets per partition"]},
        {"x": 420, "y": 310, "w": 190, "h": 70, "style": "sub", "lines": ["partition_result", "written with DONE"]},
        {"x": 645, "y": 310, "w": 150, "h": 70, "style": "sub", "lines": ["carrier_total", "per carrier"]},
    ],
    edges=[
        {"x1": 200, "y1": 155, "x2": 235, "y2": 155, "amber": True},
        {"x1": 385, "y1": 155, "x2": 420, "y2": 155, "amber": True},
        {"x1": 610, "y1": 155, "x2": 645, "y2": 155, "amber": True},
        {"x1": 795, "y1": 155, "x2": 830, "y2": 155},
        {"x1": 120, "y1": 200, "x2": 120, "y2": 310, "dashed": True},
        {"x1": 310, "y1": 200, "x2": 310, "y2": 310, "dashed": True},
        {"x1": 515, "y1": 200, "x2": 515, "y2": 310, "dashed": True},
        {"x1": 720, "y1": 200, "x2": 720, "y2": 310, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The runner creates the next Job only when the previous step is done"},
    ],
)
