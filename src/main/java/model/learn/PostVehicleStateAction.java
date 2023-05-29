package model.learn;

import dao.Dao;
import model.demand.User;
import model.route.RouteUtil;
import model.visit.VisitRelocation;
import model.node.*;
import simulation.Environment;

import java.util.HashSet;

class PostVehicleStateAction extends StateAction {
    protected StateAction preStateAction;
    protected int indexInsertionArrival;
    protected int elapsedTime;

    public PostVehicleStateAction(StateAction preStateAction, int elapsedTime, Environment env) {
        super(preStateAction.timeStep, elapsedTime, preStateAction.timeHorizon);
        this.elapsedTime = elapsedTime;
        this.preStateAction = preStateAction;
        this.vehicle = preStateAction.vehicle;
        this.vehicleCapacity = preStateAction.vehicleCapacity;
        this.visit = preStateAction.visit;
        this.requests = new HashSet<>(preStateAction.requests);
        this.passengers = new HashSet<>(preStateAction.passengers);
        this.requestCount = preStateAction.requestCount;
        this.totalDelay = preStateAction.totalDelay;
        this.totalDelayBonus = preStateAction.totalDelayBonus;

        this.indexInsertionArrival = RouteUtil.getInsertionPoint(
                this.postDecisionTimeStep,
                preStateAction.nodeArrivals);

        this.stepForward(env);
    }

