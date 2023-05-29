package model.visit;

import model.Vehicle;
import model.node.Node;
import simulation.Environment;

public class VisitDisplaceAndStop extends VisitRelocation {
    public VisitDisplaceAndStop(Node middleNode, Vehicle vehicle, Environment env) {
        super(middleNode, vehicle, env);
    }
}
