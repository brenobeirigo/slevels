package model.learn;

import com.google.common.base.Objects;
import dao.Dao;
import model.*;
import model.node.Node;
import model.node.NodeDP;
import model.node.NodePK;

import java.util.*;
import java.util.stream.Collectors;

public class StateAction implements Comparable<StateAction>, VisitObj {

    private static final int MAX_PICKUP_DELAY = 300;
    protected Vehicle vehicle;
    private double vf;

    public VisitObj getVisit() {
        return visit;
    }

    protected VisitObj visit;
    protected Set<User> requests;
    protected HashSet<User> passengers;

    protected List<Node> nodes;
    protected List<Integer> networkIds;
    protected List<Integer> nodeArrivals;

    protected Integer totalDelay;
    protected Integer totalDelayBonus;
    protected Integer requestCount;
    protected Integer departure;
    private Integer vehicleCountAtOrigin;
    protected Integer vehicleCapacity;
    protected Integer postDecisionTimeStep;
    protected List<String> nodeLabels;
    protected List<Integer> loads;
    protected List<Integer> remaining;
    protected List<Integer> delays;
    protected List<Integer> delayBonuses;

    // Normalized
    protected List<Double> normalOccupancyRates;
    protected List<Double> normalArrivalDelays;
    protected Double normalPostDecisionTime;
    protected Double shareOfSurroundingVehicles;

    protected int timeStep;
    protected int timeHorizon;


    public StateAction(VisitObj visit, int timeStep, int timeHorizon, int timestepInterval) {
        this.init(timeStep, timestepInterval, timeHorizon);
        this.visit = visit;
        this.departure = visit.getVehicle().getEarliestDeparture();
        this.requests = new HashSet<>(visit.getRequests());
        this.passengers = new HashSet<>(visit.getPassengers());
        this.vehicle = visit.getVehicle();
        this.requestCount = visit.getRequests().size();
        this.totalDelay = visit.getDelay();
        this.totalDelayBonus = visit.getDelayBonus();
        assert this.totalDelayBonus != null;
        processLastVisitedNode();

        if (visit != null) {
            if (visit instanceof VisitRelocation || visit instanceof VisitDisplaceAndStop || visit instanceof VisitStop) {
                processNextDestination();

            } else {
                processDestinationSequence();
            }
        }
    }

    public StateAction(int timeStep, int elapsedTime, int timeHorizon) {
        init(timeStep, elapsedTime, timeHorizon);
    }

    private void init(int timeStep, int elapsedTime, int timeHorizon) {
//        this.targetNode = visit.getTargetNode();
//        this.isSetup = visit.isSetup();
        this.timeStep = timeStep;
        this.timeHorizon = timeHorizon;
        this.postDecisionTimeStep = timeStep + elapsedTime;
        this.nodeArrivals = new ArrayList<>();
        this.networkIds = new ArrayList<>();
        this.nodeLabels = new ArrayList<>();
        this.nodes = new ArrayList<>();
        this.loads = new ArrayList<>();
        this.remaining = new ArrayList<>();
        this.delays = new ArrayList<>();
        this.delayBonuses = new ArrayList<>();
        this.normalPostDecisionTime = Double.valueOf(this.postDecisionTimeStep) / this.timeHorizon;
        assert this.normalPostDecisionTime<=1:String.format("%s/%s", Double.valueOf(this.postDecisionTimeStep) , this.timeHorizon);
        this.normalArrivalDelays = new ArrayList<>();
        this.normalOccupancyRates = new ArrayList<>();
        this.vehicleCapacity = null;
        this.vehicleCountAtOrigin = null;
        this.visit = null;
    }

