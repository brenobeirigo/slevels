package simulation;

import config.Config;
import dao.Dao;
import model.Leg;
import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;
import org.paukov.combinatorics.CombinatoricsVector;
import org.paukov.combinatorics.Generator;
import org.paukov.combinatorics.ICombinatoricsVector;
import util.pdcombinatorics.PDGeneratorSingleInsertion;
import util.pdcombinatorics.PDPermutations;

import java.util.*;

import static org.paukov.combinatorics.CombinatoricsFactory.createPermutationGenerator;

public class Method {

    public static int getEarliestDp(int earliest, int from, int to, String qos) {
        return earliest + Dao.getInstance().getDistSec(from, to);
    }

    public static int getLatestDp(int earliest, int from, int to, String qos) {
        return earliest + Dao.getInstance().getDistSec(from, to) + Config.getInstance().qosDic.get(qos).dpDelay;
    }

    public static int getLatestPK(int earliest, String qos) {
        return earliest + Config.getInstance().qosDic.get(qos).pkDelay;
    }

    /**
     * Returns a PK or DP node depending on the position of a user id in the sequence
     * E.g.:
     * [1,1,2,3,2,3] ===> [PK1, DP2, PK2, PK3, DP2, DP3]
     *
     * @param userId     Id of the user
     * @param visitedIds Set of ids previously visited
     * @return A PK or DP node (if PK was already visited)
     */
    public static Node getNodeFromTripId(int userId, Set<Integer> visitedIds) {

        User u = User.mapOfUsers.get(userId);

        // Pickup node was visited?
        if (User.status[userId][0] > 0)
            return u.getNodeDp();

        // Is it the first time a user appears in the sequence?
        if (!visitedIds.contains(userId)) {
            visitedIds.add(userId);
            // First time id appears return PK
            return u.getNodePk();
        } else
            // Second time id appears return DP
            return u.getNodeDp();
    }


    public static boolean feasibleSequence(Node[] sequenceNodes) {


        // Model.Vehicle node data
        Node fromNode = sequenceNodes[0];
        int arrivalFrom = fromNode.getEarliest();
        Node toNode;

        // Loop elements in tuple
        for (int i = 1; i < sequenceNodes.length; i++) {

            // Add load
            toNode = sequenceNodes[i];

            /////////////////////////* VIABLE NEXT */////////////////////////////////////
            int distFromTo = Dao.getInstance()
                    .getDistSec(
                            fromNode.getNetworkId(),
                            toNode.getNetworkId());

            // No path available
            if (distFromTo < 0)
                return false;

            // Time vehicle arrives at next node (can be earlier or later)
            int arrivalTo = arrivalFrom + distFromTo;

            // Arrival cannot be later than latest time in node
            if (arrivalTo > toNode.getLatest())
                return false;

            // Update arrival time at next node to >= earliest time of next node
            arrivalTo = Math.max(arrivalTo, toNode.getEarliest());

            // Update
            fromNode = toNode;
            arrivalFrom = arrivalTo;
        }

        // Return visit
        return true;
    }

    /**
     * Verify if sequence is valid. If so, returns delay. Otherwise returns the highest integer (MAX_VALUE)
     *
     * @param sequenceNodes
     * @return Total delay of sequence of nodes (Integer.MAX_VALUE if sequence is invalid)
     */
    public static int getDelayFrom(Node[] sequenceNodes) {


        // Model.Vehicle node data
        Node fromNode = sequenceNodes[0];
        int arrivalFrom = fromNode.getEarliest();
        int delay = 0;
        Node toNode;

        // Loop elements in tuple
        for (int i = 1; i < sequenceNodes.length; i++) {

            // Add load
            toNode = sequenceNodes[i];

            /////////////////////////* VIABLE NEXT */////////////////////////////////////
            int distFromTo = Dao.getInstance()
                    .getDistSec(
                            fromNode.getNetworkId(),
                            toNode.getNetworkId());

            // No path available
            if (distFromTo < 0)
                return Integer.MAX_VALUE;

            // Time vehicle arrives at next node (can be earlier or later)
            int arrivalTo = arrivalFrom + distFromTo;

            // Arrival cannot be later than latest time in node
            if (arrivalTo > toNode.getLatest())
                return Integer.MAX_VALUE;

            // Update arrival time at next node to >= earliest time of next node
            arrivalTo = Math.max(arrivalTo, toNode.getEarliest());

            // Update delay
            delay += arrivalTo - toNode.getEarliest();

            // Update
            fromNode = toNode;
            arrivalFrom = arrivalTo;
        }

        // Return visit
        return delay;
    }

