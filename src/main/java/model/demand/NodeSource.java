package model.demand;

import model.node.NodePUDO;

import java.time.LocalDateTime;

public class NodeSource extends NodePUDO {

    private LocalDateTime departureDateTime;
    private LocalDateTime earliestDepartureDateTime;

    public NodeSource(int requestId, int networkId, LocalDateTime earliest, LocalDateTime latest, int load) {
        super(requestId, networkId, earliest, latest, load);
        this.earliestDepartureDateTime = this.earliestDateTime;
        this.departureDateTime = null;
    }

    public LocalDateTime getDepartureDateTime() {
        return departureDateTime;
    }

    public LocalDateTime getEarliestDepartureDateTime() {
        return earliestDepartureDateTime;
    }

}
