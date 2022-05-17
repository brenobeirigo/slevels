package model.learn;

import model.User;
import model.Vehicle;
import model.node.Node;
import org.checkerframework.checker.units.qual.A;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class VehicleDecisionSpace {

    protected ArrayList<Integer> requestCounts;
    protected Set<VehicleState> vehicleStates;
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

    public VehicleDecisionSpace(Vehicle vehicle, Set<VehicleState> vehicleStates, Double requestShare) {
        this.normalCapacity = new ArrayList<>();
        this.vehicleId = vehicle.getId();
        this.vehicleStates = vehicleStates;
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

        for (VehicleState vehicleState : vehicleStates) {
            normalCapacity.add((double) (vehicleState.getCapacity()/DecisionSpaceObject.MAX_CAPACITY_VEHICLE));
            capacity.add(vehicleState.getCapacity());
            nextNetworkIds.add(vehicleState.networkIds);
            currentTimes.add(vehicleState.normalPostDecisionTime);
            assert vehicleState.normalArrivalDelays.size() <= 9:String.format("%s", vehicleState.nodes);
            arrivalDelays.add(vehicleState.normalArrivalDelays);
            occupancyRates.add(vehicleState.normalOccupancyRates.stream().map(o->1-o).collect(Collectors.toList()));
            surroundingVehiclesCount.add(vehicleState.shareOfSurroundingVehicles);
            nodes.add(vehicleState.nodes);
            numRequests.add(requestShare); //TODO add VR share?
            requestIds.add(vehicleState.getRequests().stream().map(User::getId).collect(Collectors.toList()));
            totalDelay.add(vehicleState.getDelay());
            totalDelayBonus.add(vehicleState.getDelayBonus());
            requestCounts.add(vehicleState.getRequests().size());

        }
    }

}