    public static StateAction realize(StateAction preVehicleStateStateAction, int rightTW, int timestep, Map<Integer, Integer> pickupLocationCandidateVehicleCountMap) {
        //VehicleState postVehicleState = realize(preVehicleStateState, timestep);
        StateAction postStateAction = preVehicleStateStateAction.stepForward(timestep);

        // If you take this action, how many vehicles could access the next location
        postStateAction.vehicleCountAtOrigin = pickupLocationCandidateVehicleCountMap.getOrDefault(postStateAction.networkIds.get(0), 0);
        return postStateAction;
    }


    public PostVehicleStateAction stepForward(int timeStep) {
        return new PostVehicleStateAction(this, timeStep);
    }

    //    public static VehicleState realize(VehicleState preVehicleState, int timestepInterval) {
//        VehicleState postVehicleState = new VehicleState(preVehicleState.preDecisionTime, timestepInterval, preVehicleState.totalTimeHorizon);
//        // Integer departureVehicleOrigin = preVehicleState.nodeArrivals.get(0);
//
//        postVehicleState.vehicle = preVehicleState.vehicle;
//        postVehicleState.vehicleCapacity = preVehicleState.vehicleCapacity;
//        postVehicleState.visit = preVehicleState.visit;
//        postVehicleState.requests = new HashSet<>(preVehicleState.requests);
//        postVehicleState.passengers = new HashSet<>(preVehicleState.passengers);
//        postVehicleState.requestCount = preVehicleState.requestCount;
//        postVehicleState.totalDelay = preVehicleState.totalDelay;
//        int nextTimestep = postVehicleState.postDecisionTime;
//        int indexInsertionArrival = Dao.getInstance().getInsertionPoint(nextTimestep, preVehicleState.nodeArrivals);
//
//
////        System.out.printf("Timestep normal: %.2f\n", postVehicleState.timestepNormal);
////        System.out.println("Insertion point: " + indexInsertionArrival);
////        System.out.println("        Elapsed: " + elapsed);
////        System.out.println("       Stepsize: " + timestepInterval);
////        System.out.println("  Next timestep: " + nextTimestep);
//
//        // Vehicle has not reached the next node but may have reached another middle point
//
//
//
//        boolean vehicleHasVisitedAllNodes = indexInsertionArrival == preVehicleState.nodeArrivals.size();
//        boolean vehicleWasStopped = preVehicleState.nodeArrivals.size() == 1;
//        if (vehicleHasVisitedAllNodes || vehicleWasStopped) {
//            updateStopNode(preVehicleState, postVehicleState, indexInsertionArrival);
//        } else {
//
//            updateLastVisitedNode(preVehicleState, postVehicleState, indexInsertionArrival);
//
//            // Simulate pickup and delivery of users up to the insertion index
//            processVisitedNodesUntilIndex(preVehicleState, postVehicleState, indexInsertionArrival);
//
//            // Maintain the data of the pre-decision state
//            keepNodesToVisitFromIndex(preVehicleState, postVehicleState, indexInsertionArrival);
//        }
//
////        assert preVehicleState.delays.stream().reduce(0, Integer::sum) == preVehicleState.totalDelay : String.format("%s -- %s vs. %s /// Visit: %s", preVehicleState.delays, preVehicleState.delays.stream().reduce(0, Integer::sum), preVehicleState.totalDelay, preVehicleState.visit);
////        postVehicleState.totalDelay = postVehicleState.delays.stream().reduce(0, Integer::sum);
////
////        int reward = preVehicleState.loads.subList(0, indexInsertionArrival).stream().reduce(0, Integer::sum);
//        return postVehicleState;
//    }
//
//    private static void updateStopNode(VehicleState preVehicleState, VehicleState postVehicleState, int indexInsertionArrival) {
//        // Vehicle is stopped at last visited location or continues stopped
//        Node previousNode = preVehicleState.nodes.get(indexInsertionArrival - 1);
//        int nextTimestep = postVehicleState.postDecisionTime;
//        postVehicleState.nodeArrivals.add(nextTimestep);
//        postVehicleState.delays.add(0);
//        postVehicleState.normalArrivalDelays.add(0.0);
//        postVehicleState.remaining.add(0);
//        postVehicleState.loads.add(0);
//        postVehicleState.networkIds.add(preVehicleState.networkIds.get(indexInsertionArrival - 1));
//        postVehicleState.nodeLabels.add("ST(" + preVehicleState.nodeLabels.get(indexInsertionArrival - 1) + ")");
//        NodeStop stop = new NodeStop(previousNode, preVehicleState.getVehicle().getId(), nextTimestep);
//        postVehicleState.nodes.add(stop);
//        postVehicleState.normalOccupancyRates.add(0.0);
//    }
//
//    private static void updateLastVisitedNode(VehicleState preVehicleState, VehicleState postVehicleState, int indexInsertionArrival) {
//
//        // Vehicle is stopped at last visited location or continues stopped
//        Node previousNode = preVehicleState.nodes.get(indexInsertionArrival - 1);
//
//        // Find waypoint between last visited node and next destination node
//        int vehicleOriginNetworkId = preVehicleState.networkIds.get(indexInsertionArrival - 1);
//        int vehicleOriginDeparture = preVehicleState.nodeArrivals.get(indexInsertionArrival - 1);
//        int vehicleDestinationNetworkId = preVehicleState.networkIds.get(indexInsertionArrival);
//        int waypointNetworkId = Dao.getInstance().getIntermediateNodeNetworkId(
//                vehicleOriginNetworkId,
//                vehicleDestinationNetworkId,
//                postVehicleState.postDecisionTime - vehicleOriginDeparture);
//
//        // Check if a middle point was reached
//        if (waypointNetworkId != vehicleOriginNetworkId && waypointNetworkId != vehicleDestinationNetworkId) {
//            int distOriginWaypoint = Dao.getInstance().getDistSec(vehicleOriginNetworkId, waypointNetworkId);
//            int arrivalAtWaypoint = vehicleOriginDeparture + distOriginWaypoint;
//            Node nextNode = preVehicleState.nodes.get(indexInsertionArrival);
//
//            NodeMiddle earliestMiddleNode = new NodeMiddle(
//                    waypointNetworkId,
//                    arrivalAtWaypoint,
//                    previousNode,
//                    nextNode,
//                    distOriginWaypoint);
//
//            postVehicleState.nodeArrivals.add(arrivalAtWaypoint);
//            postVehicleState.delays.add(0);
//            postVehicleState.normalArrivalDelays.add(0.0);
//            postVehicleState.remaining.add(0);
//            postVehicleState.loads.add(0);
//            postVehicleState.networkIds.add(waypointNetworkId);
//            postVehicleState.nodeLabels.add(getMiddleNodeLabel(preVehicleState, indexInsertionArrival));
//            postVehicleState.nodes.add(earliestMiddleNode);
//            Double previousOccupancy = preVehicleState.normalOccupancyRates.get(indexInsertionArrival - 1);
//            postVehicleState.normalOccupancyRates.add(previousOccupancy);
//        }
//    }
//
//    private static void processVisitedNodesUntilIndex(VehicleState preVehicleState, VehicleState postVehicleState, int indexInsertionArrival) {
//        for (int idxNode = 0; idxNode < indexInsertionArrival; idxNode++) {
//
//            Node servicedNode = preVehicleState.nodes.get(idxNode);
//
//            if (servicedNode instanceof NodePK) {
//                User user = User.mapOfUsers.get(servicedNode.getTripId());
//                postVehicleState.passengers.add(user);
//                postVehicleState.requests.remove(user);
//            }
//
//            if (servicedNode instanceof NodeDP) {
//                User user = User.mapOfUsers.get(servicedNode.getTripId());
//                postVehicleState.requests.remove(user);
//                postVehicleState.passengers.remove(user);
//
//                // Total delay decreases once a user is delivered
//                postVehicleState.totalDelay-= preVehicleState.delays.get(idxNode);
//            }
//        }
//    }
//
//    private static void keepNodesToVisitFromIndex(VehicleState preVehicleState, VehicleState postVehicleState, int indexInsertionArrival) {
//        for (int pos = indexInsertionArrival; pos < preVehicleState.nodeArrivals.size(); pos++) {
//            String nodeLabel = preVehicleState.nodeLabels.get(pos);
//            postVehicleState.nodeArrivals.add(preVehicleState.nodeArrivals.get(pos));
//            postVehicleState.delays.add(preVehicleState.delays.get(pos));
//            postVehicleState.normalArrivalDelays.add(preVehicleState.normalArrivalDelays.get(pos));
//            postVehicleState.remaining.add(preVehicleState.remaining.get(pos));
//            postVehicleState.loads.add(preVehicleState.loads.get(pos));
//            postVehicleState.networkIds.add(preVehicleState.networkIds.get(pos));
//            postVehicleState.nodeLabels.add(nodeLabel);
//            postVehicleState.nodes.add(preVehicleState.nodes.get(pos));
//            postVehicleState.normalOccupancyRates.add(preVehicleState.normalOccupancyRates.get(pos));
//        }
//    }
//
//
    public static StateAction getVisitState(Vehicle v, int preDecisionTime, int elapsed, int totalTimeHorizon) {
        if (v.getVisit() != null) {
            return StateAction.getVisitState(v.getVisit(), preDecisionTime, elapsed, totalTimeHorizon);
        } else {
            return StateAction.getVisitState(new VisitStop(v), preDecisionTime, elapsed, totalTimeHorizon);
        }
    }

