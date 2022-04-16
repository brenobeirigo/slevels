package helper;

import java.util.HashMap;
import java.util.Map;

public class Runtime {


    // Execution time headers
    public static final String TIME_UPDATE_FLEET_STATUS = "time_update_fleet_status_s";
    public static final String TIME_REBALANCING_FLEET = "time_vehicle_rebalancing_s";
    public static final String TIME_UPDATE_DEMAND = "time_ride_matching_s";
    public static final String TIME_CREATE_RV = "time_create_rv_graph";
    public static final String TIME_RTV_BUILDING_TOTAL = "time_rtv_building_total";
    public static final String TIME_MATCHING = "time_matching";
    public static final String TIME_RTV_INIT = "time_rtv_init";
    public static final String TIME_RTV_FEASIBLE_TRIPS = " time_rtv_feasible_trips";
    public static final String TIME_RTV_POPULATE_GRAPH = "time_rtv_populate_graph";

    public static final String[] TIME_HEADERS = new String[]{
            TIME_UPDATE_DEMAND,
            TIME_UPDATE_FLEET_STATUS,
            TIME_REBALANCING_FLEET
    };
    private Map<String, Long> runTimes;

    public double getExecutionTimeSecFor(String timerKey) {
        return this.runTimes.get(timerKey) / 1000000000.0;
    }

    public void startTimerFor(String timerKey) {
        this.runTimes.put(timerKey, System.nanoTime());
    }

    public void endTimerFor(String timerKey) {
        this.runTimes.put(timerKey, System.nanoTime() - this.runTimes.get(timerKey));
    }

    public Runtime(){
        runTimes = new HashMap<>();
    }
}