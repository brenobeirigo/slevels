package config;

import dao.Dao;
import model.Vehicle;
import model.node.Node;

import java.time.Instant;
import java.util.*;

public class Rebalance {

    public static final String METHOD_HEURISTIC = "rebalance_heuristic";
    public static final String METHOD_OPTIMAL = "rebalance_optimal_alonso_mora";

    public boolean allowManyToOneTarget;
    public boolean reinsertTargets;
    public boolean clearTargetListEachRound;
    public boolean useUrgentKey;
    public String method;
    public boolean showInfo;
    public boolean createEpisode;
    public RebalanceStrategy strategy;
    private long totalExecutionTimeNanoTime;


    public Rebalance(boolean allowManyToOneTarget,
                     boolean reinsertTargets,
                     boolean clearTargetListEachRound,
                     boolean useUrgentKey,
                     String method) {
        this.allowManyToOneTarget = allowManyToOneTarget;
        this.reinsertTargets = reinsertTargets;
        this.clearTargetListEachRound = clearTargetListEachRound;
        this.useUrgentKey = useUrgentKey;
        this.method = method;
        this.totalExecutionTimeNanoTime = 0;

        switch (this.method){
            case Rebalance.METHOD_OPTIMAL: this.strategy = new RebalanceOptimal();break;
            default: this.strategy = new RebalanceHeuristic();break;
        }
    }

    public Rebalance(boolean allowManyToOneTarget,
                     boolean reinsertTargets,
                     boolean clearTargetListEachRound,
                     boolean useUrgentKey,
                     String method,
                     boolean show,
                     boolean createEpisode) {
        this.allowManyToOneTarget = allowManyToOneTarget;
        this.reinsertTargets = reinsertTargets;
        this.clearTargetListEachRound = clearTargetListEachRound;
        this.useUrgentKey = useUrgentKey;
        this.method = method;
        this.showInfo = show;
        this.createEpisode = createEpisode;
        switch (this.method){
            case Rebalance.METHOD_OPTIMAL: this.strategy = new RebalanceOptimal();break;
            default: this.strategy = new RebalanceHeuristic();break;
        }
    }

    public void executeStrategy(Set<Vehicle> idleVehicles, List<Node> targets){
        long before = System.nanoTime();
        strategy.rebalance(idleVehicles, targets, this);
        totalExecutionTimeNanoTime+= (System.nanoTime()-before);
    }


    @Override
    public String toString() {

        String str = "";
        str += clearTargetListEachRound ? "_CT" : "";
        str += allowManyToOneTarget ? "_MO" : "";
        str += reinsertTargets ? "_RT" : "";
        str += useUrgentKey ? "_UR" : "";

        return str;
    }

    public long getExecutionTimeMs() {
        return this.totalExecutionTimeNanoTime/100000000;
    }
}
