package model.learn;

import dao.Dao;
import model.User;
import model.Vehicle;
import model.VisitObj;
import simulation.Simulation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


public class StateSpace {

    protected Dao environment;
    protected Map<Vehicle, Set<VisitObj>> vehicleVisitMap;
    protected Map<Vehicle, VehicleState> vehicleCurrentStateMap;
    protected Map<Vehicle, Set<VehicleState>> vehicleDecisionsMap;
    protected Map<User, Set<VehicleState>> userVisitMap;
    public Set<Vehicle> vehicles;
    public Set<User> requests;
    protected int currentTime;
    protected int timeHorizon;
    protected int elapsed;

    public Map<Vehicle, Set<VehicleState>> getVehicleDecisionsMap() {
        return vehicleDecisionsMap;
    }

    public void setVehicleDecisionsMap(Map<Vehicle, Set<VehicleState>> vehicleDecisionsMap) {
        this.vehicleDecisionsMap = vehicleDecisionsMap;
    }

    public DecisionSpaceObject getCurrentStateObject(){
        List<VehicleDecisionSpace> vehicleDecisionSpaces = getVehicleCurrentDecision();
        return new DecisionSpaceObject(vehicleDecisionSpaces, false);
    }

    public DecisionSpaceObject getDecisionSpaceObject() {


        List<VehicleDecisionSpace> vehicleDecisionSetList = getVehicleDecisionSpaces();

        // Create summary object of decision space:
        // - current_time_input
        // - num_requests_input
        // - delay_input
        // - path_location_input
        // - other_agents_input
        // - occupancy_input
        // - request_ids
        return new DecisionSpaceObject(vehicleDecisionSetList, false);
    }

    private List<VehicleDecisionSpace> getVehicleDecisionSpaces() {
        double requestShare = getRequestShare();
        return this.vehicles.stream().parallel().map(vehicle -> {

            Set<VehicleState> decisions = getVehicleDecisionsMap().get(vehicle);

            // Select for each vehicle:
            // - current time (share of total horizon)
            // - n. of requests (share)
            // - arrival delays
            // - occupancy at each node (share of total capacity)
            // - next nodes (network ids)
            // - surrounding vehicles count (share)

            return new VehicleDecisionSpace(vehicle, decisions, requestShare);
        }).collect(Collectors.toList());
    }

    private List<VehicleDecisionSpace> getVehicleCurrentDecision() {
        double requestShare = getRequestShare();
        return this.vehicles.stream().parallel().map(vehicle -> new VehicleDecisionSpace(vehicle, Collections.singleton(this.vehicleCurrentStateMap.get(vehicle)), requestShare)).collect(Collectors.toList());
    }

    private double getRequestShare() {
        return (double) this.requests.size() / this.vehicles.size();
    }

    private void createVehicleDecisions() {
        ConcurrentHashMap<Vehicle, Set<VehicleState>> vehicleDecisions = new ConcurrentHashMap<>();
        this.vehicleVisitMap.entrySet().stream().parallel().forEach(entryVehicleVisits -> {
            Vehicle vehicle = entryVehicleVisits.getKey();
            Set<VisitObj> visits = entryVehicleVisits.getValue();
            // Assumes pre-decision states of all the other vehicles
            vehicleDecisions.put(vehicle, getDecisionSetFromVisits(visits));
        });


        this.vehicleDecisionsMap = vehicleDecisions;
    }


    /**
     * Create Decision for each Visit.
     * Store decision in visit.
     * @param visits
     * @return
     */
    private Set<VehicleState> getDecisionSetFromVisits(Set<VisitObj> visits) {
        Set<VehicleState> vehicleStates = new HashSet<>();
        for (VisitObj visit : visits) {
            VehicleState vehicleState = VehicleState.getVisitState(visit, this.currentTime, this.elapsed, this.timeHorizon);
            setNumberOfSurroundingVehiclesAtNextLocation(vehicleState);
            vehicleStates.add(vehicleState);
            //visit.setVehicleState(vehicleState);
        }
        return vehicleStates;
    }

    public StateSpace(Set<Vehicle> vehicles, Set<User> requests, int currentTime, int timeHorizon) {
        this.vehicles = vehicles;
        this.requests = requests;
        this.currentTime = currentTime;
        this.timeHorizon = timeHorizon;
    }

    /**
     * Associate each vehicle to the set of feasible decisions it can execute.
     * Each decision correspond to a new VehicleState, which is derived from realizing the decision.
     *
     * @param vehicles
     * @param unassignedUsers
     * @param vehicleVisitMap
     * @param currentTime
     * @param timeHorizon
     */
    public StateSpace(Set<Vehicle> vehicles, Set<User> unassignedUsers, Map<Vehicle, Set<VisitObj>> vehicleVisitMap, int currentTime, int timeHorizon) {
        this.vehicles = vehicles;
        this.requests = unassignedUsers;
        this.currentTime = currentTime;
        this.timeHorizon = timeHorizon;
        this.vehicleVisitMap = vehicleVisitMap;
        this.elapsed = 0;

        this.vehicleCurrentStateMap = new ConcurrentHashMap<>();
        for (Vehicle vehicle : vehicles) {
            VehicleState currentState = VehicleState.getVisitState(vehicle, Simulation.rightTW, 0, Simulation.timeHorizon);
            setNumberOfSurroundingVehiclesAtNextLocation(currentState);
            vehicleCurrentStateMap.put(vehicle, currentState);
        }
        this.createVehicleDecisions();
    }

    protected void setNumberOfSurroundingVehiclesAtNextLocation(VehicleState state) {
        int nOfSurroundingVehicles = 0;
        for (Vehicle otherVehicle : this.vehicles) {
            if (!otherVehicle.equals(state.vehicle))
                if (VehicleState.vehicleCanReach(state, otherVehicle)) {
                    nOfSurroundingVehicles++;
                }
        }
        state.setVehicleCount(nOfSurroundingVehicles);
        state.setVehicleCountNormal((double) nOfSurroundingVehicles / this.vehicles.size());
    }
}
