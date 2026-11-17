"""A rootless buildah build of the unchanged Containerfile is copied to an ImageStream with its digest preserved, and the Deployment references that digest."""

from generate_diagram import emit

emit(
    "ah-build-and-copy",
    1000,
    420,
    bands=[
        {"x": 20, "y": 60, "w": 470, "h": 320, "fill": "#fff8ef", "label": "On the host, rootless"},
        {"x": 520, "y": 60, "w": 460, "h": 320, "fill": "#fafafa", "label": "In OpenShift Local, project edp"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 430, "h": 60, "style": "box", "lines": ["Containerfile of order-service", "UBI 10 build and runtime, unchanged"]},
        {"x": 40, "y": 190, "w": 430, "h": 60, "style": "accent", "lines": ["buildah build", "from examples/, no daemon"]},
        {"x": 40, "y": 280, "w": 430, "h": 70, "style": "ink", "lines": ["Image in containers-storage", "manifest digest D"]},
        {"x": 540, "y": 100, "w": 420, "h": 70, "style": "kernel", "lines": ["Registry default route", "TLS verified with the ingress CA", "builder ServiceAccount token"]},
        {"x": 540, "y": 200, "w": 420, "h": 60, "style": "accent", "lines": ["ImageStream order-service:08", "manifest digest D"]},
        {"x": 540, "y": 290, "w": 420, "h": 60, "style": "box", "lines": ["Deployment order-service", "image: internal registry, @D"]},
    ],
    edges=[
        {"x1": 255, "y1": 160, "x2": 255, "y2": 190},
        {"x1": 255, "y1": 250, "x2": 255, "y2": 280},
        {"x1": 470, "y1": 315, "x2": 540, "y2": 140, "amber": True, "label": "skopeo copy", "ly": -10},
        {"x1": 750, "y1": 170, "x2": 750, "y2": 200, "amber": True},
        {"x1": 750, "y1": 260, "x2": 750, "y2": 290},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The digest of the local build is the digest in the cluster"},
        {"x": 500, "y": 405, "anchor": "middle", "size": 12, "color": "#444444",
         "text": "--preserve-digests keeps D; a plain copy recompresses the layers and the digest changes"},
    ],
)
