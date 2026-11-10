package com.patterncatalyst.shipping;

import java.util.List;

/** One voyage of a vessel through an ordered list of port calls. */
public record Sailing(String id, String vesselImo, List<PortCall> portCalls) {

    public Sailing {
        Checks.text(id, "id");
        Checks.text(vesselImo, "vesselImo");
        portCalls = Checks.list(portCalls, "portCalls");
        if (portCalls.isEmpty()) {
            throw new IllegalArgumentException("portCalls must not be empty");
        }
    }
}
