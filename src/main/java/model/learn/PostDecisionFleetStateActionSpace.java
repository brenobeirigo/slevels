package model.learn;

import model.User;
import model.Vehicle;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PostDecisionFleetStateActionSpace extends FleetStateActionSpace {

    private Map<Vehicle, Set<StateAction>> vehiclePostVisitsMap;
    private FleetStateActionSpace fleetStateActionSpace;
    private Integer elapsed;

    public Map<Vehicle, Set<StateAction>> getVehicleVisitsMap(){
        return this.vehiclePostVisitsMap;
    }

    public Set<Vehicle> getFleet() {
        return fleetStateActionSpace.vehicles;
    }

    public Set<User> getUnassignedUsers() {
        return fleetStateActionSpace.requests;
    }

    public Set<StateAction> getPostStatesFromVehicle(Vehicle v) {
        return this.vehiclePostVisitsMap.get(v);
    }

    public Set<StateAction> getStatesFromVehicle(Vehicle v) {
        return this.fleetStateActionSpace.vehicleStateActionMap.get(v);
    }

    public PostDecisionFleetStateActionSpace(FleetStateActionSpace fleetStateActionSpace, int elapsed) {
        super(
                fleetStateActionSpace.vehicles,
                fleetStateActionSpace.requests,
                fleetStateActionSpace.timeStep,
                fleetStateActionSpace.timeHorizon);
        this.vehicleVisitMap = fleetStateActionSpace.vehicleVisitMap;
        this.fleetStateActionSpace = fleetStateActionSpace;
        this.elapsed = elapsed;
        this.vehicleStateActionMap = getVehiclePostStatesMap();
    }

    private Set<StateAction> getPostDecisionStates(Set<StateAction> stateActions, int timestepInterval) {
        Set<StateAction> postStateActions = new HashSet<>();
        for (StateAction preDecisionState : stateActions) {
//            VehicleState postDecisionState = VehicleState.realize(preDecisionState, timestepInterval);
            StateAction postDecisionState = preDecisionState.stepForward(timestepInterval);
            // Assumes pre-decision states of all the other vehicles
            setNumberOfSurroundingVehiclesAtNextLocation(postDecisionState);
            postStateActions.add(postDecisionState);
        }

        return postStateActions;
    }


    public Map<Vehicle, Set<StateAction>> getVehiclePostStatesMap() {

        ConcurrentHashMap<Vehicle, Set<StateAction>> vehicleVisitPostStateMap = new ConcurrentHashMap<>();

        // TODO Cannot make in parallel because finding post decision states depends on shortest path memorized arrays (not thread safe)
        this.fleetStateActionSpace.vehicleStateActionMap.forEach((vehicle, vehicleCurrentStates) -> {
            Set<StateAction> vehiclePostStates = this.getPostDecisionStates(vehicleCurrentStates, this.elapsed);
            vehicleVisitPostStateMap.put(vehicle, vehiclePostStates);
        });

        return vehicleVisitPostStateMap;
    }
}
