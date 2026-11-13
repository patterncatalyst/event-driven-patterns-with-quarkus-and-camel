"""What a replay and a projection read cost at 1,000, 10,000 and 100,000 orders, and what a rebuild costs once."""

from generate_diagram import emit

# Milliseconds, median of three runs of the verify.sh measurement. These are the values of the first measurement and
# are replaced by the final runs; the prose table quotes the same constants.
REPLAY_MS = {"1,000 orders": 9, "10,000 orders": 67, "100,000 orders": 510}
PROJECTION_MS = {"1,000 orders": 1.25, "10,000 orders": 0.89, "100,000 orders": 0.50}
REBUILD_MS = {"1,000 orders": 254, "10,000 orders": 770, "100,000 orders": 12964}

X0 = 220
WIDTH = 520


def bar(ms, top):
    return max(6, round(ms * WIDTH / top))


def fmt(ms):
    return f"{ms:.2f} ms" if ms < 10 and ms != int(ms) else (f"{ms / 1000:.1f} s" if ms >= 10000 else f"{ms:,.0f} ms")


nodes = []
notes = []
y = 80
notes.append({"x": 20, "y": y - 20, "bold": True, "size": 14, "color": "#444444", "text": "A read of the answer"})
for label in REPLAY_MS:
    nodes.append({"x": 20, "y": y, "w": 180, "h": 40, "style": "box", "lines": [label]})
    nodes.append({"x": X0, "y": y, "w": bar(REPLAY_MS[label], max(REPLAY_MS.values())), "h": 40, "style": "accent", "lines": [""]})
    notes.append({"x": X0 + bar(REPLAY_MS[label], max(REPLAY_MS.values())) + 10, "y": y + 26, "color": "#444444", "size": 13,
                  "text": f"{fmt(REPLAY_MS[label])} replay"})
    y += 46
    nodes.append({"x": 20, "y": y, "w": 180, "h": 40, "style": "box", "lines": ["from the projection"]})
    nodes.append({"x": X0, "y": y, "w": bar(PROJECTION_MS[label], max(REPLAY_MS.values())), "h": 40, "style": "ink", "lines": [""]})
    notes.append({"x": X0 + bar(PROJECTION_MS[label], max(REPLAY_MS.values())) + 10, "y": y + 26, "color": "#444444", "size": 13,
                  "text": f"{fmt(PROJECTION_MS[label])} projection read"})
    y += 60
y += 20
notes.append({"x": 20, "y": y - 6, "bold": True, "size": 14, "color": "#444444", "text": "A new question: the rebuild, once"})
y += 14
for label in REBUILD_MS:
    nodes.append({"x": 20, "y": y, "w": 180, "h": 40, "style": "box", "lines": [label]})
    nodes.append({"x": X0, "y": y, "w": bar(REBUILD_MS[label], max(REBUILD_MS.values())), "h": 40, "style": "ghost", "lines": [""]})
    notes.append({"x": X0 + bar(REBUILD_MS[label], max(REBUILD_MS.values())) + 10, "y": y + 26, "color": "#444444", "size": 13,
                  "text": f"{fmt(REBUILD_MS[label])} to catch up"})
    y += 52
notes.append({"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
              "text": "Median over runs, inside the service. Bar length is proportional to time within each group."})
notes.append({"x": 480, "y": y + 20, "anchor": "middle", "size": 11, "color": "#555555",
              "text": "8-core laptop CPU, PostgreSQL on the compose stack. A projection read is a scan of the same few dozen rows at every size."})

emit("17-cost-by-size", 960, y + 40, nodes=nodes, notes=notes)
