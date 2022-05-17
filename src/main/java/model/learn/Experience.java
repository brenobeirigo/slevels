package model.learn;

import model.Vehicle;
import simulation.matching.ResultAssignment;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Experience {
    public Map<Vehicle, VehicleState> state;
    public Map<Vehicle, Set<VehicleState>> decisions;
    public Map<Vehicle, VehicleState> postState;
    public Map<Vehicle, Integer> rewardRequest;
    public Map<Vehicle, Integer> rewardDelay;
    int time;

    public Experience(int time, StateSpace preDecisionStateSpace, ResultAssignment result) {
        this.time = time;
        this.state = preDecisionStateSpace.vehicleCurrentStateMap;
        this.decisions = preDecisionStateSpace.vehicleDecisionsMap;
        postState = new HashMap<>();
        rewardRequest = new HashMap<>();
        rewardDelay = new HashMap<>();
        result.getVisitsOK().forEach(visitObj -> {
            System.out.println(visitObj);
            postState.put(visitObj.getVehicle(), (VehicleState) visitObj);
            rewardDelay.put(visitObj.getVehicle(), visitObj.getDelay());
            rewardRequest.put(visitObj.getVehicle(), visitObj.getRequestsTotalLoad());
        });
    }

    public Experience(int time, Map<Vehicle, VehicleState> state, Map<Vehicle, Set<VehicleState>> decisions, ResultAssignment result) {
        this.time = time;
        this.state = state;
        this.decisions = decisions;
        postState = new HashMap<>();
        rewardRequest = new HashMap<>();
        rewardDelay = new HashMap<>();
        result.getVisitsOK().forEach(visitObj -> {
            System.out.println(visitObj);
            postState.put(visitObj.getVehicle(), (VehicleState) visitObj);
            rewardDelay.put(visitObj.getVehicle(), visitObj.getDelay());
            rewardRequest.put(visitObj.getVehicle(), visitObj.getRequestsTotalLoad());
        });
    }
}