    public static StateAction getVisitState(VisitObj visit, int preDecisionTime, int elapsed, int totalTimeHorizon) {

        StateAction vs = new StateAction(preDecisionTime, elapsed, totalTimeHorizon);
        vs.visit = visit;
        vs.requests = new HashSet<>(visit.getRequests());
        vs.passengers = new HashSet<>(visit.getPassengers());
        vs.vehicle = visit.getVehicle();
        vs.requestCount = visit.getRequests().size();
        vs.totalDelay = visit.getDelay();
        vs.totalDelayBonus = visit.getDelayBonus();
        assert vs.totalDelayBonus != null: visit;

//        # Normalising Inputs
//        current_time_input = (current_time - self.envt.START_EPOCH) / (self.envt.STOP_EPOCH - self.envt.START_EPOCH)
//        num_requests_input = num_requests / self.envt.NUM_AGENTS
//        num_other_agents_input = num_other_agents / self.envt.NUM_AGENTS

        vs.processLastVisitedNode();


        if (visit != null) {


            if (visit instanceof VisitRelocation || visit instanceof VisitDisplaceAndStop || visit instanceof VisitStop) {
                vs.processNextDestination();
            } else {

                Node originNode = visit.getVehicle().getLastVisitedNode();
                int arrivalTime = originNode.getEarliestDeparture();
                int currentLoad = visit.getVehicle().getCurrentLoad();

                int maxDelay = originNode.getMaxDelay();


                for (Node destinationNode : visit.getSequenceVisits()) {

                    int distanceLeg = Dao.getInstance().getDistSec(originNode, destinationNode);
                    //System.out.printf("%s -> %s -> %s - Arrival: %s", originNode, distanceLeg, destinationNode, arrivalTime);
                    if (distanceLeg == 0) {
                        int indexLastNode = vs.nodeArrivals.size() - 1;
                        currentLoad += destinationNode.getLoad();

                        int delay = getDelay(arrivalTime, destinationNode);
                        int delayBonus = getDelayBonus(arrivalTime, destinationNode);
                        vs.delays.set(indexLastNode, vs.delays.get(indexLastNode) + delay);
                        vs.delayBonuses.set(indexLastNode, vs.delayBonuses.get(indexLastNode) + delayBonus);
                        vs.loads.set(indexLastNode, currentLoad);
                        vs.normalOccupancyRates.set(indexLastNode, vs.getOccupancyRate(currentLoad));
                        // Assume highest delay to reach node
                        double normalDelay = vs.getNormalDelay(destinationNode, arrivalTime);
                        double highestDelay = Math.max(vs.normalArrivalDelays.get(indexLastNode), normalDelay);
                        vs.normalArrivalDelays.set(indexLastNode, highestDelay);

                        originNode = destinationNode;
                    } else {


                        arrivalTime = arrivalTime + distanceLeg;
                        //System.out.println(visit.getVehicle().getLastVisitedNode() +"="+ arrivalTime);
                        currentLoad += destinationNode.getLoad();

                        vs.networkIds.add(destinationNode.getNetworkId());
                        vs.nodeLabels.add(destinationNode.toString());
                        vs.nodes.add(destinationNode);

                        vs.loads.add(destinationNode.getLoad());
                        vs.normalOccupancyRates.add(vs.getOccupancyRate(currentLoad));

                        vs.nodeArrivals.add(arrivalTime);
                        vs.delays.add(getDelay(arrivalTime, destinationNode));
                        vs.delayBonuses.add(getDelayBonus(arrivalTime, destinationNode));

                        int remainingToRebalancingTarget = arrivalTime - vs.timeStep;
                        vs.remaining.add(remainingToRebalancingTarget);

                        vs.normalArrivalDelays.add(vs.getNormalDelay(destinationNode, arrivalTime));

                        originNode = destinationNode;
                    }
                }
            }
        }

//        if(visit.getSequenceVisits()!= null) {
//            Leg a = Visit.getDraftVisit(visit.getSequenceVisits().toArray(new Node[visit.getVisitSequenceSize()]));
//            System.out.println(a);
//            System.out.println(vs);
//            System.out.println(visit);
//            assert a.delay == vs.totalDelay: String.format(
//                    "%s - %s - %s - Visit: %s",
//                    Arrays.toString(visit.getSequenceVisits().toArray(new Node[visit.getVisitSequenceSize()])),
//                    a.delay,
//                    vs.totalDelay,
//                    visit.getDelay());
//
//        }


        return vs;
    }

