package model;

import dao.Dao;
import model.node.*;
import simulation.Simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class VisitState {

    private List<Node> nodes;
    private Integer vehicleCountAtOrigin;
    private Integer vehicleCapacity;
    private Integer timestep;
    private List<Double> occupancyRates;
    private List<Integer> arrivals;
    private List<Integer> ids;
    private List<String> nodeIds;
    private List<Integer> loads;
    private List<Integer> remaining;
    private List<Integer> delays;
    private List<Double> normalizedArrivalDelays;
    private Visit visit;

    public VisitState(Vehicle v) {

        this.arrivals = new ArrayList<>();
        this.ids = new ArrayList<>();
        this.nodeIds = new ArrayList<>();
        Visit visit = v.getVisit();
        ids.add(v.getLastVisitedNode().getNetworkId());
        arrivals.add(visit.getDeparture());
        nodeIds.add(visit.getVehicle().toString());

        if (visit != null) {
            if (visit instanceof VisitRelocation || visit instanceof VisitDisplaceAndStop) {
                ids.add(visit.getTargetNode().getNetworkId());
                arrivals.add(visit.getTargetNode().getArrivalSoFar());
                nodeIds.add(visit.getTargetNode().toString());
            } else {
                for (Node n : visit.getSequenceVisits()) {
                    ids.add(n.getNetworkId());
                    arrivals.add(n.getArrivalSoFar());
                    nodeIds.add(n.toString());
                }
            }
        }
    }

    public Vehicle getVehicle() {
        return this.visit.getVehicle();
    }

    public VisitState(Visit visit) {
        this.init();

        this.visit = visit;
        this.timestep = Simulation.rightTW;
        processLastVisitedNode();

        if (visit != null) {
            if (visit instanceof VisitRelocation || visit instanceof VisitDisplaceAndStop || visit instanceof VisitStop) {
                processNextDestination();

            } else {
                processDestinationSequence();
            }
        }
    }

    private void processDestinationSequence() {

        int departureTime = this.visit.getDeparture();
        Node originNode = this.visit.getVehicle().getLastVisitedNode();
        int currentLoad = this.visit.getVehicle().getCurrentLoad();

        for (Node destinationNode : this.visit.getSequenceVisits()) {

            int distanceLeg = Dao.getInstance().getDistSec(originNode, destinationNode);

            ids.add(destinationNode.getNetworkId());
            nodeIds.add(destinationNode.toString());
            nodes.add(destinationNode);

            loads.add(destinationNode.getLoad());
            currentLoad += destinationNode.getLoad();
            occupancyRates.add(getOccupancyRate(currentLoad));

            int arrivalTime = departureTime + distanceLeg;
            arrivals.add(arrivalTime);
            delays.add(getDelay(arrivalTime, destinationNode));

            int remainingToRebalancingTarget = arrivalTime - Simulation.rightTW;
            remaining.add(remainingToRebalancingTarget);

            double normalDelay = getNormalDelay(destinationNode, arrivalTime);
            normalizedArrivalDelays.add(normalDelay);

            departureTime = departureTime + distanceLeg;
            originNode = destinationNode;
        }
    }

    private double getOccupancyRate(int load) {
        return Double.valueOf(load) / this.visit.getVehicle().getCapacity();
    }

    private void processNextDestination() {

        if (vehicleIsNotParking()) {
            Node targetNode = this.visit.getTargetNode();
            ids.add(targetNode.getNetworkId());
            arrivals.add(targetNode.getArrivalSoFar());
            nodeIds.add(targetNode.toString());
            nodes.add(targetNode);
            loads.add(0);
            int remainingToRebalancingTarget = targetNode.getArrivalSoFar() - Simulation.rightTW;
            remaining.add(remainingToRebalancingTarget);
            delays.add(0);
            normalizedArrivalDelays.add(0.0);
            occupancyRates.add(0.0);
        }
    }

    private boolean vehicleIsNotParking() {
        return this.ids.get(0) != visit.getTargetNode().getNetworkId();
    }

    private void processLastVisitedNode() {
        Vehicle vehicle = this.visit.getVehicle();
        this.vehicleCapacity = vehicle.getCapacity();
        this.ids.add(vehicle.getLastVisitedNode().getNetworkId());
        this.arrivals.add(vehicle.getEarliestDeparture());
        this.nodeIds.add(vehicle.toString());
        this.nodes.add(vehicle.getLastVisitedNode());
        this.loads.add(0);
        this.remaining.add(0);
        this.delays.add(0);
        this.normalizedArrivalDelays.add(0.0);
        this.occupancyRates.add(getOccupancyRate(vehicle.getCurrentLoad()));
    }

    private double getNormalDelay(Node destination, int arrival) {
        double normalDelay = 0;
        if (isPickupOrDeliveryNode(destination))
            normalDelay = Double.valueOf(arrival - destination.getEarliest()) / destination.getMaxDelay();
        return normalDelay;
    }

    public VisitState() {
        init();
    }

    private void init() {
        this.arrivals = new ArrayList<>();
        this.ids = new ArrayList<>();
        this.nodeIds = new ArrayList<>();
        this.loads = new ArrayList<>();
        this.remaining = new ArrayList<>();
        this.delays = new ArrayList<>();
        this.normalizedArrivalDelays = new ArrayList<>();
        this.occupancyRates = new ArrayList<>();
        this.timestep = null;
        this.vehicleCapacity = null;
        this.vehicleCountAtOrigin = null;
        this.nodes = new ArrayList<>();
        this.visit = null;
    }

    public static VisitState getPostVisitState(VisitState currentState, int timeWindow, Map<Integer, Integer> pickupLocationCandidateVehicleCountMap) {
        VisitState v = getPostVisitState(currentState, timeWindow);
        // If you take this action, how many vehicles could also d
        v.vehicleCountAtOrigin = pickupLocationCandidateVehicleCountMap.getOrDefault(v.ids.get(0), 0);
        return v;
    }

    public static VisitState getPostVisitState(VisitState state, int timeWindow) {

        VisitState postVisit = new VisitState();
        Integer departureVehicleOrigin = state.arrivals.get(0);
        int nextTimestep = Simulation.rightTW + timeWindow;
        double timestepNormal = Double.valueOf(nextTimestep) / Simulation.timeHorizon;
        int elapsed = nextTimestep - departureVehicleOrigin;
        int i = Dao.getInstance().getInsertionPoint(nextTimestep, state.arrivals);

        postVisit.timestep = nextTimestep;
        postVisit.vehicleCapacity = state.vehicleCapacity;

        System.out.printf("Timestep normal: %.2f\n", timestepNormal);
        System.out.println("Insertion point: " + i);
        System.out.println("        Elapsed: " + elapsed);
        System.out.println("       Stepsize: " + timeWindow);
        System.out.println("  Next timestep: " + nextTimestep);

        // Vehicle has not reached the next node but may have reached another middle point

        // Vehicle is stopped at last visited location or continues stopped
        if (i == state.arrivals.size() || state.arrivals.size() == 1) {
            postVisit.arrivals.add(Simulation.rightTW + timeWindow);
            postVisit.delays.add(0);
            postVisit.normalizedArrivalDelays.add(0.0);
            postVisit.remaining.add(0);
            postVisit.loads.add(0);
            postVisit.ids.add(state.ids.get(i - 1));
            postVisit.nodeIds.add("ST(" + state.nodeIds.get(i - 1) + ")");
            postVisit.nodes.add(new NodeStop(state.nodes.get(i - 1), state.getVehicle().getId(), nextTimestep));
            postVisit.occupancyRates.add(0.0);
        } else {

            // Find waypoint between last visited node and next destination node
            int vehicleOriginNetworkId = state.ids.get(i - 1);
            int vehicleOriginDeparture = state.arrivals.get(i - 1);
            int vehicleDestinationNetworkId = state.ids.get(i);
            int waypointNetworkId = Dao.getInstance().getIntermediateNodeNetworkId(vehicleOriginNetworkId, vehicleDestinationNetworkId, nextTimestep - vehicleOriginDeparture);

            // Check if a middle point was reached
            if (waypointNetworkId != vehicleOriginNetworkId && waypointNetworkId != vehicleDestinationNetworkId) {
                int distOriginWaypoint = Dao.getInstance().getDistSec(vehicleOriginNetworkId, waypointNetworkId);
                int arrivalAtWaypoint = vehicleOriginDeparture + distOriginWaypoint;
                postVisit.arrivals.add(arrivalAtWaypoint);
                postVisit.delays.add(0);
                postVisit.normalizedArrivalDelays.add(0.0);
                postVisit.remaining.add(0);
                postVisit.loads.add(0);
                postVisit.ids.add(waypointNetworkId);
                postVisit.nodeIds.add("MI(" + state.nodeIds.get(i - 1) + " - " + state.nodeIds.get(i) + ")");
                postVisit.nodes.add(new NodeMiddle(waypointNetworkId, arrivalAtWaypoint, state.nodes.get(i - 1), state.nodes.get(i), distOriginWaypoint));
                postVisit.occupancyRates.add(state.occupancyRates.get(i - 1));
            }

            for (int j = i; j < state.arrivals.size(); j++) {
                postVisit.arrivals.add(state.arrivals.get(j));
                postVisit.delays.add(state.delays.get(j));
                postVisit.normalizedArrivalDelays.add(state.normalizedArrivalDelays.get(j));
                postVisit.remaining.add(state.remaining.get(j));
                postVisit.loads.add(state.loads.get(j));
                postVisit.ids.add(state.ids.get(j));
                postVisit.nodeIds.add(state.nodeIds.get(j));
                postVisit.nodes.add(state.nodes.get(j));
                postVisit.occupancyRates.add(state.occupancyRates.get(j));
            }
        }
        int reward = state.loads.subList(0, i).stream().reduce(0, Integer::sum);
        return postVisit;
    }

    public static VisitState getVisitState(Visit visit) {

        VisitState vs = new VisitState();
        vs.visit = visit;

//        # Normalising Inputs
//        current_time_input = (current_time - self.envt.START_EPOCH) / (self.envt.STOP_EPOCH - self.envt.START_EPOCH)
//        num_requests_input = num_requests / self.envt.NUM_AGENTS
//        num_other_agents_input = num_other_agents / self.envt.NUM_AGENTS

        vs.processLastVisitedNode();


        if (visit != null) {


            if (visit instanceof VisitRelocation || visit instanceof VisitDisplaceAndStop || visit instanceof VisitStop) {
                vs.processNextDestination();
            } else {
                int departureTime = visit.getDeparture();
                Node originNode = visit.getVehicle().getLastVisitedNode();
                int currentLoad = visit.getVehicle().getCurrentLoad();

                int maxDelay = originNode.getMaxDelay();
                Double delay = 0.0;
                for (Node destinationNode : visit.getSequenceVisits()) {

                    int distanceLeg = Dao.getInstance().getDistSec(originNode, destinationNode);

                    if (distanceLeg == 0) {
                        currentLoad += destinationNode.getLoad();
                        maxDelay += destinationNode.getMaxDelay();
                        delay += getDelay(departureTime, originNode);

                        vs.loads.set(vs.loads.size() - 1, currentLoad);
                        vs.occupancyRates.set(vs.occupancyRates.size() - 1, Double.valueOf(currentLoad) / vs.vehicleCapacity);
                        vs.delays.set(vs.delays.size() - 1, vs.delays.get(vs.delays.size() - 1) + departureTime - destinationNode.getEarliest());
                        vs.normalizedArrivalDelays.set(vs.normalizedArrivalDelays.size() - 1, delay / maxDelay);

                        originNode = destinationNode;
                        continue;
                    }
                    vs.ids.add(destinationNode.getNetworkId());
                    vs.nodeIds.add(destinationNode.toString());
                    vs.nodes.add(destinationNode);

                    vs.loads.add(destinationNode.getLoad());
                    currentLoad += destinationNode.getLoad();
                    vs.occupancyRates.add(vs.getOccupancyRate(currentLoad));

                    int arrivalTime = departureTime + distanceLeg;
                    vs.arrivals.add(arrivalTime);
                    vs.delays.add(arrivalTime - destinationNode.getEarliest());

                    int remainingToRebalancingTarget = arrivalTime - Simulation.rightTW;
                    vs.remaining.add(remainingToRebalancingTarget);

                    maxDelay = destinationNode.getMaxDelay();
                    delay = Double.valueOf(arrivalTime - destinationNode.getEarliest());
                    vs.normalizedArrivalDelays.add(delay / maxDelay);

                    departureTime = departureTime + distanceLeg;
                    originNode = destinationNode;
                }
            }
        }

        return vs;
    }

    private static int getDelay(int arrivalTime, Node node) {
        return isPickupOrDeliveryNode(node) ? arrivalTime - node.getEarliest() : 0;
    }

    private static boolean isPickupOrDeliveryNode(Node n) {
        return n instanceof NodeDP || n instanceof NodePK;
    }

    @Override
    public String toString() {
        return "VisitSnapShot{" +
                ", lenght=" + arrivals.size() +
                ", timestep=" + timestep +
                ", vehicleSize=" + vehicleCapacity +
                ", arrivals=" + arrivals +
                ", ids=" + ids +
                ", node Ids=" + nodeIds +
                ", nodes=" + nodes +
                ", loads=" + loads +
                ", capacity=" + occupancyRates +
                ", remaining=" + remaining +
                ", delays=" + delays +
                ", normalDelays=" + normalizedArrivalDelays.stream().map(aDouble -> String.format("%.2f", aDouble)).collect(Collectors.toList()) +
                ", reward=" + loads.stream().reduce(0, Integer::sum) +
                (this.vehicleCountAtOrigin != null ? ", Count at " + ids.get(0) + " = " + this.vehicleCountAtOrigin : "") +
                '}';
    }

}
