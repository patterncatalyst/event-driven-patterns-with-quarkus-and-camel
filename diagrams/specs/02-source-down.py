"""Source down: the record waits uncommitted until order-service returns."""

from generate_diagram import emit

emit(
    "02-source-down",
    1190,
    430,
    bands=[
        {"x": 20, "y": 50, "w": 1150, "h": 120, "fill": "#fafafa", "label": "order-service"},
        {"x": 20, "y": 185, "w": 1150, "h": 120, "fill": "#fafafa", "label": "Kafka topic edp.orders.placed"},
        {"x": 20, "y": 320, "w": 1150, "h": 70, "fill": "#fafafa", "label": "notification-service"},
    ],
    nodes=[
        {"x": 40, "y": 90, "w": 250, "h": 60, "style": "box", "lines": ["Place order B", "committed, event published"]},
        {"x": 440, "y": 90, "w": 250, "h": 60, "style": "ghost", "lines": ["Stopped", "cannot serve GET /orders/B"]},
        {"x": 900, "y": 90, "w": 250, "h": 60, "style": "box", "lines": ["Started again", "serves GET /orders/B"]},
        {"x": 40, "y": 225, "w": 250, "h": 60, "style": "ink", "lines": ["Record B at offset n", "committed offset stays at n"]},
        {"x": 440, "y": 225, "w": 250, "h": 60, "style": "accent", "lines": ["Re-polled", "breakOnFirstError"]},
        {"x": 900, "y": 225, "w": 250, "h": 60, "style": "ink", "lines": ["Offset moves to n+1", "after the notification is stored"]},
        {"x": 440, "y": 345, "w": 250, "h": 38, "style": "box", "lines": ["Fetch fails, retried, no notification"]},
        {"x": 900, "y": 345, "w": 250, "h": 38, "style": "accent", "lines": ["Fetch succeeds, notification for B"]},
    ],
    edges=[
        {"x1": 165, "y1": 150, "x2": 165, "y2": 225},
        {"x1": 290, "y1": 255, "x2": 440, "y2": 255, "amber": True},
        {"x1": 565, "y1": 285, "x2": 565, "y2": 345},
        {"x1": 690, "y1": 255, "x2": 900, "y2": 255, "amber": True, "label": "order-service is back", "ly": -10},
        {"x1": 1025, "y1": 150, "x2": 1025, "y2": 225},
        {"x1": 1025, "y1": 285, "x2": 1025, "y2": 345},
    ],
    notes=[
        {"x": 595, "y": 28, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "An unreadable source holds the record; it is neither lost nor skipped"},
        {"x": 595, "y": 415, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "Left to right: time. Without breakOnFirstError the offset would move past B and no notification would ever follow."},
    ],
)