    private static int getDelay(int arrivalTime, Node node) {
        return isPickupOrDeliveryNode(node) ? arrivalTime - node.getEarliest() : 0;
    }

    private static int getDelayBonus(int arrivalTime, Node node) {
        return isPickupOrDeliveryNode(node) ? node.getLatest() - arrivalTime : 0;
    }

    private static boolean isPickupOrDeliveryNode(Node n) {
        return n instanceof NodeDP || n instanceof NodePK;
    }

    public Vehicle getVehicle() {
        return this.vehicle;
    }

    private void processDestinationSequence() {

        int departureTime = this.visit.getVehicle().getEarliestDeparture();
        assert java.util.Objects.equals(this.visit.getVehicle().getEarliestDeparture(), this.visit.getDeparture());
        Node originNode = this.visit.getVehicle().getLastVisitedNode();
        int currentLoad = this.visit.getVehicle().getCurrentLoad();

        for (Node destinationNode : this.visit.getSequenceVisits()) {

            int distanceLeg = Dao.getInstance().getDistSec(originNode, destinationNode);

            networkIds.add(destinationNode.getNetworkId());
            nodeLabels.add(destinationNode.toString());
            nodes.add(destinationNode);

            loads.add(destinationNode.getLoad());
            currentLoad += destinationNode.getLoad();
            normalOccupancyRates.add(getOccupancyRate(currentLoad));

            int arrivalTime = departureTime + distanceLeg;
            nodeArrivals.add(arrivalTime);
            delays.add(getDelay(arrivalTime, destinationNode));
            delayBonuses.add(getDelayBonus(arrivalTime, destinationNode));

            int remainingToRebalancingTarget = arrivalTime - timeStep;
            remaining.add(remainingToRebalancingTarget);

            double normalDelay = getNormalDelay(destinationNode, arrivalTime);
            normalArrivalDelays.add(normalDelay);

            departureTime = departureTime + distanceLeg;
            originNode = destinationNode;
        }
    }

