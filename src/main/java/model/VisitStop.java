package model;

import model.node.Node;

import java.util.stream.Collectors;

public class VisitStop extends Visit {


    public VisitStop(Vehicle v) {
        this.vehicle = v;
        this.delay = 0;
        this.idle = 0;
    }

    public String toString() {
        return String.format("# Vehicle %s stopped at %s.", this.vehicle, this.vehicle.getLastVisitedNode());
    }
}
