package model.visit;

import model.Vehicle;
import model.node.Node;

public class VisitStop extends Visit {


    public VisitStop(Vehicle v) {
        this.vehicle = v;
        this.delay = 0;
        this.delayBonus = 0;
        this.idle = 0;
    }

    public Node getTargetNode(){
        if (this.vehicle.getVisit()==null){
            return vehicle.getLastVisitedNode();
        }
        assert this.vehicle.getVisit() instanceof VisitRelocation;
        return this.vehicle.getTargetNode();
    }

    public String toString() {
        return String.format("# Vehicle %s stopped at %s.", this.vehicle, this.vehicle.getLastVisitedNode());
    }
}