    private double getOccupancyRate(int load) {
        return Double.valueOf(load) / this.visit.getVehicle().getCapacity();
    }

    private void processNextDestination() {

        if (isVehicleNotParking()) {
            Node targetNode = this.visit.getTargetNode();
            networkIds.add(targetNode.getNetworkId());
            nodeArrivals.add(targetNode.getArrivalSoFar());
            nodeLabels.add(targetNode.toString());
            nodes.add(targetNode);
            loads.add(0);
            int remainingToRebalancingTarget = targetNode.getArrivalSoFar() - timeStep;
            remaining.add(remainingToRebalancingTarget);
            delays.add(0);
            delayBonuses.add(0);
            normalArrivalDelays.add(0.0);
            normalOccupancyRates.add(0.0);
        }
    }

    private boolean isVehicleNotParking() {
        return this.networkIds.get(0) != visit.getTargetNode().getNetworkId();
    }

    private void processLastVisitedNode() {
        Vehicle vehicle = this.visit.getVehicle();
        this.vehicleCapacity = vehicle.getCapacity();
        this.networkIds.add(vehicle.getLastVisitedNode().getNetworkId());
        this.nodeArrivals.add(vehicle.getEarliestDeparture());
        this.nodeLabels.add(vehicle.toString());
        this.nodes.add(vehicle.getLastVisitedNode());
        this.loads.add(0);
        this.remaining.add(0);
        this.delays.add(0);
        this.delayBonuses.add(0);
        this.normalArrivalDelays.add(0.0);
        this.normalOccupancyRates.add(getOccupancyRate(vehicle.getCurrentLoad()));
    }

