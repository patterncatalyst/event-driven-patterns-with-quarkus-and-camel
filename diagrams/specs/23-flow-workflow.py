"""The workflow orchestrator as a list of tasks, with the explicit compensation branch after a failed or timed-out payment and the instance state in PostgreSQL."""

from generate_diagram import emit

emit(
    "23-flow-workflow",
    960,
    470,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 280, "fill": "#fafafa", "label": "OrderSagaWorkflow: tasks in order"},
        {"x": 20, "y": 360, "w": 920, "h": 90, "fill": "#fff8ef", "label": "State"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 130, "h": 60, "style": "box", "lines": ["reserve", "ReserveStock"]},
        {"x": 190, "y": 100, "w": 130, "h": 60, "style": "kernel", "lines": ["awaitReserved", "listen"]},
        {"x": 340, "y": 100, "w": 130, "h": 60, "style": "ghost", "lines": ["afterReserved", "switch"]},
        {"x": 490, "y": 100, "w": 130, "h": 60, "style": "box", "lines": ["takePayment", "TakePayment"]},
        {"x": 640, "y": 100, "w": 130, "h": 60, "style": "accent", "lines": ["awaitPayment", "listen, time limit"]},
        {"x": 790, "y": 100, "w": 130, "h": 60, "style": "ghost", "lines": ["afterPayment", "switch"]},
        {"x": 470, "y": 190, "w": 130, "h": 50, "style": "box", "lines": ["refund", "on timeout only"]},
        {"x": 650, "y": 200, "w": 120, "h": 50, "style": "box", "lines": ["release", "ReleaseStock"]},
        {"x": 790, "y": 200, "w": 130, "h": 60, "style": "box", "lines": ["markPaid", "then end"]},
        {"x": 340, "y": 300, "w": 130, "h": 50, "style": "accent", "lines": ["cancel", "order CANCELLED"]},
        {"x": 40, "y": 390, "w": 420, "h": 46, "style": "ink", "lines": ["workflow_instance_entity, task_info_entity", "written as each task completes or pauses"]},
        {"x": 500, "y": 390, "w": 420, "h": 46, "style": "ghost", "lines": ["a waiting instance does not resume after a recreate", "measured in the example"]},
    ],
    edges=[
        {"x1": 170, "y1": 130, "x2": 190, "y2": 130},
        {"x1": 320, "y1": 130, "x2": 340, "y2": 130},
        {"x1": 470, "y1": 130, "x2": 490, "y2": 130, "label": "reserved"},
        {"x1": 620, "y1": 130, "x2": 640, "y2": 130},
        {"x1": 770, "y1": 130, "x2": 790, "y2": 130},
        {"x1": 855, "y1": 160, "x2": 855, "y2": 200, "label": "paid"},
        {"x1": 650, "y1": 160, "x2": 560, "y2": 190, "amber": True, "dashed": True, "label": "timeout"},
        {"x1": 600, "y1": 215, "x2": 650, "y2": 225, "amber": True},
        {"x1": 800, "y1": 160, "x2": 770, "y2": 205, "amber": True, "label": "declined", "lx": 28, "ly": 0},
        {"x1": 650, "y1": 245, "x2": 470, "y2": 322, "amber": True},
        {"x1": 405, "y1": 160, "x2": 405, "y2": 300, "amber": True, "dashed": True, "label": "insufficient"},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The sequence is the task list; the compensation is tasks you write"},
    ],
)
