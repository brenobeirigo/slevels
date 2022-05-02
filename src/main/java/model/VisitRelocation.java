package model;

import dao.Dao;
import model.node.Node;
import model.node.NodeTargetRebalancing;

public class VisitRelocation extends Visit {

    protected Node targetNode;
    protected int targetArrival;


    public VisitRelocation(Node targetNode, Vehicle vehicle) {
        // Create a target node for rebalancing
        NodeTargetRebalancing target = new NodeTargetRebalancing(vehicle, targetNode);
        this.departure = vehicle.getEarliestDeparture();
        this.targetNode = target;
        this.vehicle = vehicle;
        this.idle = 0;
        this.delay = 0;

        // Arrival at target node (latest time at vehicle current node + distance to target)
        this.targetArrival = vehicle.getEarliestDeparture() + Dao.getInstance().getDistSec(vehicle.getLastVisitedNode(), target);
    }

    public int getArrivalTimeAtNext() {
        return this.targetArrival;
    }

    public Node getTargetNode() {
        return this.targetNode;
    }
}