    private double getNormalDelay(Node destination, int arrival) {
        double normalDelay = 0;
        if (isPickupOrDeliveryNode(destination))
            normalDelay = (double) (arrival - destination.getEarliest()) / destination.getMaxDelay();
        return normalDelay;
    }


    @Override
    public String toString() {
        return "VisitSnapShot{" +
                "\n        length=" + nodeArrivals.size() +
                ",\n  pre-decision=" + timeStep +
                ",\n   vehicleSize=" + vehicleCapacity +
                ",\n      arrivals=" + nodeArrivals +
                ",\n           ids=" + networkIds +
                ",\n      node Ids=" + nodeLabels +
                ",\n         nodes=" + nodes +
                ",\n         loads=" + loads +
                ",\n      capacity=" + normalOccupancyRates +
                ",\n     remaining=" + remaining +
                ",\n        delays=" + delays +
                ",\n  normalDelays=" + normalArrivalDelays.stream().map(aDouble -> String.format("%.2f", aDouble)).collect(Collectors.toList()) +
                ",\n request count=" + visit.getRequests().size() +
                ",\n   request sum=" + visit.getRequestsTotalLoad() +
                ",\n passenger sum=" + visit.getPassengersTotalLoad() +
                ",\n   total delay=" + visit.getDelay() +
                (this.vehicleCountAtOrigin != null ? ",\n count of surrounding at " + this.getNextNode() + " = " + this.vehicleCountAtOrigin : "") +
                (this.shareOfSurroundingVehicles != null ? ",\n share of surrounding at " + this.getNextNode() + " = " + this.shareOfSurroundingVehicles : "") +
                '}';
    }

    public Node getNextNode() {
        return this.nodes.get(0);
    }

    public void setVehicleCount(int nOfSurroundingVehicles) {
        this.vehicleCountAtOrigin = nOfSurroundingVehicles;
    }

