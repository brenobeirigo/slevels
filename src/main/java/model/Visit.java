package model;

import dao.Dao;
import model.node.Node;
import model.node.NodeTargetRebalancing;

import java.util.*;

import static config.Config.*;

public class Visit implements Comparable<Visit> {
    private int arrival;
    protected LinkedList<Node> sequenceVisits; // Sequence of pickup and delivery nodes
    private Set<User> setUsers; // Users in sequence of visits
    private Set<User> setFlexibleUsers; // Users that can still be moved
    protected Vehicle vehicle;
    protected double avgOccupationLeg;

    // Delays and idle times
    protected int delay, idle;

    public Visit(Visit v) {
        this.arrival = v.arrival;
        this.setUsers = new HashSet<>(v.setUsers);
        this.setFlexibleUsers = new HashSet<>(v.setFlexibleUsers);
        this.sequenceVisits = new LinkedList<>(v.sequenceVisits);
        this.vehicle = v.vehicle;
        this.avgOccupationLeg = v.avgOccupationLeg;
        this.delay = v.delay;
        this.idle = v.idle;
    }

    public Visit(LinkedList<Node> sequenceVisits, int delay, int idle) {
        this.sequenceVisits = sequenceVisits;
        this.delay = delay;
        this.idle = idle;
        this.setUsers = new HashSet<>();
    }

    public Visit(LinkedList<Node> sequenceVisits, int delay) {
        this.sequenceVisits = sequenceVisits;
        this.delay = delay;
        this.setUsers = new HashSet<>();
        this.setFlexibleUsers = new HashSet();
    }

    public Visit(LinkedList<Node> sequenceVisits,
                 int delay,
                 int idle,
                 double avgOccupationLeg) {

        this(sequenceVisits, delay, idle);
        this.avgOccupationLeg = avgOccupationLeg;
    }

    public static void reset() {
        return;
    }

    public Visit() {
        this.delay = Integer.MAX_VALUE;
        this.idle = Integer.MAX_VALUE;
        this.avgOccupationLeg = Double.MIN_VALUE;
    }

    /**
     * Get a list of users and return a list of pickup and delivery ids.
     * E.g.: [u1,u2,u3,u4] => [1,1,2,2,3,3,4,4]
     *
     * @param passengers List of users
     * @return Sequence of user ids representing pickup and delivery nodes
     */
    public static List<Integer> getIdPairListFromUsers(Set<User> passengers) {

        /* Create a sequenceVisits of PK and DL points given a list of passengers. */
        List<Integer> sequence = new ArrayList<>();

        for (User r : passengers) {
            // Add pickup node
            sequence.add(r.getNodePk().getTripId());

            // Add dropoff node
            sequence.add(r.getNodeDp().getTripId());
        }

        return sequence;
    }

    public int getArrival() {
        return arrival;
    }

    public Set<User> getSetUsers() {
        return setUsers;
    }

    public LinkedList<Node> getSequenceVisits() {
        return sequenceVisits;
    }

    public int getDelay() {
        return delay;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle v) {
        this.vehicle = v;
    }

    public int getIdle() {
        return idle;
    }

    public void update(LinkedList<Node> sequenceVisits,
                       int delay,
                       int idle,
                       double avgOccupationLeg,
                       int departureVehicleCurrent) {

        this.sequenceVisits = sequenceVisits;
        this.delay = delay;
        this.idle = idle;
        this.avgOccupationLeg = avgOccupationLeg;
    }

    public void setSetUsers(Set<User> setUsers) {
        this.setUsers = setUsers;
    }

    public void setSequenceVisits(LinkedList<Node> sequenceVisits) {
        this.sequenceVisits = sequenceVisits;
    }

    /**
     * @param that is a non-null Visit.
     */
    @Override
    public int compareTo(Visit that) {

        // Objects are equal
        //if (this == that) return EQUAL;
        if (that == null) return BEFORE;

        //"Empty" visit

        //if (this.getSequenceVisits() == null) return BEFORE;
        //if (that.getSequenceVisits() == null) return AFTER;

        // Privilege trip size
        //if (this.getSequenceVisits().size() > that.getSequenceVisits().size()) return BEFORE;
        //if (this.getSequenceVisits().size() < that.getSequenceVisits().size()) return AFTER;

        // Compare average occupation per leg
        //if (this.avgOccupationLeg > that.avgOccupationLeg) return BEFORE;
        //if (this.avgOccupationLeg < that.avgOccupationLeg) return AFTER;

        //if (this.getVehicle().getUsers().size() > this.getVehicle().getUsers().size()) return BEFORE;
        //if (this.getVehicle().getUsers().size() < this.getVehicle().getUsers().size()) return AFTER;

        //primitive numbers follow this form
        if (this.delay < that.delay) return BEFORE;
        if (this.delay > that.delay) return AFTER;

        return EQUAL;
    }

