"""Two Kafka clusters in one namespace, MirrorMaker 2 between them with its source and checkpoint connectors."""

from generate_diagram import emit

emit(
    "34-two-clusters",
    1000,
    420,
    bands=[
        {"x": 20, "y": 60, "w": 270, "h": 300, "fill": "#f6f8fa", "label": "Cluster edp (source)"},
        {"x": 365, "y": 60, "w": 270, "h": 300, "fill": "#fff8ef", "label": "MirrorMaker 2 workers"},
        {"x": 710, "y": 60, "w": 270, "h": 300, "fill": "#f6f8fa", "label": "Cluster edp-dr (target)"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 230, "h": 60, "style": "ink", "lines": ["edp.orders.placed", "3 partitions"]},
        {"x": 40, "y": 190, "w": 230, "h": 60, "style": "box", "lines": ["group notification-service", "committed offsets"]},
        {"x": 40, "y": 280, "w": 230, "h": 60, "style": "ghost", "lines": ["mm2-offset-syncs", ".edp-dr.internal"]},
        {"x": 385, "y": 100, "w": 230, "h": 70, "style": "accent", "lines": ["MirrorSourceConnector", "copies records, keeps names", "IdentityReplicationPolicy"]},
        {"x": 385, "y": 230, "w": 230, "h": 70, "style": "accent", "lines": ["MirrorCheckpointConnector", "translates group offsets", "sync.group.offsets.enabled"]},
        {"x": 730, "y": 100, "w": 230, "h": 60, "style": "ink", "lines": ["edp.orders.placed", "same name, same offsets"]},
        {"x": 730, "y": 190, "w": 230, "h": 60, "style": "box", "lines": ["group notification-service", "written by the connector"]},
        {"x": 730, "y": 280, "w": 230, "h": 60, "style": "ghost", "lines": ["edp.checkpoints.internal"]},
    ],
    edges=[
        {"x1": 270, "y1": 130, "x2": 385, "y2": 130, "amber": True, "label": "reads", "ly": -10},
        {"x1": 615, "y1": 130, "x2": 730, "y2": 130, "amber": True, "label": "writes", "ly": -10},
        {"x1": 270, "y1": 310, "x2": 385, "y2": 270, "dashed": True, "label": "offset syncs", "ly": 22},
        {"x1": 270, "y1": 220, "x2": 385, "y2": 250, "dashed": True},
        {"x1": 615, "y1": 270, "x2": 730, "y2": 220, "amber": True, "label": "sets", "ly": 18},
        {"x1": 615, "y1": 285, "x2": 730, "y2": 305, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "One direction: edp to edp-dr, with the same topic names on both sides"},
        {"x": 500, "y": 395, "anchor": "middle", "size": 12, "color": "#555555",
         "text": "The offset-syncs topic stays on the source by default; the checkpoints topic is on the target"},
    ],
)
