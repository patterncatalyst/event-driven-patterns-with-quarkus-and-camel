"""Three resources applied with kubectl, and what the Strimzi operator creates from them."""

from generate_diagram import emit

emit(
    "28-operator-reconcile",
    1000,
    430,
    bands=[
        {"x": 20, "y": 60, "w": 280, "h": 330, "fill": "#fafafa", "label": "You apply"},
        {"x": 400, "y": 60, "w": 580, "h": 330, "fill": "#fff8ef", "label": "The operator keeps"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 240, "h": 60, "style": "box", "lines": ["Kafka edp", "version, listeners, config"]},
        {"x": 40, "y": 190, "w": 240, "h": 60, "style": "box", "lines": ["KafkaNodePool controller", "roles: controller"]},
        {"x": 40, "y": 280, "w": 240, "h": 60, "style": "box", "lines": ["KafkaNodePool broker", "roles: broker"]},
        {"x": 420, "y": 100, "w": 250, "h": 60, "style": "accent", "lines": ["Strimzi cluster operator", "edp-kafka namespace"]},
        {"x": 720, "y": 100, "w": 240, "h": 60, "style": "ink", "lines": ["Service edp-kafka-bootstrap", "the address clients use"]},
        {"x": 420, "y": 200, "w": 250, "h": 60, "style": "box", "lines": ["StrimziPodSet edp-controller", "pod edp-controller-<id>"]},
        {"x": 420, "y": 290, "w": 250, "h": 60, "style": "box", "lines": ["StrimziPodSet edp-broker", "pod edp-broker-<id>"]},
        {"x": 720, "y": 200, "w": 240, "h": 60, "style": "ghost", "lines": ["PersistentVolumeClaim", "one per node, follows the node"]},
        {"x": 720, "y": 290, "w": 240, "h": 60, "style": "ghost", "lines": ["status", "nodeIds, kafkaVersion"]},
    ],
    edges=[
        {"x1": 280, "y1": 130, "x2": 420, "y2": 130, "amber": True},
        {"x1": 280, "y1": 220, "x2": 420, "y2": 150, "amber": True},
        {"x1": 280, "y1": 310, "x2": 420, "y2": 155, "amber": True},
        {"x1": 670, "y1": 130, "x2": 720, "y2": 130},
        {"x1": 545, "y1": 160, "x2": 545, "y2": 200},
        {"x1": 545, "y1": 260, "x2": 545, "y2": 290, "dashed": True},
        {"x1": 670, "y1": 230, "x2": 720, "y2": 230},
        {"x1": 670, "y1": 320, "x2": 720, "y2": 320, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Declare the cluster; the operator creates and keeps the pods, volumes and Services"},
        {"x": 500, "y": 415, "anchor": "middle", "size": 13, "color": "#555555",
         "text": "A deleted pod is created again with the same name, node id and volume claim"},
    ],
)
