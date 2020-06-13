package config;

import model.Vehicle;
import model.node.Node;

import java.util.List;
import java.util.Set;

public interface RebalanceStrategy {
    void rebalance(Set<Vehicle> idleVehicles, List<Node> targets, Rebalance config);

}

