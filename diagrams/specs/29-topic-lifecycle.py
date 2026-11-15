"""What each action on a KafkaTopic or on a Kafka topic does to the other side, as measured."""

from generate_diagram import emit

emit(
    "29-topic-lifecycle",
    1000,
    430,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 150, "fill": "#fff8ef", "label": "Acted on in Kubernetes"},
        {"x": 20, "y": 230, "w": 960, "h": 150, "fill": "#f6f8fa", "label": "Acted on in Kafka"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 200, "h": 90, "style": "box", "lines": ["Apply a KafkaTopic", "the topic exists", "Ready within seconds"]},
        {"x": 280, "y": 100, "w": 200, "h": 90, "style": "accent", "lines": ["Delete the KafkaTopic", "the finalizer waits,", "then the topic is gone"]},
        {"x": 520, "y": 100, "w": 200, "h": 90, "style": "box", "lines": ["Annotate managed: false", "status Unmanaged"]},
        {"x": 760, "y": 100, "w": 200, "h": 90, "style": "accent", "lines": ["Then delete it", "the resource goes,", "the topic stays"]},
        {"x": 40, "y": 270, "w": 200, "h": 90, "style": "box", "lines": ["Alter a declared key", "reverted on a", "later pass"]},
        {"x": 280, "y": 270, "w": 200, "h": 90, "style": "box", "lines": ["Delete the topic", "recreated from", "the KafkaTopic"]},
        {"x": 520, "y": 270, "w": 200, "h": 90, "style": "ghost", "lines": ["Raise partitions", "stays raised;", "Ready=False NotSupported"]},
        {"x": 760, "y": 270, "w": 200, "h": 90, "style": "ghost", "lines": ["Create a new topic", "no KafkaTopic", "ever appears"]},
    ],
    edges=[
        {"x1": 240, "y1": 145, "x2": 280, "y2": 145},
        {"x1": 480, "y1": 145, "x2": 520, "y2": 145},
        {"x1": 720, "y1": 145, "x2": 760, "y2": 145, "amber": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Deleting the resource deletes the topic unless the resource was unmanaged first"},
        {"x": 500, "y": 410, "anchor": "middle", "size": 13, "color": "#555555",
         "text": "Measured with a 15 second reconciliation interval"},
    ],
)
