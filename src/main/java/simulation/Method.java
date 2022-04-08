package simulation;

import config.Config;
import config.Qos;
import dao.Dao;
import model.User;
import model.Vehicle;
import model.Visit;
import model.VisitByInsertion;
import model.node.Node;
import org.paukov.combinatorics.CombinatoricsVector;
import org.paukov.combinatorics.Generator;
import org.paukov.combinatorics.ICombinatoricsVector;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    /**
     * Get sequence of user ids representing nodes to visit (including vehicle nodes) and return visit (if possible)/
     *
     * @param sequenceUserIds
     * @param v               Model.Vehicle
     * @return Model.Visit or null
     */
    public static Visit getValidVisit(List<Integer> sequenceUserIds, Vehicle v) {

        // Update values in nodes
        int totalIdleness = 0;
        int totalDelay = 0;

        // Saves the order of nodes
        //TODO decompile sequence of trips into sequence of nodes
        Set<Integer> visitedIds = new HashSet<>();

        // Declare control sequences
        LinkedList<Node> sequenceVisits = new LinkedList<>();

        // Model.Vehicle node data
        Node fromNode = v.getLastVisitedNode();
        int load = fromNode.getLoad();
        int arrivalFrom = fromNode.getDeparture();

        Node toNode;

        // Loop elements in tuple
        for (int i = 0; i < sequenceUserIds.size(); i++) {

            // Add load
            toNode = getNodeFromTripId(sequenceUserIds.get(i), visitedIds);

            // Update loads (DP nodes have negative loads)
            load += toNode.getLoad();
            //System.out.println("Load:"+load);

            // Capacity constraint (if lower than zero, sequence is invalid! Visited DP before PK)
            if (load < 0 || load > v.getCapacity()) {
                return null;
            }

            /////////////////////////* VIABLE NEXT */////////////////////////////////////
            int distFromTo = Dao.getInstance()
                    .getDistSec(
                            fromNode.getNetworkId(),
                            toNode.getNetworkId());

            // No path available
            if (distFromTo < 0)
                return null;

            // Time vehicle arrives at next node (can be earlier or later)
            int arrivalTo = arrivalFrom + distFromTo;

            // Arrival cannot be later than latest time in node
            if (arrivalTo > toNode.getLatest())
                return null;

            // Delay in seconds
            // If negative: Idle time, i.e., vehicle arrives earlier and wait until earliest time
            // If positive: arrival_next_node = arrivalTo, i.e., vehicle arrives after earliest time
            int delay = arrivalTo - toNode.getEarliest();

            // Update totals
            totalIdleness = totalIdleness + Math.min(delay, 0);
            totalDelay = totalDelay + Math.max(delay, 0);

            // Update arrival time at next node to >= earliest time of nexxt node
            arrivalTo = Math.max(arrivalTo, toNode.getEarliest());

            /*TODO: previous_arrival: verify if arrival is better than previous arrival saved for node*/
            // In this sequence, one of the customers was already scheduled earlier.
            // The new journey must decrease waiting time of all passengers.

            //if( previous_arrival >= 0 && next_node in previous_arrival and previous_arrival[next_node]<arrivalTo){
            //    Model.Node.Model.Node.memo_dic[id_viable] = (None,None)
            //    return delays;

            ///////////////////////////////////////////////////////////////////////////////
            // Update sequences
            sequenceVisits.add(toNode);

            // Update
            fromNode = toNode;
            arrivalFrom = arrivalTo;
        }

        // Return visit
        return new Visit(
                sequenceVisits,
                totalDelay,
                totalIdleness);
    }

    /**
     * Get sequence of nodes to visit (including vehicle nodes) and return visit (if possible)/
     *
     * @param sequenceNodes
     * @param v             Model.Vehicle
     * @return Model.Visit or null
     */
    public static Visit getVisitFromNodeSequence(List<Node> sequenceNodes, Vehicle v) {

        // Update values in nodes
        int totalIdleness = 0;
        int totalDelay = 0;

        // Saves the order of nodes
        //TODO decompile sequence of trips into sequence of nodes

        // Declare control sequences
        LinkedList<Node> sequenceVisits = new LinkedList<>();

        // Model.Vehicle node data
        Node fromNode = v.getLastVisitedNode();
        int load = fromNode.getLoad();
        int arrivalFrom = v.getDepartureCurrent();

        Node toNode;

        // Loop elements in tuple
        for (int i = 0; i < sequenceNodes.size(); i++) {

            // Add load
            toNode = sequenceNodes.get(i);

            // Update loads (DP nodes have negative loads)
            load += toNode.getLoad();
            //System.out.println("Load:"+load);

            // Capacity constraint (if lower than zero, sequence is invalid! Visited DP before PK)
            if (load < 0 || load > v.getCapacity()) {
                return null;
            }

            /////////////////////////* VIABLE NEXT */////////////////////////////////////
            int distFromTo = Dao.getInstance().getDistSec(fromNode, toNode);

            // No path available
            if (distFromTo < 0)
                return null;

            // Time vehicle arrives at next node (can be earlier or later)
            int arrivalTo = arrivalFrom + distFromTo;

            // Arrival cannot be later than latest time in node
            if (arrivalTo > toNode.getLatest())
                return null;

            // Delay in seconds
            // If negative: Idle time, i.e., vehicle arrives earlier and wait until earliest time
            // If positive: arrival_next_node = arrivalTo, i.e., vehicle arrives after earliest time
            int delay = arrivalTo - toNode.getEarliest();

            // Update totals
            totalIdleness = totalIdleness + Math.min(delay, 0);
            totalDelay = totalDelay + Math.max(delay, 0);

            // Update arrival time at next node to >= earliest time of next node
            arrivalTo = Math.max(arrivalTo, toNode.getEarliest());

            /*TODO: previous_arrival: verify if arrival is better than previous arrival saved for node*/
            // In this sequence, one of the customers was already scheduled earlier.
            // The new journey must decrease waiting time of all passengers.

            //if( previous_arrival >= 0 && next_node in previous_arrival and previous_arrival[next_node]<arrivalTo){
            //    Model.Node.Model.Node.memo_dic[id_viable] = (None,None)
            //    return delays;

            ///////////////////////////////////////////////////////////////////////////////
            // Update sequences
            sequenceVisits.add(toNode);

            // Update
            fromNode = toNode;
            arrivalFrom = arrivalTo;
        }

        // Return visit
        return new Visit(
                sequenceVisits,
                totalDelay,
                totalIdleness);
    }


    /**
     * Check if there is a valid trip between "fromNode" and "toNode" occurring in vehicle "v".
     * If true, update trip status to reflect the checking of the current leg
     *
     * @param fromNode   Origin node
     * @param toNode     Destination node
     * @param v          Vehicle
     * @param tripStatus Precedent status to update
     * @return true, if there is a valid trip, and false, otherwise.
     */
    public static boolean isValidLeg(Node fromNode,
                                     Node toNode,
                                     Vehicle v,
                                     int[] tripStatus) {

        // tripStatus[0] - arrivalFrom
        // tripStatus[1] - loadFrom
        // tripStatus[2] - totalDelay
        // tripStatus[3] - totalIdleness
        // tripStatus[4] - relative occupancy


        // Update loads (DP nodes have negative loads)
        int load = tripStatus[1] + toNode.getLoad();


        // Capacity constraint (if lower than zero, sequence is invalid! Visited DP before PK)
        if (load < 0 || load > v.getCapacity()) {
            return false;
        }

        /////////////////////////* VIABLE NEXT */////////////////////////////////////
        int distFromTo = Dao.getInstance().getDistSec(fromNode, toNode);

        // No path available
        if (distFromTo < 0) {
            return false;
        }
        // Time vehicle arrives at next node (can be earlier or later)
        int arrivalTo = tripStatus[0] + distFromTo;
        /*
        System.out.println(String.format("%s -> %s: %d + %d = %d (%d, %d)",
                fromNode,
                toNode,
                tripStatus[0],
                distFromTo,
                arrivalTo,
                fromNode.getEarliest(),
                toNode.getLatest()));
        */
        // Arrival cannot be later than latest time in node
        if (arrivalTo > toNode.getLatest()) {
            return false;
        }

        //int occupationTime = (distFromTo==0?0:1)*(int)((1000000 * ((double)tripStatus[1]/v.getCapacity()/distFromTo)));
        int occupationTime = (int) (100 * ((double) tripStatus[1] / v.getCapacity()));


        //System.out.println(fromNode + "->" +toNode +": "+ (occupationTime)/100);
        //System.out.println(fromNode + "->" +toNode +": "+ (1000000 * (double)tripStatus[1]) + " / " + v.getCapacity() +" / " + distFromTo + " = "+ occupationTime);
        //System.out.println(fromNode + "->" +toNode +": "+ (100 * (double)tripStatus[1]) + " / " + v.getCapacity() + " = "+ occupationTime);

        // Update arrival time at next node to >= earliest time of next node
        tripStatus[0] = Math.max(arrivalTo, toNode.getEarliest());

        // Update load
        tripStatus[1] = load;

        // Delay in seconds
        // If negative: Idle time, i.e., vehicle arrives earlier and wait until earliest time
        // If positive: arrival_next_node = arrivalTo, i.e., vehicle arrives after earliest time
        int delay = arrivalTo - toNode.getEarliest();

        // Delay and idleness
        tripStatus[2] += Math.max(delay, 0);
        tripStatus[3] += Math.min(delay, 0);

        // Update occupation time
        tripStatus[4] += occupationTime;

        /*TODO: previous_arrival: verify if arrival is better than previous arrival saved for node*/
        // In this sequence, one of the customers was already scheduled earlier.
        // The new journey must decrease waiting time of all passengers.

        //if( previous_arrival >= 0 && next_node in previous_arrival and previous_arrival[next_node]<arrivalTo){
        //    Model.Node.Model.Node.memo_dic[id_viable] = (None,None)
        //    return delays;

        ///////////////////////////////////////////////////////////////////////////////
        // Update sequences
        return true;
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

    /**
     * Try to create a visit by inserting a list of passengers into vehicle.
     * List is constructed by method getPkDpUserIdSequence
     *
     */
    /*public static Visit getVisitByPermutation(Set<User> passengers,
                                              Vehicle v,
                                              boolean findBestVisit,
                                              int maxNumberPermutations) {

        // Sequence of requests and previous arrivals (if any)
        List<Integer> visits_vehicle = getPkDpUserIdSequence(passengers, v); //TODO: return arrival also

        // Get all permutations
        //MultiSet m = new MultiSet(visits_vehicle);
        //List<List<Integer>> permutations = m.getPermutations(maxNumberPermutations);

        // Aux. best visit for comparison
        Visit best = new Visit();

        // Loop permutation
        boolean up = false;

        //Number of trips
        int cont_trip = 0;

        // Loop permutation to find the best
        for (List<Integer> trip : permutations) {

            // Increment number of permutations tested
            cont_trip = cont_trip + 1;

            // Stop checking permutations?
            if (cont_trip == maxNumberPermutations)
                break;
            //#########################################################################################################
            // Get visit involving vehicle and permutations
            Visit visit = getValidVisit(trip, v);
            //#########################################################################################################

            // if permutation gives origin to a valid visit
            if (visit != null && visit.compareTo(best) < 0) {

                // Better permutation found
                up = true;

                // Update best
                best = visit;

                // Break if finds the first best
                if (!findBestVisit)
                    break;
            }
        }

        //If a valid visit was found
        if (up) {

            //Update vehicle in visit
            best.setVehicle(v);
            return best;
        }
        return null;
    }*/


//    public static Visit getBestInsertionNoAddFirst(Set<User> candidateRequests,
//                                                   Vehicle v,
//                                                   int currentTime,
//                                                   int numberOfUsersPermutation,
//                                                   boolean findBestVisit,
//                                                   int maxNumberPermutations) {
//
//        //System.out.println("#################################### ADD "+v);
//        //if(v.getVisit()!=null){
//        //    System.out.println(v.getVisit().getInfo());
//        //}
//        // Get best permutation if list of users <= numberOfUsersPermutation
//        //if (candidateRequests.size() + v.getUsers().size() <= numberOfUsersPermutation) {
//        //    return getVisitByPermutation(candidateRequests, v, findBestVisit, maxNumberPermutations);
//        //}
//
//        // Candidate sequence that will be formed
//        List<Node> visitSequence;
//
//        // First best including new users is initiated blank
//        Visit bestVisit = new Visit();
//
//        // If vehicle has NO visits, place first user in candidate sequence
//        if (v.getVisit() == null ||
//                v.getVisit().getSequenceVisits() == null ||
//                v.getVisit().getSequenceVisits().isEmpty()) {
//
//            // Start empty candidate sequence
//            visitSequence = new ArrayList<>();
//
//        } else {
//
//            // Copy elements in vehicle visit to candidate sequence
//            visitSequence = new ArrayList<>(v.getVisit().getSequenceVisits());
//        }
//
//        // Loop users and try to insert them in vehicle
//        for (User candidateUser : candidateRequests) {
//
//            //System.out.println("####### User:" + u);
//
//            // True if user was inserted in sequence
//            boolean inserted = false;
//
//
//            // Loop all insertion positions
//            for (int pkPos = 0; pkPos <= visitSequence.size(); pkPos++) {
//                for (int dpPos = pkPos; dpPos <= visitSequence.size(); dpPos++) {
//
//                    // Try to get a visit by inserting candidate user in sequence
//                    Visit candidateVisit = Method.getVisitByInsertPosition(candidateUser, visitSequence, v, pkPos, dpPos, currentTime);
//
//                    //System.out.println(String.format("Insert %d and %d -%s %s (%s)", pkPos, dpPos, candidateUser, visitSequence, candidateVisit));
//
//                    // Update best visit
//                    if (candidateVisit != null && candidateVisit.compareTo(bestVisit) < 0) {
//                        inserted = true;
//                        bestVisit = candidateVisit;
//                    }
//                }
//            }
//
//            // User u was not inserted. Can't make sequence out of ALL users.
//            if (!inserted) return null;
//
//            // Update visit sequence from best visit found so far
//            visitSequence = bestVisit.getSequenceVisits();
//        }
//
//        // If best visit was not found
//        if (bestVisit.getSequenceVisits() == null) {
//            return null;
//        }
//
//        // Assign vehicle to best
//        bestVisit.setVehicle(v);
//        // TODO What is this logic?
//        //bestVisit.setUsers(candidateRequests);
//        //bestVisit.getUsers().addAll(v.getVisit().getUsers());
//
//
//        return bestVisit;
//
//
//    }

    public static Visit getBestInsertionParallel(Set<User> candidateRequests,
                                                 Vehicle v,
                                                 int numberOfUsersPermutation,
                                                 boolean findBestVisit,
                                                 int maxNumberPermutations) {

        // Get best permutation if list of users <= numberOfUsersPermutation
        //if (candidateRequests.size() + v.getUsers().size() <= numberOfUsersPermutation) {
        //    return getVisitByPermutation(candidateRequests, v, findBestVisit, maxNumberPermutations);
        //}


        // Vehicle sequence
        List<Node> candidateSequence;
        Iterator<User> iteratorUser = candidateRequests.iterator();

        // First best including new users is initiated blank
        Visit best = new Visit();

        // If vehicle has visits, start candidate sequence with vehicle elements
        if (v.getVisit() == null ||
                v.getVisit().getSequenceVisits() == null ||
                v.getVisit().getSequenceVisits().isEmpty()) {

            User firstUser = iteratorUser.next();

            // Start empty candidate sequence
            candidateSequence = new ArrayList<>();

            // Add first user to the sequence
            candidateSequence.add(firstUser.getNodePk());
            candidateSequence.add(firstUser.getNodeDp());

            // Best visit (so far) contains only first user
            best = getVisitFromNodeSequence(candidateSequence, v);

            // If a visit can't be constructed with only one user, there is no valid visit
            // containing this user. Hence, candidate uses cannot be combined.
            if (best == null) {
                //System.out.println("BEST:"+best);
                return null;
            }

        } else {

            // Copy elements in vehicle visit to candidate sequence
            candidateSequence = new ArrayList<>(v.getVisit().getSequenceVisits());

        }

        //System.out.println("Candidates:"+candidates);
        //System.out.println("Visits:"+v.getUsers());

        // Loop users and try to insert them in vehicle
        while (iteratorUser.hasNext()) {

            User u = iteratorUser.next();
            //System.out.println("####### User:" + u);

            // True if user was inserted in sequence
            boolean inserted = false;

            List<Visit> candidateVisits = new ArrayList<>();
            //ExecutorService executor = Executors.newFixedThreadPool(10);

            ExecutorService executor = Executors.newWorkStealingPool();

            List<Thread> tt = new ArrayList<>();

            // Loop all insertion positions
            for (int pkPos = 0; pkPos <= candidateSequence.size(); pkPos++) {
                for (int dpPos = pkPos + 1; dpPos <= candidateSequence.size(); dpPos++) {

                    VisitByInsertion candidate = new VisitByInsertion(u, v, candidateSequence, pkPos, dpPos);
                    candidateVisits.add(candidate);
                    Thread t = new Thread(candidate);
                    tt.add(t);
                    t.start();

                    //System.out.println(String.format("Insert %d and %d -%s %s (%s)", pkPos, dpPos, u, vSequence, visitInsertion));
                }
            }

            try {
                for (Thread t : tt) {
                    t.join();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


            for (int i = 0; i < candidateVisits.size(); i++) {
                if (candidateVisits.get(i).getDelay() > 0) {
                    inserted = true;
                    best = candidateVisits.get(i);
                }
            }

            // User u was not inserted. Can't make sequence out of ALL users.
            if (!inserted) return null;

            candidateSequence = best.getSequenceVisits();
        }

        // If best visit was not found
        if (best.getSequenceVisits() == null) {
            return null;
        }

        best.setVehicle(v);
        return best;
    }

    /**
     * Try to insert user pickup and drop-off point in base visit sequence occurring in vehicle.
     *
     * @param insertedUser User who is being inserted
     * @param baseSequence Visit sequence being modified
     * @param vehicle      Vehicle carrying out the trip
     * @param pkPos        Pickup point insert position
     * @param dpPos        Drop-off point insert position
     * @param currentTime  Time vehicle is leaving vehicle current node
     * @return Visit with user "insertedUser" inserted in base visit sequence OR null if visit not found
     */
    public static Visit getVisitByInsertPosition(User insertedUser,
                                                 List<Node> baseSequence,
                                                 Vehicle vehicle,
                                                 int pkPos,
                                                 int dpPos,
                                                 int currentTime) {

        //System.out.println("#########################################################################################");

        // Create linked list (fast adds and removals)
        LinkedList<Node> newSequence = new LinkedList<>();

        // tripStatus[0] - Integer arrivalFrom
        // tripStatus[1] - Integer loadFrom
        // tripStatus[2] - Integer totalDelay
        // tripStatus[3] - Integer totalIdleness
        // tripStatus[4] - Load

        // Status throughout sequence (arrival, load, delay, idle)
        int[] tripStatus = new int[5];

        // Arrival time of vehicle current node (if first node in visit sequence does not change).
        // Otherwise, current time.
        int visitCurrentTime;

        // If first leg of vehicle trips is kept, the original visit start time can be maintained.
        // In fact, only nodes after the first leg will be modified.
        if (pkPos > 0 && vehicle.getVisit() != null && baseSequence.get(0) == vehicle.getVisit().getSequenceVisits().getFirst()) {
            // vehicle.getVisit()!= null &&

            // Departure time of vehicle original visit may remain the same since node "pkPos" can still be visited
            tripStatus[0] = visitCurrentTime = vehicle.getDepartureCurrent();

        } else {

            // If first leg will be changed, the earliest arrival time at vehicle
            // current node shall be updated to current time
            tripStatus[0] = visitCurrentTime = currentTime;
        }

        //System.out.println(pkPos + "("+ vehicle.getLastVisitedNode() + ") Arrival in vehicle:" + tripStatus[0] + "-- Current time:" + departureVehicleCurrent);

        // Load
        tripStatus[1] = vehicle.getLastVisitedNode().getLoad();

        // Current node is vehicle
        Node current = vehicle.getLastVisitedNode();

        // #### Before PK ##############################################################################################
        for (int i = 0; i < pkPos; i++) {

            // Get next in sequence
            Node next = baseSequence.get(i);

            // Check if it is possible to go from current to next
            if (!isValidLeg(current, next, vehicle, tripStatus)) {
                return null;
            }

            // Update current
            current = next;
            newSequence.add(current);
        }

        // #### PK #####################################################################################################
        if (!isValidLeg(current, insertedUser.getNodePk(), vehicle, tripStatus)) {
            return null;
        }

        // Update current
        current = insertedUser.getNodePk();
        newSequence.add(current);

        // #### Between PK and DP ######################################################################################
        for (int i = pkPos; i < dpPos; i++) {

            // Get next in sequence
            Node next = baseSequence.get(i);

            // Check if it is possible to go from current to next
            if (!isValidLeg(current, next, vehicle, tripStatus)) {
                return null;
            }

            // Update current
            current = next;
            newSequence.add(current);
        }

        // #### DP #####################################################################################################
        if (!isValidLeg(current, insertedUser.getNodeDp(), vehicle, tripStatus))
            return null;

        // Update current
        current = insertedUser.getNodeDp();
        newSequence.add(current);

        // #### After DP ###############################################################################################
        for (int i = dpPos; i < baseSequence.size(); i++) {

            // Get next in sequence
            Node next = baseSequence.get(i);

            // Check if it is possible to go from current to next
            if (!isValidLeg(current, next, vehicle, tripStatus)) {
                return null;
            }

            // Update current
            current = next;
            newSequence.add(current);
        }
        /*
        System.out.println("Vehicle: " + vehicle.getLastVisitedNode().getArrival() + "->" + vehicle.getVisit().getSequenceArrivals() + "("+ vehicle.getVisit().getSequenceVisits()+")");
        System.out.println("Vehicle: " + vehicle.getLastVisitedNode().getArrival() + "->" + vehicle.getVisit().getSequenceArrivals() + "("+ vehicle.getVisit().getSequenceVisits()+")");
        System.out.println("Current: " + departureVehicleCurrent + "->" + newSequenceArrival + "("+ newSequence+")");
        if(vehicle.getLastVisitedNode().getArrival() != departureVehicleCurrent){
            System.out.println("----------------------------------------------------------------------------------------");
        }else {
            System.out.println("****************************************************************************************");
        }
        */
        //System.out.println("Visit:" + visitCurrentTime + "-- Current:" + departureVehicleCurrent);
        double occupationLeg = (double) tripStatus[4] / (newSequence.size() - 1);
        //System.out.println("AVG:"+occupationLeg);


        Visit visit = new Visit(newSequence,
                tripStatus[2],
                tripStatus[3],
                occupationLeg);

        //System.out.println("TRIP STATUS:"+ tripStatus[4]);
        //System.out.println(pkPos +")" + u.getNodePk() + " -- "+ dpPos +")" + u.getNodeDp());
        //System.out.println(bestSequence);
        //System.out.println(newSequence);
        //System.out.println(visit);
        //System.out.println("-------------------------- i: "+ i);

        return visit;
    }

//    /**
//     * Try to insert a user in every vehicle, and return the set of users inserted.
//     *
//     * @param listUsers       List of users to be assigned to a vehicle
//     * @param listVehicles    List of vehicles operating
//     * @param allPermutations True, if all permutations (considering user+vehicle stops) should be tested
//     * @param stopAtFirstBest True, if solution is returned when first user/vehicle combination is found
//     * @param currentTime     Refers to current latest TW time
//     * @param maxPermutations Restrict the number of permutations
//     * @param checkInParallel True, if visits should be checked in parallel
//     * @return setServicedUsers Set of all users that could be inserted into vehicles
//     */
//    public static Set<User> getSolutionFCFS(Set<User> listUsers,
//                                            List<Vehicle> listVehicles,
//                                            boolean allPermutations,
//                                            boolean stopAtFirstBest,
//                                            int currentTime,
//                                            int maxPermutations,
//                                            boolean checkInParallel) {
//
//        // Set of users serviced
//        Set<User> setServicedUsers = new HashSet<>();
//
//        // Loop users
//        for (User u : listUsers) {
//
//            //System.out.println(u+" - " + u.getNodePk().getEarliest() + " - "+ departureVehicleCurrent);
//
//            // Aux. best visit for comparison
//            Visit bestVisit = new Visit();
//
//            // Sort vehicles according to arrival time
//            //Collections.sort(listVehicles);
//
//            // Try to insert user in each vehicle
//            for (Vehicle v : listVehicles) {
//
//                // Final visit
//                Visit candidateVisit = null;
//
//                // Check in parallel
//                if (checkInParallel) {
//                        /*TODO implement parallel check
//                         visit = Trip.gen_visit_parallel([r],
//                                v=veh,
//                                find_best=find_best_visits,
//                                max_trips = max_trips,
//                                distance_data = distance_data)
//                        */
//
//                } else {
//
//                    // Sequence with user to be added in vehicle
//                    Set<User> auxUserSequence = new HashSet<>();
//                    auxUserSequence.add(u);
//
//                    // #################################################################################################
//                    // Get a candidate visit by trying to add user u to vehicle v
//                    candidateVisit = Method.getVisitByPermutation(auxUserSequence, v, allPermutations, maxPermutations);
//                    // #################################################################################################
//                }
//
//                // Update best visit if delay of candidate visit is shorter
//                if (candidateVisit != null && candidateVisit.compareTo(bestVisit) < 0) {
//
//                    // Updating visit
//                    bestVisit = candidateVisit;
//
//                    // Stop at the first improvement
//                    if (stopAtFirstBest) {
//                        break;
//                    }
//                }
//            }
//
//            // if best visit is found, update vehicle with visit data
//            if (bestVisit.getUsers() != null) {
//
//                // Add visit to vehicle (circular)
//                bestVisit.getVehicle().setVisit(bestVisit);
//
//                // Model.User u belongs to vehicle
//                bestVisit.getVehicle().getVisit().getUsers().add(u);
//
//                // Model.User u belongs to visit
//                bestVisit.getUsers().add(u);
//
//                // Model.User u was serviced
//                setServicedUsers.add(u);
//
//                //TODO: update latest nodes to arrival time
//            }
//        }
//
//        // Return all serviced users
//        return setServicedUsers;
//    }

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

    public static Visit getBestVisitFor(Vehicle vehicle, Set<User> requests) {

        Generator<Node> gen = getGeneratorOfNodeSequence(requests, vehicle);
        // System.out.println("Setting up sequence " + vehicle.getVisit());

        // A single request can be inserted in a vehicle in multiple ways. Only the best (i.e., the lowest delay)
        // visit is inserted in the RTV graph.

        List<List<Node>> sequences = new LinkedList<>();
        //Map<String, Visit> bestVisitOfEachClass = new HashMap<>();

        Visit visit = null;
        int lowestDelay = Integer.MAX_VALUE;
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

            if (delay >= 0 && delay < lowestDelay) {

                lowestDelay = delay;

                // Remove last visited node from sequence
                sequenceFromVehiclePositionToLastDelivery.poll();

                // Setup new visit
                visit = new Visit(sequenceFromVehiclePositionToLastDelivery, delay);
                //System.out.println("     # comb " + sequence + " - " + delay + " = " + visit);
            }
        }

        if (visit != null) {

            // Finish visit configuration
            visit.setVehicle(vehicle);
            visit.getRequests().addAll(requests);

            // All passengers in vehicle belong to visit (they need to be drop off)
            if (vehicle.isServicing()) {
                visit.setPassengers(vehicle.getVisit().getPassengers());
            }
        }

        // TODO: is this needed?
        if (visit == null && requests.isEmpty()) {
            System.out.println(sequences);
            System.out.println("Invalid sequence!");
            for (List<Node> seq : sequences) {
                int delay = Visit.isValidSequence(
                        (LinkedList<Node>) seq,
                        vehicle.getDepartureCurrent(),
                        vehicle.getCurrentLoad(),
                        vehicle.getCapacity(),
                        vehicle.getContractDeadline()
                );
            }
        }
        return visit;
    }


    public static void reset() {
        return;
    }
}