    /* TODO: Use hash memo_dic hits */
    public static int[] feasibleTrip(Node o, Node d, int arrivalO) {

        // Arrival and delay (if negative, it means idleness)
        int[] delays = {-1, -1};

        // Get travel time between origin and destination
        int ttOD = Dao.getInstance().getDistSec(o.getNetworkId(), d.getNetworkId());

        // No path between nodes
        if (ttOD < 0)
            return delays;

        // if arrival at origin node "o" is not provided, arrival is equal to earliest arrival at "o"
        if (arrivalO < 0)
            arrivalO = o.getEarliest();

        // Time vehicle arrives at next node
        int arrivalD = arrivalO + ttOD;

        // Arrival cannot be later than latest time in destination node "d"
        if (arrivalD > d.getLatest())
            return delays;

        // Arrival at destination must be >= earliest arrival (it can arrive before and wait)
        int noWaitArrivalD = Math.max(arrivalD, d.getEarliest());

        // TODO In this sequence, one of the customers was already scheduled earlier.
        // The new journey must decrease waiting time of all passengers.
        //if( previous_arrival >= 0 && next_node in previous_arrival and previous_arrival[next_node]<arrival_next){
        //    Model.Node.Model.Node.memo_dic[id_viable] = (None,None)
        //    return delays;

        // Delay in seconds
        int delay = arrivalD - d.getEarliest();
        delays[0] = noWaitArrivalD;
        delays[1] = delay;

        return delays;
    }

    /**
     * Create a sequence of trip ids to be permuted.
     *
     * @param passengers Array of users
     * @param v          Model.Vehicle (with nodes to visit)
     * @return List of trip ids
     */
    private static List<Integer> getPkDpUserIdSequence(Set<User> passengers, Vehicle v) {

        // List of request indices
        List<Integer> pkDpUserIdSequence = Visit.getIdPairListFromUsers(passengers);

        //If vehicle has already a sequence
        if (v.getVisit() != null
                && v.getVisit().getSequenceVisits() != null
                && !v.getVisit().getSequenceVisits().isEmpty()) {

            //TODO: Ignore first node when adding vehicle sequence?
            //First node is not included, vehicle is on its way to it.
            for (Node n : v.getVisit().getSequenceVisits()) {
                pkDpUserIdSequence.add(0, n.getTripId());
            }
            //TODO: Return arrival sequence also? The way it is now, customers arrival can change within TW
            //Get previous arrival times of nodes in vehicles
            //A new sequence is only valid if better arrival times are established
            //TODO: Model.Node arrival dictionary for processing in parallel
            //List<Integer> arrival_dic = new ArrayList<>();
            //{n:v.visits.seq_arrival[i] for i, n in enumerate (v.visits.sequence)};
        }

        return pkDpUserIdSequence;//,arrival_dic;
    }


