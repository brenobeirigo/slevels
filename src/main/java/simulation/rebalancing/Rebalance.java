package simulation.rebalancing;

import model.Vehicle;
import model.node.Node;

import java.util.List;
import java.util.Set;

public class Rebalance {

    public static final String METHOD_HEURISTIC = "rebalance_heuristic";
    public static final String METHOD_OPTIMAL = "rebalance_optimal_alonso_mora";

    public boolean showInfo;
    public boolean createEpisode;
    public RebalanceStrategy strategy;
    private long totalExecutionTimeNanoTime;


    public Rebalance(boolean show,
                     boolean createEpisode) {
        this.showInfo = show;
        this.createEpisode = createEpisode;
        this.totalExecutionTimeNanoTime = 0;
    }

    public Rebalance(RebalanceStrategy strategy) {
        this.showInfo = false;
        this.createEpisode = false;
        this.totalExecutionTimeNanoTime = 0;
        this.strategy = strategy;
    }

    public void interruptRebalancing(Vehicle vehicle, int timeWindow) {
        // null = No strategy
        if (this.strategy != null)
            this.strategy.interruptRebalancing(vehicle, timeWindow, this.createEpisode, this.showInfo);
    }

    public void executeStrategy(Set<Vehicle> idleVehicles, List<Node> targets) {
        long before = System.nanoTime();
        if (strategy != null) {
            strategy.rebalance(idleVehicles, targets, this);
        }
        totalExecutionTimeNanoTime += (System.nanoTime() - before);
    }

    public long getExecutionTimeMs() {
        return this.totalExecutionTimeNanoTime / 100000000;
    }

    public void setStrategy(RebalanceStrategy strategy) {
        this.strategy = strategy;
    }

    public boolean isRebalanceEnabled() {
        return this.strategy != null;
    }
}
