package simulation;

import config.CustomerBaseConfig;
import config.Rebalance;
import model.User;
import model.Vehicle;

import java.util.List;
import java.util.Map;

public class Matching {
    public static final String METHOD_OPTIMAL = "method_optimal";
    public static final String METHOD_GREEDY = "method_greedy";
    public static final String METHOD_FCFS = "method_fsfc";
    public static final String METHOD_OPTIMAL_ENFORCE_SL = "method_optimal_enforce_sl";
    protected Map<String, Long> runTimes;
    protected boolean isAllowedToLowerServiceLevel;
    protected int contractDuration;
    protected Rebalance rebalanceUtil;
    protected boolean isAllowedToHire;
    protected CustomerBaseConfig customerBaseSettings;
    protected RideMatchingStrategy strategy;

    public Matching(boolean isAllowedToLowerServiceLevel, int contractDuration, Rebalance rebalanceSettings, boolean isAllowedToHire) {

    }

    public Matching(boolean isAllowedToLowerServiceLevel,
                    CustomerBaseConfig customerBaseConfig,
                    int contractDuration,
                    Rebalance rebalanceUtil,
                    boolean isAllowedToHire) {
        this.isAllowedToLowerServiceLevel = isAllowedToLowerServiceLevel;
        this.customerBaseSettings = customerBaseConfig;
        this.contractDuration = contractDuration;
        this.rebalanceUtil = rebalanceUtil;
        this.isAllowedToHire = isAllowedToHire;
    }


    public ResultAssignment executeStrategy(int currentTime, List<User> setUnassignedRequests, List<Vehicle> listVehicles) {
        return strategy.match(currentTime, setUnassignedRequests, listVehicles, this);
    }

    public void setStrategy(RideMatchingStrategy matchingMethod) {
        this.strategy = matchingMethod;
    }
}