    public void setVehicleCountNormal(double shareOfSurroundingVehicles) {
        this.shareOfSurroundingVehicles = shareOfSurroundingVehicles;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StateAction that = (StateAction) o;
        return timeStep == that.timeStep && timeHorizon == that.timeHorizon && Objects.equal(networkIds, that.networkIds) && Objects.equal(nodeArrivals, that.nodeArrivals) && Objects.equal(vehicleCountAtOrigin, that.vehicleCountAtOrigin) && Objects.equal(vehicleCapacity, that.vehicleCapacity) && Objects.equal(postDecisionTimeStep, that.postDecisionTimeStep) && Objects.equal(loads, that.loads) && Objects.equal(delays, that.delays) && Objects.equal(shareOfSurroundingVehicles, that.shareOfSurroundingVehicles);
    }

    @Override
    public int hashCode() {
        return this.visit.hashCode();
        //return Objects.hashCode(networkIds, nodeArrivals, vehicleCountAtOrigin, vehicleCapacity, postDecisionTime, loads, delays, shareOfSurroundingVehicles, preDecisionTime, totalTimeHorizon);
    }

    @Override
    public int compareTo(StateAction o) {
        return this.getVehicle().getId();
    }

    @Override
    public void setVehicleState(StateAction stateAction) {

    }

    @Override
    public int getVisitSequenceSize() {
        return 0;
    }

    @Override
    public int compareTo(VisitObj v) {
        return 0;
    }

    @Override
    public boolean isValid() {
        return false;
    }

    @Override
    public void genUserPickupDelayMap() {

    }

    @Override
    public Map<User, Integer> getUserPickupDelayMap() {
        return null;
    }

    @Override
    public void discountDelay(int delayServicedUser) {

    }

    @Override
    public String getUserInfo() {
        return null;
    }

    @Override
    public int getRequestsTotalLoad() {
        return this.requestCount;
    }

    @Override
    public int getPassengersTotalLoad() {
        return 0;
    }

    @Override
    public double getAvgLoadPerVisitLeg() {
        return 0;
    }

    @Override
    public int getArrival() {
        return 0;
    }

    public Integer getDelay() {
        return this.totalDelay;
    }

    @Override
    public Integer getIdle() {
        return null;
    }

    public Set<User> getRequests() {
        return requests;
    }

    @Override
    public Set<User> getPassengers() {
        return passengers;
    }

    @Override
    public Set<User> getUsers() {
        return null;
    }

    @Override
    public LinkedList<Node> getSequenceVisits() {
        return null;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    @Override
    public Integer getDeparture() {
        return this.departure;
    }

    @Override
    public void updateArrivalSoFarAtVisitNodes() {

    }

    @Override
    public int getArrivalTimeAtNext() {
        return 0;
    }

    @Override
    public Node getLastVisitedNode() {
        return null;
    }

    @Override
    public Node getTargetNode() {
        return null;
    }

    @Override
    public boolean isSetup() {
        return false;
    }

    @Override
    public Integer getDelayBonus() {
        return this.totalDelayBonus;
    }

    @Override
    public void setVF(double vf) {
        this.vf = vf;

    }

    @Override
    public double getVF() {
        return this.vf;
    }

    @Override
    public void setDeparture(int departure) {
        this.departure = departure;
    }

    public int getCapacity() {
        return this.vehicleCapacity;
    }

    public static boolean vehicleCanReach(StateAction v1VisitPostState, Vehicle v2PreDecision) {
        // Next node post decision
        Node v1PostNextNode = v1VisitPostState.getNextNode();

        // Next node a vehicle will visit (pre-decision)
        // The assumption is that since the step is small, this holds
        Node v2PreNextNode = v2PreDecision.getTargetNode();
        int delayV1_V2 = Dao.getInstance().getDistSec(v1PostNextNode, v2PreNextNode);
        int delayV2_V1 = Dao.getInstance().getDistSec(v2PreNextNode, v1PostNextNode);
        return delayV1_V2 <= MAX_PICKUP_DELAY || delayV2_V1 <= MAX_PICKUP_DELAY;
    }
}
