package model;

import dao.Dao;
import model.node.Node;

import java.util.stream.Collectors;

public class VisitRelocation extends Visit {

    protected Node targetNode;
    protected int targetArrival;

    public VisitRelocation(Node target, Vehicle v) {
        this.targetNode = target;
        this.vehicle = v;
        this.delay = 0;

        // Arrival at target node (latest time at vehicle current node + distance to target)
        this.targetArrival = v.getDepartureCurrent() + Dao.getInstance().getDistSec(v.getLastVisitedNode(), target);

        // Arrival at target node
        target.setArrival(this.targetArrival);
    }


    public int getArrivalTimeAtNext() {
        return this.targetArrival;
    }

    public Node getTargetNode() {
        return this.targetNode;
    }

    public String getVarId(){
        return this.getTargetNode().toString();
    }
}
