package simulation;

import config.Config;
import dao.Dao;
import model.User;
import model.Vehicle;
import model.Visit;
import model.node.MultiSet;
import model.node.Node;

import java.util.*;

public class Method {

    public static int getEarliestDp(int earliest, int from, int to, char qos) {
        return earliest + Dao.getInstance().getDistSec(from, to);
    }

    public static int getLatestDp(int earliest, int from, int to, char qos) {
        return earliest + Dao.getInstance().getDistSec(from, to) + Config.getInstance().qosDic.get(qos).pkDelay;
    }

    public static int getLatestPK(int earliest, int from, int to, char qos) {
        return earliest + Dao.getInstance().getDistSec(from, to);
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

        User u = User.all_users.get(userId);

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


    //TODO: previous arrival

    /**
     * Get sequence of user ids representing nodes to visit (including vehicle nodes) and return visit (if possible)/
     *
     * @param sequenceUserIds
     * @param v               Model.Vehicle
     * @return Model.Visit or null
     */
    public static Visit getValidVisitOld(List<Integer> sequenceUserIds, Vehicle v) {

        // Update values in nodes
        int totalIdleness = 0;
        int totalDelay = 0;

        // Saves the order of nodes
        //TODO decompile sequence of trips into sequence of nodes
        Set<Integer> visitedIds = new HashSet<>();

        // Declare control sequences
        List<Node> sequenceVisits = new ArrayList<>();
        List<Integer> sequenceArrivals = new ArrayList<>();
        List<Integer> sequenceLoads = new ArrayList<>();

        // Model.Vehicle node data
        Node fromNode = v.getCurrentNode();
        int load = fromNode.getLoad();
        int arrivalFrom = fromNode.getArrival();

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
            sequenceArrivals.add(arrivalTo);
            sequenceLoads.add(load);

            // Update
            fromNode = toNode;
            arrivalFrom = arrivalTo;
        }

        // Return visit
        return new Visit(
                sequenceVisits,
                totalDelay,
                sequenceArrivals,
                totalIdleness,
                sequenceLoads);
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
        List<Node> sequenceVisits = new ArrayList<>();

        // Model.Vehicle node data
        Node fromNode = v.getCurrentNode();
        int load = fromNode.getLoad();
        int arrivalFrom = fromNode.getArrival();

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
     * <p>
     * Old - because save array of intermediate arrivals
     *
     * @param sequenceNodes
     * @param v             Model.Vehicle
     * @return Model.Visit or null
     */
    public static Visit getVisitFromNodeSequenceOld(List<Node> sequenceNodes, Vehicle v) {

        // Update values in nodes
        int totalIdleness = 0;
        int totalDelay = 0;

        // Saves the order of nodes
        //TODO decompile sequence of trips into sequence of nodes

        // Declare control sequences
        List<Node> sequenceVisits = new ArrayList<>();
        List<Integer> sequenceArrivals = new ArrayList<>();
        List<Integer> sequenceLoads = new ArrayList<>();

        // Model.Vehicle node data
        Node fromNode = v.getCurrentNode();
        int load = fromNode.getLoad();
        int arrivalFrom = fromNode.getArrival();

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
            sequenceArrivals.add(arrivalTo);
            sequenceLoads.add(load);

            // Update
            fromNode = toNode;
            arrivalFrom = arrivalTo;
        }

        // Return visit
        return new Visit(
                sequenceVisits,
                totalDelay,
                sequenceArrivals,
                totalIdleness,
                sequenceLoads);
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
        List<Node> sequenceVisits = new ArrayList<>();

        // Model.Vehicle node data
        Node fromNode = v.getCurrentNode();
        int load = fromNode.getLoad();
        int arrivalFrom = fromNode.getArrival();

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
    public static int[] feasibleTrip(Node from, Node next_node) {
        return feasibleTrip(from, next_node, -1);
    }

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
     * Create a sequence of trip ids to be permutated.
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
     * @param passengers            List of passengers searching for a ride
     * @param v                     Model.Vehicle where passengers will be inserted
     * @param findBestVisit         False, if first permutation is used to create a visit
     * @param maxNumberPermutations Limit the number of permutation
     * @return Model.Visit(passengers + vehicle ' s previous passengers) or null
     * @see Visit
     */
    public static Visit getVisit(Set<User> passengers, Vehicle v, boolean findBestVisit, int maxNumberPermutations) {

        // Sequence of requests and previous arrivals (if any)
        List<Integer> visits_vehicle = getPkDpUserIdSequence(passengers, v); //TODO: return arrival also

        // Get all permutations
        MultiSet m = new MultiSet(visits_vehicle);
        List<List<Integer>> permutations = m.getPermutations(maxNumberPermutations);

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
            if (visit != null && visit.getDelay() < best.getDelay()) {

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
    }

    public static Visit getBestInsertion(Set<User> candidates, Vehicle v) {

        // Get best permutation if list of users <= 3
        if (candidates.size() + v.getListUsers().size() <= 3) {
            return getVisit(candidates, v, false, 100);
        }


        // Vehicle sequence
        List<Node> vSequence;

        // If vehicle is empty
        if (v.getVisit() == null || v.getVisit().getSequenceVisits() == null) {
            // Start vehicle sequence
            vSequence = new ArrayList<>();

            //TODO Get best visit for |candidates| <=4?
            //if (candidates.size()>3) {
            //    Visit visit = getVisit(candidates, v, false, 100);
            //    System.out.println(visit);
            //}
            //  return visit;
        } else {
            // Vehicle sequence
            vSequence = new ArrayList<>(v.getVisit().getSequenceVisits());
        }

        // First best
        Visit best = new Visit();

        //System.out.println("Candidates:"+candidates);
        //System.out.println("Visits:"+v1.getListUsers());

        // Loop users and try to insert them in vehicle
        for (User u : candidates) {

            // If vehicle has no visit, construct a visit with first user
            if (vSequence.isEmpty()) {
                vSequence.add(u.getNodePk());
                vSequence.add(u.getNodeDp());

                // Best visit (so far) contains only first user
                best = getVisitFromNodeSequence(vSequence, v);

                // If a visit can't be constructed with only one user, there is no valid visit
                // containing this user
                if (best == null) {
                    //System.out.println("BEST:"+best);
                    return null;
                }

                //System.out.println("VSEQ:" + vSequence + " - BEST:" + best);

                // Jump to next user
                continue;
            }


            // True if user was inserted in sequence
            boolean inserted = false;

            // Loop all insertion positions
            for (int pkPos = 0; pkPos < vSequence.size(); pkPos++) {
                for (int dpPos = pkPos + 1; dpPos < vSequence.size() + 2; dpPos++) {


                    Visit visitInsertion = Method.getVisitByInsertion(u, vSequence, v, pkPos, dpPos, 0);

                    //System.out.println(String.format("Insert %d and %d -%s %s (%s)",pkPos, dpPos, u, vSequence, visitInsertion));

                    if (visitInsertion != null && visitInsertion.getDelay() < best.getDelay()) {
                        inserted = true;
                        best = visitInsertion;
                    }
                }
            }

            // User u was not inserted. Can't make sequence out of users.
            if (!inserted) return null;

            vSequence = best.getSequenceVisits();
            //System.out.println("NEW:" + vSequence);
        }

        // If best visit was not found
        if (best.getSequenceVisits() == null) {
            return null;
        }

        best.setVehicle(v);
        return best;
    }

    public static Visit getVisitByInsertion(User u,
                                            List<Node> bestSequence,
                                            Vehicle v, int pkPos,
                                            int dpPos, int currentTime) {


        List<Node> newSequence = new ArrayList<>(bestSequence);
        newSequence.add(pkPos, u.getNodePk());
        newSequence.add(dpPos, u.getNodeDp());
        //System.out.println(newSequence);

        return getVisitFromNodeSequence(newSequence, v);


        /*
        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.println("Vehicle: " + v.get_info());
        System.out.println(String.format("Insert: %d - %d", pkPos, dpPos));
        */

        /*
        Insert xX
              [0, 1, 2, 3, 4, 5, 6, 7]
        E.g.: [a, b, c, d, B, A, C, D]
                            [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
        i=0, j=1    - [x, X, a, b, c, d, B, A, C, D]
        i=0, j=7    -    [x, a, b, c, d, B, A, C, D, X]
        i=3, j=7    -       [a, b, c, x, d, B, A, C, D, X]
        i=3, j=5    -       [a, b, c, x, d, B, X, A, C, D, X]


         */

        /*
        List<Node> vSequence = v.getVisit().getSequenceVisits();
        List<Integer> vArrivals = v.getVisit().getSequenceArrivals();


        System.out.println(vSequence);
        System.out.println(vArrivals);


        // Arrivals after pkPos
        List<Integer> newArrivals = new ArrayList<>();

        // pkPos-1 to pkPos
        //Check if pkPos is zero
        Node beforePk;
        if(pkPos-1 <0){beforePk = v.getCurrentNode();}
        else{ beforePk = vSequence.get(pkPos-1); }

        int travelTimeToPK = Dao.getInstance().getDistSec(beforePk, u.getNodePk());
        int arrivalPk = currentTime+travelTimeToPK;

        System.out.println(" Before Pk: " + beforePk);
        System.out.println("  TT to Pk: " + travelTimeToPK);
        System.out.println("Arrival Pk: " + arrivalPk);


        Node afterPk = vSequence.get(pkPos);
        // pkPos + 1 to pkPos
        if(pkPos+1 >=vSequence.size()){
            //TODO insert in the end
        }

        else{ afterPk = vSequence.get(pkPos-1); }


        int travelTimeFromPK = Dao.getInstance().getDistSec(u.getNodePk(), afterPk);
        int arrivalNextPk = Math.max(afterPk.getEarliest(), arrivalPk+travelTimeFromPK);

        System.out.println("  After Pk: " + afterPk);
        System.out.println("TT from Pk: " + travelTimeFromPK);
        System.out.println(" Arr. next: " + arrivalNextPk);


        // Check between pkPos and dpPos
        int previousArrivalNext = vArrivals.get(pkPos);
        // Increment caused by insertion in node immediately after pkNode
        int increment = arrivalNextPk - previousArrivalNext;

        for (int i = pkPos; i < dpPos; i++) {

            int latestNext = vSequence.get(i).getLatest();
            if(arrivalNextPk+increment > latestNext){
                return null;
            }

            // Update new arrival time
            newArrivals.add(arrivalNextPk);

            // Update arrival at last pk
            arrivalNextPk = vSequence.get(i).getArrival();

        }

        /*
        // dpPos-1 to dpPos
        Node beforeDp = vSequence.get(dpPos-1);
        int travelTimeToDp = Dao.getInstance().getDistSec(beforeDp, u.getNodeDp());
        int arrivalDp = newArrivals.get(newArrivals.size()-1)+travelTimeToDp;


        // dpPos to dpPos + 1
        Node afterDp = vSequence.get(pkPos+1);
        int travelTimeFromDp = Dao.getInstance().getDistSec(u.getNodeDp(), afterDp);
        int arrivalNextDp = arrivalDp+travelTimeFromDp;


        // Check between pkPos and dpPos
        int previousArrivalNextDp = v.getVisit().getSequenceArrivals().get(pkPos+1);
        // Increment caused by insertion in node immediately after pkNode
        int increment2 = arrivalNextDp - previousArrivalNextDp;

        for (int i = dpPos; i < vSequence.size(); i++) {

            int latestNext = vSequence.get(i).getLatest();
            if(arrivalNextDp+increment2 > latestNext){
                return null;
            }

            // Update new arrival time
            newArrivals.add(arrivalNextPk);

            // Update arrival at last pk
            arrivalNextDp = vSequence.get(i).getArrival();

        }

        List<Node> newSequence = new ArrayList<>(vSequence);

        //Create sequence
        newSequence.add(pkPos, u.getNodePk());
        newSequence.add(dpPos, u.getNodeDp());


        List<Integer> arrivals = newArrivals.subList(0, pkPos);
        arrivals.addAll(newArrivals);
        */

        //return new Visit();
        //return new Visit(newSequence, 0, arrivals, 0, new ArrayList<>());

    }

    /**
     * Try to insert a user in every vehicle, and return the set of users inserted.
     *
     * @param listUsers       List of users to be assigned to a vehicle
     * @param listVehicles    List of vehicles operating
     * @param allPermutations True, if all permutations (considering user+vehicle stops) should be tested
     * @param stopAtFirstBest True, if solution is returned when first user/vehicle combination is found
     * @param currentTime     Refers to current latest TW time
     * @param maxPermutations Restrict the number of permutations
     * @param checkInParallel True, if visits should be checked in parallel
     * @return setServicedUsers Set of all users that could be inserted into vehicles
     */
    public static Set<User> getSolutionFCFS(Set<User> listUsers,
                                            List<Vehicle> listVehicles,
                                            boolean allPermutations,
                                            boolean stopAtFirstBest,
                                            int currentTime,
                                            int maxPermutations,
                                            boolean checkInParallel) {

        // Set of users serviced
        Set<User> setServicedUsers = new HashSet<>();

        // Loop users
        for (User u : listUsers) {

            //System.out.println(u+" - " + u.getNodePk().getEarliest() + " - "+ currentTime);

            // Aux. best visit for comparison
            Visit bestVisit = new Visit();

            // Sort vehicles according to arrival time
            Collections.sort(listVehicles);

            // Try to insert user in each vehicle
            for (Vehicle v : listVehicles) {

                // Final visit
                Visit candidateVisit = null;

                // Check in parallel
                if (checkInParallel) {
                        /*TODO implement parallel check
                         visit = Trip.gen_visit_parallel([r],
                                v=veh,
                                find_best=find_best_visits,
                                max_trips = max_trips,
                                distance_data = distance_data)
                        */

                } else {

                    // Sequence with user to be added in vehicle
                    Set<User> auxUserSequence = new HashSet<>();
                    auxUserSequence.add(u);

                    // #################################################################################################
                    // Get a candidate visit by trying to add user u to vehicle v
                    candidateVisit = Method.getVisit(auxUserSequence, v, allPermutations, maxPermutations);
                    // #################################################################################################
                }

                // Update best visit if delay of candidate visit is shorter
                if (candidateVisit != null && candidateVisit.getDelay() < bestVisit.getDelay()) {

                    // Updating visit
                    bestVisit = candidateVisit;

                    // Stop at the first improvement
                    if (stopAtFirstBest) {
                        break;
                    }
                }
            }

            // if best visit is found, update vehicle with visit data
            if (bestVisit.getSetUsers() != null) {

                // Add visit to vehicle (circular)
                bestVisit.getVehicle().setVisit(bestVisit);

                // Model.User u belongs to vehicle
                bestVisit.getVehicle().getListUsers().add(u);

                // Model.User u belongs to visit
                bestVisit.getSetUsers().add(u);

                // Model.User u was serviced
                setServicedUsers.add(u);

                //TODO: update latest nodes to arrival time
            }
        }

        // Return all serviced users
        return setServicedUsers;
    }
}