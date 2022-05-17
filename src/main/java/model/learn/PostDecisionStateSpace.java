package model.learn;

import dao.Dao;
import model.User;
import model.Vehicle;
import model.VisitObj;
import model.node.Node;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PostDecisionStateSpace extends StateSpace {

    private Map<Vehicle, Set<VehicleState>> vehiclePostVisitsMap;
    private StateSpace stateSpace;
    private Integer elapsed;

    /**
     *
     * @param vehicles
     * @param unassignedUsers
     * @param vehicleVisitMap
     * @param currentTime
     * @param timeHorizon
     * @param elapsed
     */
    public PostDecisionStateSpace(Set<Vehicle> vehicles, Set<User> unassignedUsers, Map<Vehicle, Set<VisitObj>> vehicleVisitMap, int currentTime, int timeHorizon, int elapsed) {
        super(vehicles, unassignedUsers, currentTime, timeHorizon);
        this.stateSpace = new StateSpace(vehicles, unassignedUsers, vehicleVisitMap, currentTime, timeHorizon);
        this.elapsed = elapsed;
        this.vehiclePostVisitsMap = getVehiclePostStatesMap();
    }

    public Map<Vehicle, Set<VehicleState>> getVehicleVisitsMap(){
        return this.vehiclePostVisitsMap;
    }

    public Set<Vehicle> getFleet() {
        return stateSpace.vehicles;
    }

    public Set<User> getUnassignedUsers() {
        return stateSpace.requests;
    }

//    public Map<String, List<Object>> getStateSummary() {
//
//        Map<String, List<Object>> input = new HashMap<>();
//
//        input.put("current_time_input", new ArrayList<>());
//        input.put("num_requests_input", new ArrayList<>());
//        input.put("delay_input", new ArrayList<>());
//        input.put("path_location_input", new ArrayList<>());
//        input.put("other_agents_input", new ArrayList<>());
//        input.put("occupancy_input", new ArrayList<>());
//
//        double requestShare = getRequestShare();
//
//        List<VehicleDecisionSpace> postStateVectors = this.vehiclePostVisitsMap.entrySet().stream().parallel().map(e -> {
//            Vehicle v = e.getKey();
//            Set<VehicleState> postStates = e.getValue();
//            VehicleDecisionSpace postStateVector = new VehicleDecisionSpace(v, postStates, requestShare);
//            return postStateVector;
//        }).collect(Collectors.toList());
//
//        for (VehicleDecisionSpace p : postStateVectors) {
//            input.get("current_time_input").add(p.currentTimes);
//            input.get("num_requests_input").add(p.numRequests);
//            input.get("delay_input").add(p.arrivalDelays);
//            input.get("path_location_input").add(p.nextNetworkIds);
//            input.get("other_agents_input").add(p.surroundingVehiclesCount);
//            input.get("occupancy_input").add(p.occupancyRates);
//        }
//        return input;
//    }

//    public DecisionSpaceObject getStateObject() {
//
//        double requestShare = getRequestShare();
//
//        List<VehicleDecisionSpace> postStateVectors = this.vehiclePostVisitsMap.entrySet().stream().parallel().map(e -> {
//            Vehicle v = e.getKey();
//            Set<VehicleState> postStates = e.getValue();
//            VehicleDecisionSpace postStateVector = new VehicleDecisionSpace(v, postStates, requestShare);
//            return postStateVector;
//        }).collect(Collectors.toList());
//
//        return new DecisionSpaceObject(postStateVectors);
//    }
//
//    private double getRequestShare() {
//        double requestShare = (double) this.getUnassignedUsers().size() / this.getFleet().size();
//        return requestShare;
//    }

    public Set<VehicleState> getPostStatesFromVehicle(Vehicle v) {
        return this.vehiclePostVisitsMap.get(v);
    }

    public Set<VehicleState> getStatesFromVehicle(Vehicle v) {
        return this.stateSpace.vehicleDecisionsMap.get(v);
    }

    public PostDecisionStateSpace(StateSpace stateSpace, int elapsed) {
        super(stateSpace.vehicles, stateSpace.requests, stateSpace.currentTime, stateSpace.timeHorizon);
        this.vehicleVisitMap = stateSpace.vehicleVisitMap;
        this.stateSpace = stateSpace;
        this.elapsed = elapsed;
        this.vehicleDecisionsMap = getVehiclePostStatesMap();
    }

    private Set<VehicleState> getPostDecisionStates(Set<VehicleState> vehicleStates, int timestepInterval) {
        Set<VehicleState> postVehicleStates = new HashSet<>();
        for (VehicleState preDecisionState : vehicleStates) {
//            VehicleState postDecisionState = VehicleState.realize(preDecisionState, timestepInterval);
            VehicleState postDecisionState = preDecisionState.stepForward(timestepInterval);
            // Assumes pre-decision states of all the other vehicles
            setNumberOfSurroundingVehiclesAtNextLocation(postDecisionState);
            postVehicleStates.add(postDecisionState);
        }

        return postVehicleStates;
    }


    public Map<Vehicle, Set<VehicleState>> getVehiclePostStatesMap() {

        ConcurrentHashMap<Vehicle, Set<VehicleState>> vehicleVisitPostStateMap = new ConcurrentHashMap<>();

        // TODO Cannot make in parallel because finding post decision states depends on shortest path memorized arrays (not thread safe)
        this.stateSpace.vehicleDecisionsMap.forEach((vehicle, vehicleCurrentStates) -> {
            Set<VehicleState> vehiclePostStates = this.getPostDecisionStates(vehicleCurrentStates, this.elapsed);
            vehicleVisitPostStateMap.put(vehicle, vehiclePostStates);
        });

        return vehicleVisitPostStateMap;
    }
}