    @Override
    public String toString() {

        if (this.vehicle != null) {

        }

        Node current = this.vehicle.getCurrentNode();
        int load = this.vehicle.getLoad();
        // Node strings
        List<String> nodes = new ArrayList<>();
        int dep = this.vehicle.getCurrentNode().getDeparture();
        String n = String.format("<<%s>> - %s[%2d] <%4s | %4s>", vehicle, current, load, String.valueOf(current.getEarliest()), String.valueOf(dep));
        nodes.add(n);

        // If there is a sequence of visits
        if (getSequenceVisits() != null) {
            for (int i = 0; i < getSequenceVisits().size(); i++) {
                Node next = getSequenceVisits().get(i);
                int dist = Dao.getInstance().getDistSec(current, next);
                dep += dist;
                dep = Math.max(dep, current.getEarliest());
                current = next;
                load += current.getLoad();
                nodes.add(String.format("--> {%4s} --> %s[%2d] <%4s | %4s>", String.valueOf(dist), current, load, current.getEarliest(), String.valueOf(dep))); // config.Config.sec2TStamp(sequenceArrivals.get(i))));
            }
        }
        return String.format("%s (delay: %5d - occ.: %5.2f)", String.join(" ", nodes), delay, avgOccupationLeg);
    }



    /*
    This method is called when a visit is chosen to be the best match for a vehicle.
    The vehicle is updated with the information of a visit.

     */

    public String getInfo() {
        return "  - Users: " + setUsers +
                " - Avg. Occupation: " + avgOccupationLeg +
                " - Visits: " + sequenceVisits +
                " - Delay: " + delay +
                " - Idle: " + idle +
                " - Vehicle: " + vehicle.getInfo();
    }

    public void setup() {

        // If vehicle was rebalancing
        if (this.vehicle.isRebalancing()) {


            //System.out.println("STOPPED REBALANCING!" + this);

            NodeTargetRebalancing target = (NodeTargetRebalancing) this.vehicle.getVisit().getTargetNode();

            // Vehicles can be reschedule to this position again
            Node.tabu.remove(target.getNetworkId());

            // Target was not reached, back to hot points
            if (target.getGenNode().getArrival() == 0) {
                //TODO Does re-adding the node helps? It looks like YES!
                //System.out.println("STOPPED REB.:" + target.getGenNode().getUrgent() + " - " + target.getGenNode().getArrival() + " :" + this.getSequenceVisits().getFirst() + "-"+target+"-" + target.getGenNode());

                Vehicle.setOfHotPoints.add(target);
            }


            // Vehicle is no longer rebalancing (User was inserted)
            this.vehicle.stoppedRebalancingToPickup();


            double distTraveledKm = Dao.getInstance().getDistKm(this.vehicle.getCurrentNode(), this.vehicle.getVisit().getTargetNode());

            this.vehicle.increaseDistanceTraveledRebalancing(distTraveledKm);
        }

        // Add visit to vehicle (circular)
        this.vehicle.setVisit(this);

        // Vehicle set of users
        this.vehicle.setUsers(this.getSetUsers());

        // Vehicle is not idle
        this.vehicle.setRoundsIdle(0);


    }

    /**
     * Get arrival time at first node in sequence of visits
     *
     * @return Arrival time
     */
    public int getTargetArrival() {
        return this.vehicle.getDepartureCurrent() +
                Dao.getInstance().getDistSec(this.vehicle.getCurrentNode(),
                        this.sequenceVisits.getFirst());
    }

    /**
     * Get target node, that is, the node a vehicle is currently moving towards
     *
     * @return target node
     */
    public Node getTargetNode() {
        return this.sequenceVisits.getFirst();
    }

    public Set<User> getSetFlexibleUsers() {
        return this.setFlexibleUsers;
    }
}