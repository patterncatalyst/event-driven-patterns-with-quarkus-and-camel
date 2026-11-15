"""One Deployment, two writers: managedFields records which manager owns which field."""

from generate_diagram import emit

emit(
    "30-field-managers",
    900,
    360,
    nodes=[
        {"x": 20, "y": 70, "w": 190, "h": 90, "style": "box", "lines": ["edp-apps", "kubectl apply --server-side", "the app manifests"]},
        {"x": 20, "y": 220, "w": 190, "h": 90, "style": "box", "lines": ["edp-platform", "kubectl apply --server-side", "a memory patch"]},
        {"x": 350, "y": 60, "w": 200, "h": 250, "style": "accent", "lines": ["Deployment order-service", "", "image, env, probes", "owned by edp-apps", "", "limits.memory", "one owner at a time"]},
        {"x": 690, "y": 110, "w": 190, "h": 150, "style": "ink", "lines": ["managedFields", "manager + operation", "fieldsV1 paths"]},
    ],
    edges=[
        {"x1": 210, "y1": 115, "x2": 350, "y2": 115, "amber": True},
        {"x1": 210, "y1": 265, "x2": 350, "y2": 265, "amber": True, "dashed": True},
        {"x1": 550, "y1": 185, "x2": 690, "y2": 185},
    ],
    notes=[
        {"x": 450, "y": 28, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The API server records an owner for every field a manager applies"},
    ],
)
