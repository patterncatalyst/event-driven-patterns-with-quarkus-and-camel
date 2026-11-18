"""The Kueue objects behind one queue: cluster-scoped flavor, quota and priorities, and the namespaced queue, Job and Workload."""

from generate_diagram import emit

emit(
    "ai-kueue-objects",
    1000,
    440,
    bands=[
        {"x": 20, "y": 60, "w": 450, "h": 340, "fill": "#fafafa", "label": "Cluster scoped"},
        {"x": 520, "y": 60, "w": 460, "h": 340, "fill": "#fff8ef", "label": "Namespace edp"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 200, "h": 60, "style": "box", "lines": ["ResourceFlavor", "default-flavor"]},
        {"x": 260, "y": 100, "w": 190, "h": 60, "style": "box", "lines": ["WorkloadPriorityClass", "batch-low 100, batch-high 1000"]},
        {"x": 40, "y": 250, "w": 410, "h": 120, "style": "accent", "lines": ["ClusterQueue reconciliation", "cpu 1750m, memory 3Gi on default-flavor", "queueing strategy BestEffortFIFO", "preemption: LowerPriority in the queue"]},
        {"x": 540, "y": 100, "w": 200, "h": 60, "style": "box", "lines": ["LocalQueue", "reconciliation"]},
        {"x": 760, "y": 100, "w": 200, "h": 110, "style": "user", "lines": ["Job, suspend: true", "label queue-name", "label priority-class", "created by the runner"]},
        {"x": 540, "y": 250, "w": 200, "h": 110, "style": "ink", "lines": ["Workload", "created by Kueue", "one pod set", "quota for all its pods"]},
    ],
    edges=[
        {"x1": 140, "y1": 160, "x2": 140, "y2": 250, "label": "quota of", "lx": 40},
        {"x1": 540, "y1": 150, "x2": 450, "y2": 270, "label": "feeds", "lx": 0, "ly": -5},
        {"x1": 760, "y1": 130, "x2": 740, "y2": 130, "amber": True},
        {"x1": 860, "y1": 210, "x2": 740, "y2": 290, "label": "Kueue creates", "lx": 50, "ly": -5},
        {"x1": 540, "y1": 330, "x2": 450, "y2": 330, "amber": True, "label": "admitted", "ly": -12},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A Job runs only after its Workload is admitted"},
        {"x": 500, "y": 425, "anchor": "middle", "size": 12, "color": "#666666",
         "text": "The priority-class label names a WorkloadPriorityClass; the queue-name label names the LocalQueue"},
    ],
)
