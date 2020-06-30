package simulation;

import java.util.List;
import java.util.Map;


import config.CustomerBaseConfig;
import config.Rebalance;
import model.User;
import model.Vehicle;

public class Matching {
    public static final String METHOD_OPTIMAL = "method_optimal";
    public static final String METHOD_GREEDY = "method_greedy";
    public static final String METHOD_FCFS = "method_fsfc";
    protected Map<String, Long> runTimes;



    private String method;
    private RideMatchingStrategy strategy;

    protected boolean isAllowedToLowerServiceLevel;
    protected int contractDuration;
    protected Rebalance rebalanceUtil;
    protected boolean isAllowedToHire;
    protected CustomerBaseConfig customerBaseSettings;

    public Matching(String method, boolean isAllowedToLowerServiceLevel, int contractDuration, Rebalance rebalanceSettings, boolean isAllowedToHire) {
        this.method = method;

        switch (this.method) {
            case Matching.METHOD_OPTIMAL:
                this.strategy = new MatchingOptimal();
                break;
            case Matching.METHOD_FCFS:
                this.strategy = new MatchingFCFS();
                break;
            default:
                this.strategy = new MatchingGreedy();
                break;
        }
    }

    public Matching(String method,
                    boolean isAllowedToLowerServiceLevel,
                    CustomerBaseConfig customerBaseConfig,
                    int contractDuration,
                    Rebalance rebalanceUtil,
                    boolean isAllowedToHire) {
        this.method = method;
        this.isAllowedToLowerServiceLevel = isAllowedToLowerServiceLevel;
        this.customerBaseSettings = customerBaseConfig;
        this.contractDuration = contractDuration;
        this.rebalanceUtil = rebalanceUtil;
        this.isAllowedToHire = isAllowedToHire;

        switch (this.method) {
            case Matching.METHOD_OPTIMAL:
                this.strategy = new MatchingOptimal();
                break;
            case Matching.METHOD_FCFS:
                this.strategy = new MatchingFCFS();
                break;
            default:
                this.strategy = new MatchingGreedy();
                break;
        }
    }

    public static String getMethodOptimal() {
        return METHOD_OPTIMAL;
    }

    public ResultAssignment executeStrategy(int currentTime, List<User> setUnassignedRequests, List<Vehicle> listVehicles) {
        return strategy.match(currentTime, setUnassignedRequests, listVehicles, this);
    }

}