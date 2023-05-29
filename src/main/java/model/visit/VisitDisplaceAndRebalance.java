package model.visit;

import model.Vehicle;
import model.node.Node;
import simulation.Environment;

public class VisitDisplaceAndRebalance extends VisitRelocation {
    public VisitDisplaceAndRebalance(Node middleNode, Vehicle vehicle, Environment environment) {
        super(middleNode, vehicle, environment);
    }
}
