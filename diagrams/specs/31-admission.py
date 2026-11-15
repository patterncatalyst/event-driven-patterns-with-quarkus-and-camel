"""Pod Security Admission on a labelled namespace: a pod is checked when it is created, a Deployment is accepted and its ReplicaSet fails."""

from generate_diagram import emit

emit(
    "31-admission",
    1000,
    400,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 150, "fill": "#fafafa", "label": "A pod created directly"},
        {"x": 20, "y": 225, "w": 960, "h": 150, "fill": "#fff8ef", "label": "A pod created by a Deployment"},
    ],
    nodes=[
        {"x": 40, "y": 95, "w": 170, "h": 90, "style": "user", "lines": ["Namespace edp", "enforce: restricted", "version v1.37"]},
        {"x": 260, "y": 95, "w": 170, "h": 90, "style": "ink", "lines": ["API server", "Pod Security", "Admission"]},
        {"x": 560, "y": 95, "w": 180, "h": 40, "style": "box", "lines": ["Four fields set: admitted"]},
        {"x": 560, "y": 150, "w": 180, "h": 40, "style": "accent", "lines": ["No fields: forbidden"]},
        {"x": 790, "y": 95, "w": 170, "h": 90, "style": "ghost", "lines": ["Violations are named", "in the error message"]},
        {"x": 40, "y": 260, "w": 170, "h": 90, "style": "box", "lines": ["Deployment", "no security context", "accepted, with a warning"]},
        {"x": 260, "y": 260, "w": 170, "h": 90, "style": "box", "lines": ["ReplicaSet", "tries to create", "the pod"]},
        {"x": 480, "y": 260, "w": 170, "h": 90, "style": "accent", "lines": ["Pod refused", "event FailedCreate", "names the fields"]},
        {"x": 700, "y": 260, "w": 260, "h": 90, "style": "ghost", "lines": ["The old pod keeps running;", "the rollout never completes", "and no new pod appears"]},
    ],
    edges=[
        {"x1": 210, "y1": 140, "x2": 260, "y2": 140},
        {"x1": 430, "y1": 125, "x2": 560, "y2": 115},
        {"x1": 430, "y1": 155, "x2": 560, "y2": 170, "amber": True},
        {"x1": 740, "y1": 170, "x2": 790, "y2": 150, "dashed": True},
        {"x1": 210, "y1": 305, "x2": 260, "y2": 305},
        {"x1": 430, "y1": 305, "x2": 480, "y2": 305, "amber": True},
        {"x1": 650, "y1": 305, "x2": 700, "y2": 305, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The check runs when a pod is created, not when the namespace is labelled"},
        {"x": 500, "y": 392, "anchor": "middle", "size": 12, "color": "#555555",
         "text": "Labelling a namespace with running pods only prints a warning; the next pod creation is refused"},
    ],
)
