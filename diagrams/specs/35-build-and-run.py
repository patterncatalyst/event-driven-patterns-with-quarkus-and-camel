"""A KafkaConnect resource becomes a build pod, an image in the registry inside the cluster and a Connect pod."""

from generate_diagram import emit

emit(
    "35-build-and-run",
    1000,
    380,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 190, "fill": "#fff8ef", "label": "In the namespace edp-kafka"},
    ],
    nodes=[
        {"x": 40, "y": 110, "w": 180, "h": 110, "style": "accent", "lines": ["KafkaConnect", "build: plugin archive,", "sha512, output image"]},
        {"x": 270, "y": 110, "w": 170, "h": 110, "style": "box", "lines": ["Build pod", "downloads the plugin,", "checks the sha512"]},
        {"x": 490, "y": 110, "w": 170, "h": 110, "style": "ink", "lines": ["Registry", "fixed ClusterIP,", "plain HTTP"]},
        {"x": 710, "y": 110, "w": 150, "h": 110, "style": "box", "lines": ["Connect pod", "pulls by digest"]},
        {"x": 890, "y": 110, "w": 90, "h": 110, "style": "ghost", "lines": ["Connector", "tasks"]},
        {"x": 270, "y": 290, "w": 390, "h": 60, "style": "ghost", "lines": ["A checksum that does not match:", "the build fails, no image, no Connect pod"]},
    ],
    edges=[
        {"x1": 220, "y1": 165, "x2": 270, "y2": 165, "amber": True},
        {"x1": 440, "y1": 165, "x2": 490, "y2": 165},
        {"x1": 660, "y1": 165, "x2": 710, "y2": 165},
        {"x1": 860, "y1": 165, "x2": 890, "y2": 165},
        {"x1": 355, "y1": 220, "x2": 355, "y2": 290, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The image is built from the resource and never leaves the cluster"},
        {"x": 465, "y": 150, "anchor": "middle", "size": 12, "color": "#444444", "text": "push"},
        {"x": 685, "y": 150, "anchor": "middle", "size": 12, "color": "#444444", "text": "pull"},
    ],
)
