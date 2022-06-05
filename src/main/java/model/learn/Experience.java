package model.learn;

import dao.Logging;
import model.Vehicle;
import simulation.matching.ResultAssignment;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Experience {
    public Map<Vehicle, StateAction> state;
    public Map<Vehicle, Set<StateAction>> decisions;
    public Map<Vehicle, StateAction> postDecisionStateAction;
    public Map<Vehicle, Integer> rewardRequest;
    public Map<Vehicle, Integer> rewardDelay;
    int time;

    public Experience(int time, FleetStateActionSpace preDecisionFleetStateActionSpace, ResultAssignment result) {
        this.time = time;
        this.state = preDecisionFleetStateActionSpace.vehicleCurrentStateMap;
        this.decisions = preDecisionFleetStateActionSpace.vehicleStateActionMap;
        postDecisionStateAction = new HashMap<>();
        rewardRequest = new HashMap<>();
        rewardDelay = new HashMap<>();
        result.getVisitsOK().forEach(visitObj -> {
            Logging.logger.info(visitObj.toString());
            postDecisionStateAction.put(visitObj.getVehicle(), (StateAction) visitObj);
            rewardDelay.put(visitObj.getVehicle(), visitObj.getDelay());
            rewardRequest.put(visitObj.getVehicle(), visitObj.getRequestsTotalLoad());
        });
    }

    public Experience(int time, Map<Vehicle, StateAction> state, Map<Vehicle, Set<StateAction>> decisions, ResultAssignment result) {
        this.time = time;
        this.state = state;
        this.decisions = decisions;
        postDecisionStateAction = new HashMap<>();
        rewardRequest = new HashMap<>();
        rewardDelay = new HashMap<>();
        result.getVisitsOK().forEach(visitObj -> {
            Logging.logger.info(visitObj.toString());
            postDecisionStateAction.put(visitObj.getVehicle(), (StateAction) visitObj);
            rewardDelay.put(visitObj.getVehicle(), visitObj.getDelay());
            rewardRequest.put(visitObj.getVehicle(), visitObj.getRequestsTotalLoad());
        });
    }
}