    public static Vehicle getBestVehicleToServiceTarget(List<Vehicle> listIdle, Node hotPoint) {

        // Closest vehicle to candidate hot point (a previous miss)
        Vehicle idlestClosestVehicle = listIdle.get(0);

        // If hotpoint is urgent, get closest
        if (hotPoint.getUrgent() > 0) {

            //System.out.println("Addressing urgent...");

            for (int i = 1; i < listIdle.size(); i++) {

                Vehicle candidateRebalancingVehicle = listIdle.get(i);

                // Untie using shortest distance
                int distanceCandidate = Dao.getInstance().getDistSec(hotPoint, candidateRebalancingVehicle.getLastVisitedNode());
                int distanceIncumbent = Dao.getInstance().getDistSec(hotPoint, idlestClosestVehicle.getLastVisitedNode());

                // Untie with distance
                if (distanceCandidate < distanceIncumbent && distanceCandidate > 0) {
                    idlestClosestVehicle = candidateRebalancingVehicle;
                }
            }
        } else {
            for (int i = 1; i < listIdle.size(); i++) {

                Vehicle candidateRebalancingVehicle = listIdle.get(i);

                // Get the idlest and closest vehicle to hot point
                if (candidateRebalancingVehicle.getRoundsIdle() >= idlestClosestVehicle.getRoundsIdle()) {

                    if (candidateRebalancingVehicle.getRoundsIdle() == idlestClosestVehicle.getRoundsIdle()) {

                        // Untie using shortest distance
                        int distanceCandidate = Dao.getInstance().getDistSec(hotPoint, candidateRebalancingVehicle.getLastVisitedNode());
                        int distanceIncumbent = Dao.getInstance().getDistSec(hotPoint, idlestClosestVehicle.getLastVisitedNode());

                        // Untie with distance
                        if (distanceCandidate < distanceIncumbent && distanceCandidate > 0) {
                            idlestClosestVehicle = candidateRebalancingVehicle;
                        }
                    } else {
                        idlestClosestVehicle = candidateRebalancingVehicle;
                    }
                }
            }
        }

        return idlestClosestVehicle;

    }

    /**
     * Add last visited node to the start of the sequence and subsequent breakpoint IF:
     * - Vehicle is rebalancing, e.g., ST-->RE = ST-->BP-->SEQ
     * - Vehicle is moving to pickup different node, e.g., ST-->PK1 = ST-->BK-->SEQ (with SEQ[0] != PK1)
     *
     * @param sequence Candidate sequence of pickup and delivery nodes
     * @param vehicle Candidate vehicle to carry out sequence
     * @return New sequence with nodes from previous vehicle plan (stop and possibly breakpoint)
     */
    public static LinkedList<Node> addLastVisitedAndMiddleNodesToStart(List<Node> sequence, Vehicle vehicle) {

        // Sequence has to start from last vehicle visited node
        LinkedList<Node> sequenceWithVehicleNode = new LinkedList<>(sequence);

        Node middle = vehicle.getMiddleNode();

        /*if (vehicle.isServicing())
            System.out.println(String.format(
                    ">>>>>> Servicing: %s, target: %s, sequence: %s, middle: %s",
                    vehicle.isServicing(),
                    vehicle.getVisit().getTargetNode(),
                    sequenceWithVehicleNode,
                    middle));*/

        // When vehicle is rebalancing, middle node has to be included
        if (vehicle.isRebalancing()) {

            // If middle node does not exist, discard sequence
            // TODO add the rebalancing target?
            if (middle != null) {
                sequenceWithVehicleNode.add(0, middle);
            } else {
                return null;
            }

            // When next node in visit sequence is not the node in potential visits, vehicle has to break path
        } else if (vehicle.isServicing() && vehicle.getVisit().getTargetNode() != sequenceWithVehicleNode.getFirst()) {

            // TODO If next in sequence is middle, is it worth it breaking again???
            //assert !(vehicle.getVisit().getTargetNode() instanceof NodeMiddle) : String.format("First is middle (current middle = %s) - Visit: %s", middle, vehicle.getVisit());

            // Assumption: Vehicle can take a turn at the location of the next node
            // Why? Because ANOTHER vehicle can pick-up that user
            // O -------------- 1'------ 2  ------ 2'
            // 0 -------------- M -------3' ------ 4 (Vehicle can take a turn at location of 1')
            // It may be frustrating for user 1 to be its drop-off delayed (the car would pass by 1's location),
            // but it might be worthwhile for the system
            if (middle != null) {
                sequenceWithVehicleNode.add(0, middle);
            } else {
                // Cannot break, vehicle will be available in the next iteration
                return null;
            }
        }

        sequenceWithVehicleNode.add(0, vehicle.getLastVisitedNode());


        return sequenceWithVehicleNode;
    }