    private void stepForward(Environment env) {

        boolean vehicleHasVisitedAllNodes = indexInsertionArrival == preStateAction.nodeArrivals.size();
        boolean vehicleWasStopped = preStateAction.nodeArrivals.size() == 1;

        if (vehicleHasVisitedAllNodes || vehicleWasStopped) {
            stopVehicleAtLastVisitedNode();
        } else {

            updateLastVisitedNode(env);

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
        Node previousNode = preStateAction.nodes.get(indexInsertionArrival - 1);
        this.nodeArrivals.add(this.postDecisionTimeStep);
        this.delays.add(0);
        this.delayBonuses.add(0);
        this.normalArrivalDelays.add(0.0);
        this.remaining.add(0);
        this.loads.add(0);
        this.networkIds.add(preStateAction.networkIds.get(indexInsertionArrival - 1));
        this.nodeLabels.add("ST(" + preStateAction.nodeLabels.get(indexInsertionArrival - 1) + ")");
        NodeStop stop = new NodeStop(previousNode, preStateAction.getVehicle().getId(), this.postDecisionTimeStep);
        this.nodes.add(stop);
        this.normalOccupancyRates.add(0.0);
    }

    private void updateLastVisitedNode(Environment env) {

        // Vehicle is stopped at last visited location or continues stopped
        Node previousNode = preStateAction.nodes.get(indexInsertionArrival - 1);

        int waypointNetworkId = this.getWayPointNetworkId(env);
        int vehicleOriginNetworkId = preStateAction.networkIds.get(indexInsertionArrival - 1);
        int vehicleOriginDeparture = preStateAction.nodeArrivals.get(indexInsertionArrival - 1);
        int vehicleDestinationNetworkId = preStateAction.networkIds.get(indexInsertionArrival);

        // Check if a middle point was reached
        boolean waypointIsNotOrigin = waypointNetworkId != vehicleOriginNetworkId;
        boolean waypointIsNotDestination = waypointNetworkId != vehicleDestinationNetworkId;

        if (waypointIsNotOrigin && waypointIsNotDestination) {

            int distOriginWaypoint = env.getNetwork().getDistSec(vehicleOriginNetworkId, waypointNetworkId);
            int arrivalAtWaypoint = vehicleOriginDeparture + distOriginWaypoint;
            Node nextNode = preStateAction.nodes.get(indexInsertionArrival);

            NodeWaypoint earliestMiddleNode = new NodeWaypoint(
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
            this.nodeLabels.add(getMiddleNodeLabel(preStateAction, indexInsertionArrival));
            this.nodes.add(earliestMiddleNode);
            Double previousOccupancy = preStateAction.normalOccupancyRates.get(indexInsertionArrival - 1);
            this.normalOccupancyRates.add(previousOccupancy);
        }
    }


    protected static String getMiddleNodeLabel(StateAction state, int indexInsertionArrival) {
        return "MI(" + state.nodeLabels.get(indexInsertionArrival - 1) + " - " + state.nodeLabels.get(indexInsertionArrival) + ")";
    }


    private int getWayPointNetworkId(Environment env) {
        // Find waypoint between last visited node and next destination node
        int vehicleOriginNetworkId = preStateAction.networkIds.get(indexInsertionArrival - 1);
        int vehicleOriginDeparture = preStateAction.nodeArrivals.get(indexInsertionArrival - 1);
        int vehicleDestinationNetworkId = preStateAction.networkIds.get(indexInsertionArrival);

        int waypointNetworkId = env.getNetwork().getIntermediateNodeNetworkId(
                vehicleOriginNetworkId,
                vehicleDestinationNetworkId,
                this.postDecisionTimeStep - vehicleOriginDeparture);

        return waypointNetworkId;
    }

    private void processVisitedNodesUntilIndex() {
        for (int idxNode = 0; idxNode < indexInsertionArrival; idxNode++) {

            Node servicedNode = preStateAction.nodes.get(idxNode);

            if (servicedNode instanceof NodePickup) {
                User user = User.mapOfUsers.get(servicedNode.getTripId());
                this.passengers.add(user);
                this.requests.remove(user);
                this.requestCount--;

                // Delay bonus refer to both pickup and drop-off nodes
                this.totalDelayBonus -= preStateAction.delayBonuses.get(idxNode);
            }

            if (servicedNode instanceof NodeDropoff) {
                User user = User.mapOfUsers.get(servicedNode.getTripId());
                this.passengers.remove(user);

                // Total delay decreases once a user is delivered
                this.totalDelay -= preStateAction.delays.get(idxNode);
                this.totalDelayBonus -= preStateAction.delayBonuses.get(idxNode);
            }
        }
    }

    private void keepNodesToVisitFromIndex() {
        // If relocation visit, keep only the target into sequence
        if (this.visit instanceof VisitRelocation) {
            int pos = preStateAction.nodeArrivals.size() - 1;
            String nodeLabel2 = preStateAction.nodeLabels.get(pos);
            this.nodeArrivals.add(preStateAction.nodeArrivals.get(pos));
            this.delays.add(preStateAction.delays.get(pos));
            this.delayBonuses.add(preStateAction.delayBonuses.get(pos));
            this.normalArrivalDelays.add(preStateAction.normalArrivalDelays.get(pos));
            this.remaining.add(preStateAction.remaining.get(pos));
            this.loads.add(preStateAction.loads.get(pos));
            this.networkIds.add(preStateAction.networkIds.get(pos));
            this.nodeLabels.add(nodeLabel2);
            this.nodes.add(preStateAction.nodes.get(pos));
            this.normalOccupancyRates.add(preStateAction.normalOccupancyRates.get(pos));
        } else {
            for (int pos = indexInsertionArrival; pos < preStateAction.nodeArrivals.size(); pos++) {
                String nodeLabel = preStateAction.nodeLabels.get(pos);
                this.nodeArrivals.add(preStateAction.nodeArrivals.get(pos));
                this.delays.add(preStateAction.delays.get(pos));
                this.delayBonuses.add(preStateAction.delayBonuses.get(pos));
                this.normalArrivalDelays.add(preStateAction.normalArrivalDelays.get(pos));
                this.remaining.add(preStateAction.remaining.get(pos));
                this.loads.add(preStateAction.loads.get(pos));
                this.networkIds.add(preStateAction.networkIds.get(pos));
                this.nodeLabels.add(nodeLabel);
                this.nodes.add(preStateAction.nodes.get(pos));
                this.normalOccupancyRates.add(preStateAction.normalOccupancyRates.get(pos));
            }
        }
    }
}
