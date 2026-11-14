"""The same order saga as choreography, as a Camel saga with an LRA coordinator and as a Quarkus Flow workflow: where the sequence, the state and the compensation trigger live."""

from generate_diagram import emit

emit(
    "23-three-ways",
    960,
    480,
    bands=[
        {"x": 20, "y": 60, "w": 290, "h": 380, "fill": "#fafafa", "label": "Choreography"},
        {"x": 335, "y": 60, "w": 290, "h": 380, "fill": "#fff8ef", "label": "Camel Saga with LRA"},
        {"x": 650, "y": 60, "w": 290, "h": 380, "fill": "#fafafa", "label": "Quarkus Flow"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 250, "h": 70, "style": "box", "lines": ["Sequence", "spread over three services"]},
        {"x": 40, "y": 200, "w": 250, "h": 70, "style": "box", "lines": ["State", "order, inventory and payment tables"]},
        {"x": 40, "y": 300, "w": 250, "h": 70, "style": "box", "lines": ["Compensation trigger", "consumers of failure events"]},
        {"x": 40, "y": 390, "w": 250, "h": 36, "style": "ghost", "lines": ["time limit: a sweeper"]},
        {"x": 355, "y": 100, "w": 250, "h": 70, "style": "accent", "lines": ["Sequence", "routes in order-service"]},
        {"x": 355, "y": 200, "w": 250, "h": 70, "style": "accent", "lines": ["State", "coordinator and Kafka headers"]},
        {"x": 355, "y": 300, "w": 250, "h": 70, "style": "accent", "lines": ["Compensation trigger", "coordinator callbacks to joined participants"]},
        {"x": 355, "y": 390, "w": 250, "h": 36, "style": "ghost", "lines": ["time limit: the saga timeout"]},
        {"x": 670, "y": 100, "w": 250, "h": 70, "style": "kernel", "lines": ["Sequence", "the task list of one workflow"]},
        {"x": 670, "y": 200, "w": 250, "h": 70, "style": "kernel", "lines": ["State", "workflow instance in PostgreSQL"]},
        {"x": 670, "y": 300, "w": 250, "h": 70, "style": "kernel", "lines": ["Compensation trigger", "tasks after a failed or timed-out listen"]},
        {"x": 670, "y": 390, "w": 250, "h": 36, "style": "ghost", "lines": ["time limit: the listen timeout"]},
    ],
    edges=[],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Three places to look when an order is stuck"},
    ],
)
