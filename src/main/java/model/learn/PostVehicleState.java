package model.learn;

import dao.Dao;
import model.User;
import model.node.*;

import java.util.HashSet;

class PostVehicleState extends VehicleState {
    protected VehicleState preVehicleState;
    protected int indexInsertionArrival;

    public PostVehicleState(VehicleState preVehicleState, int timestep) {
        super(preVehicleState.preDecisionTime, timestep, preVehicleState.totalTimeHorizon);
        this.preVehicleState = preVehicleState;
        this.vehicle = preVehicleState.vehicle;
        this.vehicleCapacity = preVehicleState.vehicleCapacity;
        this.visit = preVehicleState.visit;
        this.requests = new HashSet<>(preVehicleState.requests);
        this.passengers = new HashSet<>(preVehicleState.passengers);
        this.requestCount = preVehicleState.requestCount;
        this.totalDelay = preVehicleState.totalDelay;
        this.totalDelayBonus = preVehicleState.totalDelayBonus;

        this.indexInsertionArrival = Dao.getInstance().getInsertionPoint(
                this.postDecisionTime,
                preVehicleState.nodeArrivals);

        this.stepForward();
    }

    private void stepForward() {

        boolean vehicleHasVisitedAllNodes = indexInsertionArrival == preVehicleState.nodeArrivals.size();
        boolean vehicleWasStopped = preVehicleState.nodeArrivals.size() == 1;

        if (vehicleHasVisitedAllNodes || vehicleWasStopped) {
            stopVehicleAtLastVisitedNode();
        } else {

            updateLastVisitedNode();

            // Simulate pickup and delivery of users up to the insertion index
            processVisitedNodesUntilIndex();

            // Maintain the data of the pre-decision state
            keepNodesToVisitFromIndex();
        }
    }

    private void stopVehicleAtLastVisitedNode() {

        this.requests.clear();
        this.passengers.clear();
        this.requestCount = 0;
        this.totalDelay = 0;
        this.totalDelayBonus = 0;

        // Vehicle is stopped at last visited location or continues stopped
        Node previousNode = preVehicleState.nodes.get(indexInsertionArrival - 1);
        this.nodeArrivals.add(this.postDecisionTime);
        this.delays.add(0);
        this.delayBonuses.add(0);
        this.normalArrivalDelays.add(0.0);
        this.remaining.add(0);
        this.loads.add(0);
        this.networkIds.add(preVehicleState.networkIds.get(indexInsertionArrival - 1));
        this.nodeLabels.add("ST(" + preVehicleState.nodeLabels.get(indexInsertionArrival - 1) + ")");
        NodeStop stop = new NodeStop(previousNode, preVehicleState.getVehicle().getId(), this.postDecisionTime);
        this.nodes.add(stop);
        this.normalOccupancyRates.add(0.0);
    }

    private void updateLastVisitedNode() {

        // Vehicle is stopped at last visited location or continues stopped
        Node previousNode = preVehicleState.nodes.get(indexInsertionArrival - 1);

        int waypointNetworkId = this.getWayPointNetworkId();
        int vehicleOriginNetworkId = preVehicleState.networkIds.get(indexInsertionArrival - 1);
        int vehicleOriginDeparture = preVehicleState.nodeArrivals.get(indexInsertionArrival - 1);
        int vehicleDestinationNetworkId = preVehicleState.networkIds.get(indexInsertionArrival);

        // Check if a middle point was reached
        boolean waypointIsNotOrigin = waypointNetworkId != vehicleOriginNetworkId;
        boolean waypointIsNotDestination = waypointNetworkId != vehicleDestinationNetworkId;

        if (waypointIsNotOrigin && waypointIsNotDestination) {

            int distOriginWaypoint = Dao.getInstance().getDistSec(vehicleOriginNetworkId, waypointNetworkId);
            int arrivalAtWaypoint = vehicleOriginDeparture + distOriginWaypoint;
            Node nextNode = preVehicleState.nodes.get(indexInsertionArrival);

            NodeMiddle earliestMiddleNode = new NodeMiddle(
                    waypointNetworkId,
                    arrivalAtWaypoint,
                    previousNode,
                    nextNode);

            this.nodeArrivals.add(arrivalAtWaypoint);
            this.delays.add(0);
            this.delayBonuses.add(0);
            this.normalArrivalDelays.add(0.0);
            this.remaining.add(0);
            this.loads.add(0);
            this.networkIds.add(waypointNetworkId);
            this.nodeLabels.add(getMiddleNodeLabel(preVehicleState, indexInsertionArrival));
            this.nodes.add(earliestMiddleNode);
            Double previousOccupancy = preVehicleState.normalOccupancyRates.get(indexInsertionArrival - 1);
            this.normalOccupancyRates.add(previousOccupancy);
        }
    }


    protected static String getMiddleNodeLabel(VehicleState state, int indexInsertionArrival) {
        return "MI(" + state.nodeLabels.get(indexInsertionArrival - 1) + " - " + state.nodeLabels.get(indexInsertionArrival) + ")";
    }


    private int getWayPointNetworkId() {
        // Find waypoint between last visited node and next destination node
        int vehicleOriginNetworkId = preVehicleState.networkIds.get(indexInsertionArrival - 1);
        int vehicleOriginDeparture = preVehicleState.nodeArrivals.get(indexInsertionArrival - 1);
        int vehicleDestinationNetworkId = preVehicleState.networkIds.get(indexInsertionArrival);

        int waypointNetworkId = Dao.getInstance().getIntermediateNodeNetworkId(
                vehicleOriginNetworkId,
                vehicleDestinationNetworkId,
                this.postDecisionTime - vehicleOriginDeparture);

        return waypointNetworkId;
    }

    private void processVisitedNodesUntilIndex() {
        for (int idxNode = 0; idxNode < indexInsertionArrival; idxNode++) {

            Node servicedNode = preVehicleState.nodes.get(idxNode);

            if (servicedNode instanceof NodePK) {
                User user = User.mapOfUsers.get(servicedNode.getTripId());
                this.passengers.add(user);
                this.requests.remove(user);
                this.requestCount--;

                // Delay bonus refer to both pickup and drop-off nodes
                this.totalDelayBonus -= preVehicleState.delayBonuses.get(idxNode);
            }

            if (servicedNode instanceof NodeDP) {
                User user = User.mapOfUsers.get(servicedNode.getTripId());
                this.passengers.remove(user);

                // Total delay decreases once a user is delivered
                this.totalDelay -= preVehicleState.delays.get(idxNode);
                this.totalDelayBonus -= preVehicleState.delayBonuses.get(idxNode);
            }
        }
    }

    private void keepNodesToVisitFromIndex() {
        for (int pos = indexInsertionArrival; pos < preVehicleState.nodeArrivals.size(); pos++) {
            String nodeLabel = preVehicleState.nodeLabels.get(pos);
            this.nodeArrivals.add(preVehicleState.nodeArrivals.get(pos));
            this.delays.add(preVehicleState.delays.get(pos));
            this.delayBonuses.add(preVehicleState.delayBonuses.get(pos));
            this.normalArrivalDelays.add(preVehicleState.normalArrivalDelays.get(pos));
            this.remaining.add(preVehicleState.remaining.get(pos));
            this.loads.add(preVehicleState.loads.get(pos));
            this.networkIds.add(preVehicleState.networkIds.get(pos));
            this.nodeLabels.add(nodeLabel);
            this.nodes.add(preVehicleState.nodes.get(pos));
            this.normalOccupancyRates.add(preVehicleState.normalOccupancyRates.get(pos));
        }
    }
}
