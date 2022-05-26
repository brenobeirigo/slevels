package model.learn;

import dao.Dao;
import model.User;
import model.Vehicle;
import model.VisitObj;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


public class FleetStateActionSpace {

    protected Dao environment;
    protected Map<Vehicle, Set<VisitObj>> vehicleVisitMap;
    protected Map<Vehicle, StateAction> vehicleCurrentStateMap;
    protected Map<Vehicle, Set<StateAction>> vehicleStateActionMap;
    protected Map<User, Set<StateAction>> userVisitMap;
    public Set<Vehicle> vehicles;
    public Set<User> requests;
    public int timeStep;
    protected int timeHorizon;
    protected int elapsed;

    public Map<Vehicle, Set<StateAction>> getVehicleStateActionMap() {
        return vehicleStateActionMap;
    }

    public void setVehicleStateActionMap(Map<Vehicle, Set<StateAction>> vehicleStateActionMap) {
        this.vehicleStateActionMap = vehicleStateActionMap;
    }

    public FleetStateActionSpaceObject getStateActionObject(){
        List<VehicleStateActionSpace> vehicleStateActionSpaces = getVehicleCurrentDecision();
        return new FleetStateActionSpaceObject(vehicleStateActionSpaces, false);
    }

    public FleetStateActionSpaceObject getDecisionSpaceObject() {


        List<VehicleStateActionSpace> vehicleDecisionSetList = getVehicleDecisionSpaces();

        // Create summary object of decision space:
        // - current_time_input
        // - num_requests_input
        // - delay_input
        // - path_location_input
        // - other_agents_input
        // - occupancy_input
        // - request_ids
        return new FleetStateActionSpaceObject(vehicleDecisionSetList, false);
    }

    private List<VehicleStateActionSpace> getVehicleDecisionSpaces() {
        double requestShare = getRequestShare();
        return this.vehicles.stream().parallel().map(vehicle -> {

            Set<StateAction> decisions = getVehicleStateActionMap().get(vehicle);

            // Select for each vehicle:
            // - current time (share of total horizon)
            // - n. of requests (share)
            // - arrival delays
            // - occupancy at each node (share of total capacity)
            // - next nodes (network ids)
            // - surrounding vehicles count (share)

            return new VehicleStateActionSpace(vehicle, decisions, requestShare);
        }).collect(Collectors.toList());
    }

    private List<VehicleStateActionSpace> getVehicleCurrentDecision() {
        double requestShare = getRequestShare();
        return this.vehicles.stream().parallel().map(vehicle -> new VehicleStateActionSpace(vehicle, Collections.singleton(this.vehicleCurrentStateMap.get(vehicle)), requestShare)).collect(Collectors.toList());
    }

    private double getRequestShare() {
        return (double) this.requests.size() / this.vehicles.size();
    }

    private void createVehicleDecisions() {
        ConcurrentHashMap<Vehicle, Set<StateAction>> vehicleDecisions = new ConcurrentHashMap<>();
        this.vehicleVisitMap.entrySet().stream().parallel().forEach(entryVehicleVisits -> {
            Vehicle vehicle = entryVehicleVisits.getKey();
            Set<VisitObj> visits = entryVehicleVisits.getValue();
            // Assumes pre-decision states of all the other vehicles
            vehicleDecisions.put(vehicle, getDecisionSetFromVisits(visits));
        });


        this.vehicleStateActionMap = vehicleDecisions;
    }


    /**
     * Create Decision for each Visit.
     * Store decision in visit.
     * @param visits
     * @return
     */
    private Set<StateAction> getDecisionSetFromVisits(Set<VisitObj> visits) {
        Set<StateAction> stateActions = new HashSet<>();
        for (VisitObj visit : visits) {
            StateAction stateAction = StateAction.getVisitState(visit, this.timeStep, this.elapsed, this.timeHorizon);
            setNumberOfSurroundingVehiclesAtNextLocation(stateAction);
            stateActions.add(stateAction);
            //visit.setVehicleState(vehicleState);
        }
        return stateActions;
    }

    public FleetStateActionSpace(Set<Vehicle> vehicles, Set<User> requests, int timeStep, int timeHorizon) {
        this.vehicles = vehicles;
        this.requests = requests;
        this.timeStep = timeStep;
        this.timeHorizon = timeHorizon;
    }

    /**
     * Associate each vehicle to the set of feasible decisions it can execute.
     * Each decision correspond to a new VehicleState, which is derived from realizing the decision.
     *
     * @param vehicles
     * @param requests
     * @param vehicleVisitMap
     * @param timeStep
     * @param timeHorizon
     */
    public FleetStateActionSpace(Set<Vehicle> vehicles, Set<User> requests, Map<Vehicle, Set<VisitObj>> vehicleVisitMap, int timeStep, int timeHorizon) {
        this.vehicles = vehicles;
        this.requests = requests;
        this.timeStep = timeStep;
        this.timeHorizon = timeHorizon;
        this.vehicleVisitMap = vehicleVisitMap;
        this.elapsed = 0;

        this.vehicleCurrentStateMap = new ConcurrentHashMap<>();
        for (Vehicle vehicle : vehicles) {
            StateAction currentState = StateAction.getVisitState(vehicle, timeStep, 0, timeHorizon);
            setNumberOfSurroundingVehiclesAtNextLocation(currentState);
            vehicleCurrentStateMap.put(vehicle, currentState);
        }
        this.createVehicleDecisions();
    }

    protected void setNumberOfSurroundingVehiclesAtNextLocation(StateAction state) {
        int nOfSurroundingVehicles = 0;
        for (Vehicle otherVehicle : this.vehicles) {
            if (!otherVehicle.equals(state.vehicle))
                if (StateAction.vehicleCanReach(state, otherVehicle)) {
                    nOfSurroundingVehicles++;
                }
        }
        state.setVehicleCount(nOfSurroundingVehicles);
        state.setVehicleCountNormal((double) nOfSurroundingVehicles / this.vehicles.size());
    }
}
