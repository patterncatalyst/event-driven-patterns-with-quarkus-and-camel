"""A KafkaRebalance moves through a proposal and waits for approval before anything moves."""

from generate_diagram import emit

emit(
    "33-rebalance-states",
    1040,
    330,
    bands=[
        {"x": 20, "y": 60, "w": 1000, "h": 150, "fill": "#fff8ef", "label": "KafkaRebalance status"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 170, "h": 80, "style": "box", "lines": ["PendingProposal", "Cruise Control", "computes a plan"]},
        {"x": 260, "y": 100, "w": 170, "h": 80, "style": "accent", "lines": ["ProposalReady", "optimizationResult", "nothing has moved"]},
        {"x": 480, "y": 100, "w": 170, "h": 80, "style": "box", "lines": ["Rebalancing", "replicas are", "being copied"]},
        {"x": 700, "y": 100, "w": 170, "h": 80, "style": "ink", "lines": ["Ready", "the new layout", "is in place"]},
        {"x": 260, "y": 245, "w": 250, "h": 55, "style": "ghost", "lines": ["rebalance=refresh", "a new plan from current load"]},
        {"x": 560, "y": 245, "w": 300, "h": 55, "style": "ghost", "lines": ["rebalance-auto-approval: true", "or the operator's autoRebalance"]},
    ],
    edges=[
        {"x1": 210, "y1": 140, "x2": 260, "y2": 140},
        {"x1": 430, "y1": 140, "x2": 480, "y2": 140, "amber": True, "label": "approve", "ly": -10},
        {"x1": 650, "y1": 140, "x2": 700, "y2": 140},
        {"x1": 345, "y1": 245, "x2": 345, "y2": 180, "dashed": True},
        {"x1": 710, "y1": 245, "x2": 570, "y2": 180, "dashed": True},
    ],
    notes=[
        {"x": 520, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A proposal is only a plan: nothing moves until it is approved"},
        {"x": 520, "y": 320, "anchor": "middle", "size": 13, "color": "#555555",
         "text": "A rebalance that cannot get a plan reports NotReady and is refreshed"},
    ],
)