    /**
     * Generator of all combinations of stops from the request list and vehicle stops (passenger delivery nodes).
     * Notice that:
     * - Some sequence may be invalid (e.g., {dp1, pk1}
     * - Sequences do not include breakpoints (use addLastVisitedAndMiddleNodesToStart to get feasible)
     *
     * @param requests List of requests to build a visit (add pickup and drop-off nodes)
     * @param vehicle Vehicle whose visit will be the basis to generate visit sequences (consider passenger nodes)
     * @return Generator of sequences containing passenger and request nodes
     */
    public static Generator<Node> getGeneratorOfNodeSequence(Set<User> requests, Vehicle vehicle) {

        // TODO here we assume the requests include vehicle requests to
        ICombinatoricsVector<Node> vector = new CombinatoricsVector<>();
        for (User user : requests) {
            vector.addValue(user.getNodePk());
            vector.addValue(user.getNodeDp());
        }

        // Passengers have been picked up
        if (vehicle.isServicing()) {
            for (User passenger : vehicle.getVisit().getPassengers()) {
                vector.addValue(passenger.getNodeDp());
            }
        }

        return createPermutationGenerator(vector);
    }


    /**
     * Find the best itinerary (over all possible PU/DO permutations) for a vehicle to service all requests.
     * TODO: If n. of requests > 4, many combinations are possible.
     *
     * @param vehicle Vehicle carrying out the visit
     * @param requests Candidate requests to build a visiting sequence
     * @return Best visiting sequence or null if visit does not exist
     */
    public static Visit getBestVisitFromPDPermutations(Vehicle vehicle, Set<User> requests) {

        // A single request can be inserted in a vehicle in multiple ways. Only the best (i.e., the lowest delay)
        // visit is inserted in the RTV graph.

        Visit visit = null;
        int lowestDelay = Integer.MAX_VALUE;
        LinkedList<Node> lowestDelaySequence = null;

        // Create request set out of one request
        PDPermutations perms = new PDPermutations(requests, vehicle);

        while (perms.hasNext()) {

            List<Node> sequencePickupsAndDeliveries = List.of(perms.next());

            LinkedList<Node> sequenceFromVehiclePositionToLastDelivery = Method.addLastVisitedAndMiddleNodesToStart(sequencePickupsAndDeliveries, vehicle);

            if (sequenceFromVehiclePositionToLastDelivery == null)
                continue;

            int delay = Visit.isValidSequenceFeasible(
                    sequenceFromVehiclePositionToLastDelivery,
                    vehicle.getDepartureCurrent(),
                    vehicle.getCurrentLoad(),
                    vehicle.getCapacity(),
                    vehicle.getContractDeadline());

//            System.out.printf(
//                    "delay = %4d - %s -> %s (network ids = %s)\n", delay,
//                    sequencePickupsAndDeliveries,
//                    sequenceFromVehiclePositionToLastDelivery,
//                    getNetworkIdsFromNodeSequence(sequenceFromVehiclePositionToLastDelivery));

            // Update if delay is valid
            if (delay >= 0) {
                if (delay < lowestDelay || (delay == lowestDelay && sequenceFromVehiclePositionToLastDelivery.size() < lowestDelaySequence.size())) {
                    lowestDelay = delay;
                    lowestDelaySequence = sequenceFromVehiclePositionToLastDelivery;
                }
            }
        }

        if (lowestDelaySequence != null) {

            // Remove vehicle's last visited node (i.e., sequence's first node)
            lowestDelaySequence.poll();

            // Setup new visit
            visit = new Visit(lowestDelaySequence, lowestDelay);

            // Finish visit configuration
            visit.setVehicle(vehicle);
            visit.getRequests().addAll(requests);

            // printMapOfNodesPerNetworkIdSortedByEarliestTime(vehicle, visit);

            // All passengers in vehicle belong to visit (they need to be drop off)
            if (vehicle.isServicing()) {
                visit.setPassengers(new HashSet<>(vehicle.getVisit().getPassengers()));
            }
        }

        return visit;
    }

