package model.learn;

import model.demand.User;
import model.Vehicle;
import model.node.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class VehicleStateActionSpace {

    protected int decisionCount;
    protected ArrayList<Integer> requestCounts;
    protected Set<StateAction> stateActions;
    protected int vehicleId;
    protected List<Double> currentTimes;
    protected List<Double> numRequests;
    protected List<Integer> totalDelay;
    public List<Integer> totalDelayBonus;
    protected List<Integer> capacity;
    protected List<Double> normalCapacity;
    protected List<List<Integer>> nextNetworkIds;
    protected List<List<Integer>> requestIds;
    protected List<List<Double>> arrivalDelays;
    protected List<List<Double>> occupancyRates;
    protected List<List<Node>> nodes;
    protected List<Double> surroundingVehiclesCount;

    public VehicleStateActionSpace(Vehicle vehicle, Set<StateAction> stateActions, Double requestShare) {
        this.normalCapacity = new ArrayList<>();
        this.vehicleId = vehicle.getId();
        this.stateActions = stateActions;
        this.capacity = new ArrayList<>();
        this.currentTimes = new ArrayList<>();
        this.numRequests = new ArrayList<>();
        this.requestIds = new ArrayList<>();
        this.arrivalDelays = new ArrayList<>();
        this.occupancyRates = new ArrayList<>();
        this.nextNetworkIds = new ArrayList<>();
        this.surroundingVehiclesCount = new ArrayList<>();
        this.totalDelay = new ArrayList<>();
        this.totalDelayBonus = new ArrayList<>();
        this.nodes = new ArrayList<>();
        this.requestCounts = new ArrayList<>();
        this.decisionCount = 0;

        for (StateAction stateAction : stateActions) {
            this.decisionCount++;
            normalCapacity.add((double) (stateAction.getCapacity()/ FleetStateActionSpaceObject.MAX_CAPACITY_VEHICLE));
            capacity.add(stateAction.getCapacity());
            nextNetworkIds.add(stateAction.networkIds);
            currentTimes.add(stateAction.normalPostDecisionTime);
            arrivalDelays.add(stateAction.normalArrivalDelays);
            occupancyRates.add(stateAction.normalOccupancyRates.stream().map(o->1-o).collect(Collectors.toList()));
            surroundingVehiclesCount.add(stateAction.shareOfSurroundingVehicles);
            nodes.add(stateAction.nodes);
            numRequests.add(requestShare); //TODO add VR share?
            requestIds.add(stateAction.getRequests().stream().map(User::getId).collect(Collectors.toList()));
            totalDelay.add(stateAction.getDelay());
            totalDelayBonus.add(stateAction.getDelayBonus());
            requestCounts.add(stateAction.getRequests().size());

        }
    }

}
