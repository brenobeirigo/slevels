package model;

import model.node.Node;

public class NodeSP extends Node {
    public NodeSP(int networkID) {
        super(networkID);

        this.load = 0;
        this.latest = Integer.MAX_VALUE;
        this.tripId = -1;
        this.departure = null;

        this.delay = 0;
        this.maxDelay = 0;

    }

    @Override
    public String getType() {
        return "sp";
    }

    @Override
    public String toString() {
        return String.format("%10s", "SP" + String.valueOf(Math.abs(this.networkId)));
    }
}
