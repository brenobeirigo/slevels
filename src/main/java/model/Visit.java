package model;

import dao.Dao;
import model.node.Node;

import java.util.*;
import java.util.stream.Collectors;

import static config.Config.*;

public class Visit implements Comparable<Visit> {
    private int arrival;
    protected LinkedList<Node> sequenceVisits; // Sequence of pickup and delivery nodes
    private Set<User> passengers; // Users picked up
    private Set<User> requests; // Users that can still be moved (not picked up)
    protected Vehicle vehicle;
    protected double avgOccupationLeg;

    // Delays and idle times
    protected int delay, idle;

    public Visit(Visit v) {
        this.arrival = v.arrival;
        this.requests = new HashSet<>(v.requests);
        this.passengers = new HashSet<>(v.passengers);
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
        this.passengers = new HashSet<>();
        this.requests = new HashSet<>();
    }

    public Visit(LinkedList<Node> sequenceVisits, int delay, int idle, Vehicle v) {
        this.sequenceVisits = sequenceVisits;
        this.delay = delay;
        this.idle = idle;
        this.vehicle = v;
        this.passengers = new HashSet<>();
        this.requests = new HashSet<>();
    }

    public Visit(LinkedList<Node> sequenceVisits, int delay) {
        this.sequenceVisits = sequenceVisits;
        this.delay = delay;
        this.requests = new HashSet();
        this.passengers = new HashSet<>();
    }

    public Visit(LinkedList<Node> sequenceVisits, int delay, Vehicle v) {
        this.sequenceVisits = sequenceVisits;
        this.delay = delay;
        this.vehicle = v;
        this.requests = new HashSet();
        this.passengers = new HashSet<>();
    }

    public Visit(LinkedList<Node> sequenceVisits, int delay, Vehicle v, User request) {
        this.sequenceVisits = sequenceVisits;
        this.delay = delay;
        this.vehicle = v;
        this.requests = new HashSet();
        this.requests.add(request);
        this.passengers = new HashSet<>();
    }

    public Visit(LinkedList<Node> sequenceVisits,
                 int delay,
                 int idle,
                 double avgOccupationLeg) {

        this(sequenceVisits, delay, idle);
        this.avgOccupationLeg = avgOccupationLeg;
    }

    public static void reset() {
    }

    public Visit() {
        this.delay = Integer.MAX_VALUE;
        this.idle = Integer.MAX_VALUE;
        this.avgOccupationLeg = Double.MIN_VALUE;
        this.passengers = new HashSet<>();
        this.requests = new HashSet<>();
    }

    /**
     * Get a list of users and return a list of pickup and delivery ids.
     * E.g.: [u1,u2,u3,u4] => [1,1,2,2,3,3,4,4]
     *
     * @param passengers List of users
     * @return Sequence of user ids representing pickup and delivery nodes
     */
    public static List<Integer> getIdPairListFromUsers(Set<User> passengers) {

        /* Create a sequenceVisits of PK and DL points given a list of users. */
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

    public Set<User> getPassengers() {
        return passengers;
    }

    public void setPassengers(Set<User> passengers) {
        this.passengers = passengers;
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
        if (this.vehicle == null){
            return "DUMMY VISIT";
        }
        Node current = this.vehicle.getLastVisitedNode();
        int load = this.vehicle.getCurrentLoad();
        // Node strings
        List<String> nodes = new ArrayList<>();
        int dep = this.vehicle.getLastVisitedNode().getDeparture();

        nodes.add(
                String.format(
                        "%s[%2d] [P=%2d(%2d), R=%2d(%2d)]",
                        vehicle,
                        load,
                        passengers.size(),
                        passengers.stream().collect(Collectors.summingInt(p->p.getNumPassengers())),
                        requests.size(),
                        requests.stream().collect(Collectors.summingInt(p->p.getNumPassengers()))
                )
        );

        //Current node info
        String n = String.format(
                "%s %s <%4s | %4s>",
                (this.vehicle.isRebalancing()?"###":"---"),
                current,
                String.valueOf(current.getEarliest()),
                String.valueOf(dep)
        );
        nodes.add(n);

        // Visits
        List<Node> sequence;
        if (getTargetNode()!=null){
            sequence = new LinkedList<>();
            sequence.add(getTargetNode());
        }else{
            sequence = this.getSequenceVisits();
        }

        for (int i = 0; i < sequence.size(); i++) {
            Node next = sequence.get(i);
            int dist = Dao.getInstance().getDistSec(current, next);
            dep += dist;
            dep = Math.max(dep, current.getEarliest());
            current = next;
            load += current.getLoad();
            nodes.add(String.format("--> {%4s} --> %s[%2d] <%4s | %4s>", String.valueOf(dist), current, load, current.getEarliest(), String.valueOf(dep))); // config.Config.sec2TStamp(sequenceArrivals.get(i))));

        }
        return String.format("%s (delay: %5d - occ.: %5.2f)", String.join(" ", nodes), delay, avgOccupationLeg);
    }



    /*
    This method is called when a visit is chosen to be the best match for a vehicle.
    The vehicle is updated with the information of a visit.

     */

    public String getInfo() {
        return "  - Users: " + passengers + requests +
                " - Avg. Occupation: " + avgOccupationLeg +
                " - Visits: " + sequenceVisits +
                " - Delay: " + delay +
                " - Idle: " + idle +
                " - Vehicle: " + vehicle.getInfo();
    }

    /**
     * Loop sequence of nodes and check if vehicle load and arrival constraints are satisfied.
     * @return true, only if visit is feasible
     */
    public boolean isFeasible() {

        int[] cumulativeLegPK = new int[]{
                this.getVehicle().getDepartureCurrent(),
                this.getVehicle().getCurrentLoad(),
                0};

        Node currentPK = this.getVehicle().getLastVisitedNode();

        for (Node nextNode:this.getSequenceVisits()) {
            if (!this.getVehicle().isValidLeg(currentPK, nextNode, cumulativeLegPK)) {
                return false;
            }
            currentPK = nextNode;
        }
        return true;
    }


    /**
     * Get arrival time at first node in sequence of visits
     *
     * @return Arrival time
     */
    public int getArrivalTimeAtNext() {
        return this.vehicle.getDepartureCurrent() +
                Dao.getInstance().getDistSec(this.vehicle.getLastVisitedNode(),
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

    public Set<User> getRequests() {
        return this.requests;
    }

    public void setRequests(Set<User> requests) {
        this.requests = requests;
    }
}