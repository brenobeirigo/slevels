package model;

import dao.Dao;
import model.node.Node;

import java.util.LinkedList;
import java.util.List;

public class VisitByInsertion extends Visit implements Runnable {

    private User candidate;
    private int pkPos;
    private int dpPos;
    private List<Node> candidateVisit;

    public VisitByInsertion(User u, Vehicle v, List<Node> candidateVisit, int pkPos, int dpPos) {
        super();
        this.pkPos = pkPos;
        this.dpPos = dpPos;
        this.vehicle = v;
        this.candidate = u;
        this.candidateVisit = candidateVisit;
    }

    @Override
    public void run() {
        // Create linked list (fast adds and removals)
        LinkedList<Node> newSequence = new LinkedList<>();


        // intermediate[0] - Integer arrivalFrom
        // intermediate[1] - Integer loadFrom
        // intermediate[2] - Integer totalDelay
        // intermediate[3] - Integer totalIdleness

        // Status throughout sequence (arrival, load, delay, idle)
        int[] visitStatus = new int[4];

        // Arrival
        visitStatus[0] = this.vehicle.getLastVisitedNode().getArrival();

        // Load
        visitStatus[1] = this.vehicle.getLastVisitedNode().getLoad();

        // Current node is vehicle
        Node current = this.vehicle.getLastVisitedNode();

        // #### Before PK ##############################################################################################
        for (int i = 0; i < pkPos; i++) {

            // Get next in sequence
            Node next = candidateVisit.get(i);

            // Check if it is possible to go from current to next
            if (calculateArrival(current, next, visitStatus) == null) {
                return;
            }

            // Update current
            current = next;
            newSequence.add(current);
        }

        // #### PK #####################################################################################################
        if (calculateArrival(current, this.candidate.getNodePk(), visitStatus) == null) {
            return;
        }

        // Update current
        current = this.candidate.getNodePk();
        newSequence.add(current);

        // #### Between PK and DP ######################################################################################
        for (int i = pkPos; i < dpPos; i++) {

            // Get next in sequence
            Node next = candidateVisit.get(i);

            // Check if it is possible to go from current to next
            if (calculateArrival(current, next, visitStatus) == null) {
                return;
            }

            // Update current
            current = next;
            newSequence.add(current);
        }

        // #### DP #####################################################################################################
        if (calculateArrival(current, this.candidate.getNodeDp(), visitStatus) == null)
            return;

        // Update current
        current = this.candidate.getNodeDp();
        newSequence.add(current);

        // #### After DP ###############################################################################################
        for (int i = dpPos; i < candidateVisit.size(); i++) {

            // Get next in sequence
            Node next = candidateVisit.get(i);

            // Check if it is possible to go from current to next
            if (calculateArrival(current, next, visitStatus) == null) {
                return;
            }

            // Update current
            current = next;
            newSequence.add(current);
        }

        this.sequenceVisits = newSequence;
        this.delay = visitStatus[2];
        this.idle = visitStatus[3];
    }

    public Integer calculateArrival(Node fromNode,
                                    Node toNode,
                                    int[] intermediate) {

        // intermediate[0] - arrivalFrom
        // intermediate[1] - loadFrom
        // intermediate[2] - totalDelay
        // intermediate[3] - totalIdleness

        // Update loads (DP nodes have negative loads)
        int load = intermediate[1] + toNode.getLoad();
        //System.out.println("Load:"+load);

        // Capacity constraint (if lower than zero, sequence is invalid! Visited DP before PK)
        if (load < 0 || load > this.vehicle.getCapacity()) {
            return null;
        }

        /////////////////////////* VIABLE NEXT */////////////////////////////////////
        int distFromTo = Dao.getInstance().getDistSec(fromNode, toNode);

        // No path available
        if (distFromTo < 0)
            return null;

        // Time vehicle arrives at next node (can be earlier or later)
        int arrivalTo = intermediate[0] + distFromTo;

        // Arrival cannot be later than latest time in node
        if (arrivalTo > toNode.getLatest())
            return null;


        // Update totals
        intermediate[0] = arrivalTo;
        intermediate[1] = load;

        // Delay in seconds
        // If negative: Idle time, i.e., vehicle arrives earlier and wait until earliest time
        // If positive: arrival_next_node = arrivalTo, i.e., vehicle arrives after earliest time
        int delay = intermediate[0] - toNode.getEarliest();

        // Delay and idleness
        intermediate[3] += Math.min(delay, 0);
        intermediate[2] += Math.max(delay, 0);


        // Update arrival time at next node to >= earliest time of nexxt node
        intermediate[0] = Math.max(intermediate[0], toNode.getEarliest());

        /*TODO: previous_arrival: verify if arrival is better than previous arrival saved for node*/
        // In this sequence, one of the customers was already scheduled earlier.
        // The new journey must decrease waiting time of all passengers.

        //if( previous_arrival >= 0 && next_node in previous_arrival and previous_arrival[next_node]<arrivalTo){
        //    Model.Node.Model.Node.memo_dic[id_viable] = (None,None)
        //    return delays;

        ///////////////////////////////////////////////////////////////////////////////
        // Update sequences
        return intermediate[0];
    }
}
