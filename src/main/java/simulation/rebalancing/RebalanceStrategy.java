package simulation.rebalancing;

import model.Vehicle;
import model.Visit;
import model.node.Node;

import java.util.List;
import java.util.Set;

public interface RebalanceStrategy {
    void rebalance(Set<Vehicle> idleVehicles, List<Node> targets, Rebalance config);
    void interruptRebalancing(Vehicle vehicle, int timeWindow, boolean episode, boolean createEpisode);
}