    public static Visit getBestVisitFromAllNodePermutations(Vehicle vehicle, Set<User> requests) {

        Generator<Node> gen = getGeneratorOfNodeSequence(requests, vehicle);
        // System.out.println("Setting up sequence " + vehicle.getVisit());

        // A single request can be inserted in a vehicle in multiple ways. Only the best (i.e., the lowest delay)
        // visit is inserted in the RTV graph.

        List<List<Node>> sequences = new LinkedList<>();
        //Map<String, Visit> bestVisitOfEachClass = new HashMap<>();

        Visit visit = null;
        int lowestDelay = Integer.MAX_VALUE;
        LinkedList<Node> lowestDelaySequence = null;
        for (ICombinatoricsVector<Node> combination : gen) {

            List<Node> sequence = combination.getVector();


            LinkedList<Node> sequenceFromVehiclePositionToLastDelivery = addLastVisitedAndMiddleNodesToStart(sequence, vehicle);
            sequences.add(sequenceFromVehiclePositionToLastDelivery);

            // System.out.println("Seq. from last: " + sequenceFromVehiclePositionToLastDelivery);

            if (sequenceFromVehiclePositionToLastDelivery == null)
                continue;

            int delay = Visit.isValidSequence(
                    sequenceFromVehiclePositionToLastDelivery,
                    vehicle.getDepartureCurrent(),
                    vehicle.getCurrentLoad(),
                    vehicle.getCapacity(),
                    vehicle.getContractDeadline()
            );

            // Update if delay is valid
            if (delay >= 0) {
                if (delay < lowestDelay || (delay == lowestDelay && sequenceFromVehiclePositionToLastDelivery.size() < lowestDelaySequence.size())) {
                    lowestDelay = delay;
                    lowestDelaySequence = sequenceFromVehiclePositionToLastDelivery;
                }
            }
        }

        if (lowestDelaySequence != null) {

            // Remove vehicle's last visited node (i.e., sequence's first node)
            lowestDelaySequence.poll();

            // Setup new visit
            visit = new Visit(lowestDelaySequence, lowestDelay);

            // Finish visit configuration
            visit.setVehicle(vehicle);
            visit.getRequests().addAll(requests);

            // All passengers in vehicle belong to visit (they need to be drop off)
            if (vehicle.isServicing()) {
                visit.setPassengers(new HashSet<>(vehicle.getVisit().getPassengers()));
            }
        }

        return visit;
    }

    public static Visit getBestVisitFromPDPermutationsSummarized(Vehicle vehicle, Set<User> requests) {
        // A single request can be inserted in a vehicle in multiple ways. Only the best (i.e., the lowest delay)
        // visit is inserted in the RTV graph.

        Visit visit = null;
        Node[] lowestDelaySequence = null;
        Leg bestDraftVisit = null;

        // Create request set out of one request
        PDPermutations perms = new PDPermutations(requests, vehicle);

        while (perms.hasNext()) {

            Node[] PDPermutation = perms.next();
            // getMapNetworkIdNodes(sequencePickupsAndDeliveries);

            Leg draftVisit = Visit.getDraftVisit(vehicle, PDPermutation);

            // Update if delay is valid
            if (draftVisit != null) {
                if (draftVisit.compareTo(bestDraftVisit) < 0) {
                    bestDraftVisit = draftVisit;
                    lowestDelaySequence = PDPermutation;
                }
            }
        }

        if (lowestDelaySequence != null) {

            // Setup new visit
            visit = new Visit(lowestDelaySequence, bestDraftVisit.delay, bestDraftVisit.delayBonus, bestDraftVisit.idleness, vehicle, requests);

        }

        return visit;
    }

    public static Visit getBestVisitFromInsertion(Vehicle vehicle, User request) {
        // A single request can be inserted in a vehicle in multiple ways. Only the best (i.e., the lowest delay)
        // visit is inserted in the RTV graph.

        //System.out.printf("User %s, vehicle=%s, visit=%s\n", request, vehicle, vehicle.getVisit());

        // Do not try inserting request in the same vehicle again
        if (vehicle.hasAlreadyBeenAssignedToUser(request)) {
            //System.out.printf("User %s already in %s. Visit=%s.\n", request, vehicle, vehicle.getVisit());
            return vehicle.getVisit();
        }

        Visit visit = null;
        Node[] lowestDelaySequence = null;
        Leg bestDraftVisit = null;

        PDGeneratorSingleInsertion perms = new PDGeneratorSingleInsertion(request, vehicle);

        while (perms.hasNext()) {

            Node[] PDPermutation = perms.next();
            //System.out.println(Arrays.asList(PDPermutation));
            //System.out.println(vehicle.getVisit());
            Leg draftVisit = Visit.getDraftVisit(vehicle, PDPermutation);

            // Update if delay is valid
            if (draftVisit != null) {
                if (draftVisit.compareTo(bestDraftVisit) < 0) {
                    bestDraftVisit = draftVisit;
                    lowestDelaySequence = PDPermutation;
                }
            }
        }

        if (lowestDelaySequence != null) {

            // Setup new visit
            Set<User> requests = new HashSet<>();
            requests.add(request);
            if (vehicle.isServicing()) {
                requests.addAll(vehicle.getVisit().getRequests());
            }

            visit = new Visit(lowestDelaySequence, bestDraftVisit.delay, bestDraftVisit.idleness, vehicle, requests);
        }

        return visit;
    }

