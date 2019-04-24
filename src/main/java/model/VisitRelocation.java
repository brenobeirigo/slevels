package model;

import dao.Dao;
import model.node.Node;

public class VisitRelocation extends Visit {

    protected Node targetNode;
    protected int targetArrival;

    public VisitRelocation(Node target, Vehicle v) {
        this.targetNode = target;
        this.vehicle = v;

        // Arrival at target node (latest time at vehicle current node + distance to target)
        this.targetArrival = v.getDepartureCurrent() + Dao.getInstance().getDistSec(v.getCurrentNode(), target);

        // Arrival at target node
        target.setArrival(this.targetArrival);
    }


    public int getTargetArrival() {
        return this.targetArrival;
    }

    public Node getTargetNode() {
        return this.targetNode;
    }
}