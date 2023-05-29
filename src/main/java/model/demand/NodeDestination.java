package model.demand;

import model.node.NodePUDO;

import java.time.LocalDateTime;

public class NodeDestination  extends NodePUDO {

    public NodeDestination(int requestId, int networkId, LocalDateTime earliest, LocalDateTime latest, int load) {
        super(requestId, networkId, earliest, latest, -load);
    }
}