    public static void reset() {
        return;
    }


    /**
     * Get nodes associated to each network id.
     * @param requests
     * @return
     */
    private static Map<Integer, TreeSet<Node>> getMapNetworkIdUserNodes(Set<User> requests, Set<User> passengers) {
        Map<Integer, TreeSet<Node>> networkIdNodes = new HashMap<>();

        for (User user : requests) {
            // Add pickup nodes
            networkIdNodes.putIfAbsent(user.getNodePk().getNetworkId(), new TreeSet<>(Comparator.comparingInt(Node::getEarliest)));
            networkIdNodes.get(user.getNodePk().getNetworkId()).add(user.getNodePk());
            networkIdNodes.putIfAbsent(user.getNodeDp().getNetworkId(), new TreeSet<>(Comparator.comparingInt(Node::getEarliest)));
            networkIdNodes.get(user.getNodeDp().getNetworkId()).add(user.getNodeDp());
        }

        for (User user : passengers) {
            // Add pickup nodes
            networkIdNodes.putIfAbsent(user.getNodeDp().getNetworkId(), new TreeSet<>(Comparator.comparingInt(Node::getEarliest)));
            networkIdNodes.get(user.getNodeDp().getNetworkId()).add(user.getNodeDp());
        }
        return networkIdNodes;
    }

    private static void printMapOfNodesPerNetworkIdSortedByEarliestTime(Vehicle vehicle, Visit visit) {
        System.out.printf("BEST(%s):(%s)\n", vehicle, visit.getSequenceVisits());
        System.out.printf("ARRIVALS: %s\n", Visit.getArrivalTimesFromVisit(vehicle, visit));
        //Map<Integer, TreeSet<Node>> networkIdNodes = getMapNetworkIdNodes(visit.getSequenceVisits());
        //sortNodesPerNetworkIdsByEarliestArrivalTime(networkIdNodes);
    }

    private static void sortNodesPerNetworkIdsByEarliestArrivalTime(Map<Integer, TreeSet<Node>> networkIdNodes) {
        for (Map.Entry<Integer, TreeSet<Node>> e : networkIdNodes.entrySet()) {
            StringBuilder b = new StringBuilder("%s = ".formatted(e.getKey()));
            for (Node node : networkIdNodes.get(e.getKey())) {
                b.append("\n%s(e=%7s, a=%7s, l=%7s)".formatted(
                        node,
                        node.getEarliest(),
                        node.getArrivalSoFar() == null ? "-" : node.getArrivalSoFar(),
                        node.getLatest() == Integer.MAX_VALUE ? "-" : node.getLatest()));
            }
            System.out.println(b);
        }
    }

    /**
     * Get nodes associated to each network id.
     * @param sequence
     * @return
     */
    private static Map<Integer, TreeSet<Node>> getMapNetworkIdNodes(Node[] sequence) {
        Map<Integer, TreeSet<Node>> networkIdNodes = new HashMap<>();

        for (Node node : sequence) {
            networkIdNodes.putIfAbsent(node.getNetworkId(), new TreeSet<>(Comparator.comparingInt(Node::getEarliest)) {
            });
            networkIdNodes.get(node.getNetworkId()).add(node);
        }
        return networkIdNodes;
    }

    public static List<Integer> getNetworkIdsFromNodeSequence(List<Node> nodeSequence) {
        List<Integer> networkIds = new ArrayList<>();
        for (Node node : nodeSequence) {
            networkIds.add(node.getNetworkId());

        }
        return networkIds;
    }
}