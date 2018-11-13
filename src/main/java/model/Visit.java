package model;

import model.node.Node;

import java.util.*;

import static config.Config.*;

public class Visit implements Comparable<Visit> {
    private int arrival;
    private Set<User> setUsers;
    protected LinkedList<Node> sequenceVisits;
    private List<Integer> sequenceArrivals;
    protected Vehicle vehicle;
    protected int departureVehicleCurrent; // Departure time of vehicle current node
    protected double avgOccupationLeg;

    // Delays and idle times
    protected int delay, idle;

    public Visit(int arrival, Set<User> setUsers, LinkedList<Node> sequenceArrivals, int delay, Vehicle vehicle, int idle) {
        this.arrival = arrival;
        this.setUsers = setUsers;
        this.setSequenceVisits(sequenceArrivals);
        this.delay = delay;
        this.vehicle = vehicle;
        this.idle = idle;
        //visitCount++;
    }

    public Visit(LinkedList<Node> sequenceVisits, int delay, List<Integer> sequenceArrivals, int idle, List<Integer> sequenceLoads) {
        this.arrival = 0;
        this.setUsers = new HashSet<>();
        this.setSequenceVisits(sequenceVisits);
        this.delay = delay;
        this.setSequenceArrivals(sequenceArrivals);
        this.idle = idle;
        //visitCount++;
    }

    public Visit(LinkedList<Node> sequenceVisits, int delay, int idle) {
        this.arrival = 0;
        this.setUsers = new HashSet<>();
        this.setSequenceVisits(sequenceVisits);
        this.delay = delay;
        this.idle = idle;
        //visitCount++;
    }

    public Visit(LinkedList<Node> sequenceVisits, int delay, int idle, int departureVehicleCurrent) {
        this.arrival = 0;
        this.setUsers = new HashSet<>();
        this.setSequenceVisits(sequenceVisits);
        this.delay = delay;
        this.idle = idle;
        this.departureVehicleCurrent = departureVehicleCurrent;
        //visitCount++;
    }

    public Visit(LinkedList<Node> sequenceVisits, int delay, int idle, double avgOccupationLeg, int departureVehicleCurrent) {
        this.arrival = 0;
        this.setUsers = new HashSet<>();
        this.setSequenceVisits(sequenceVisits);
        this.delay = delay;
        this.idle = idle;
        this.departureVehicleCurrent = departureVehicleCurrent;
        //this.loadPerTime = totalLoad;
        this.avgOccupationLeg = avgOccupationLeg;
        //visitCount++;
    }

    public Visit(LinkedList<Node> sequenceVisits, LinkedList<Integer> sequenceArrivals, int delay, int idle) {
        this.arrival = 0;
        this.setUsers = new HashSet<>();
        this.sequenceVisits = sequenceVisits;
        this.sequenceArrivals = sequenceArrivals;
        this.delay = delay;
        this.idle = idle;
        //visitCount++;
    }

    public Visit(LinkedList<Node> sequenceVisits, LinkedList<Integer> sequenceArrivals, int delay, int idle, int departureVehicleCurrent) {
        this.arrival = 0;
        this.setUsers = new HashSet<>();
        this.sequenceVisits = sequenceVisits;
        this.sequenceArrivals = sequenceArrivals;
        this.delay = delay;
        this.idle = idle;
        this.departureVehicleCurrent = departureVehicleCurrent;
        //visitCount++;
    }

    public Visit() {
        this.arrival = 0;
        this.setUsers = null;
        this.sequenceVisits = null;
        this.delay = Integer.MAX_VALUE;
        this.vehicle = null;
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

    /**
     * @param that is a non-null Visit.
     */
    @Override
    public int compareTo(Visit that) {

        // Objects are equal
        //if (this == that) return EQUAL;

        //"Empty" visit
        if (that.getSequenceVisits() == null) return BEFORE;

        // Privilege trip size
        //if (this.getSequenceVisits().size() > that.getSequenceVisits().size()) return BEFORE;
        //if (this.getSequenceVisits().size() < that.getSequenceVisits().size()) return AFTER;

        // Compare average occupation per leg
        //if (this.avgOccupationLeg > that.avgOccupationLeg) return BEFORE;
        //if (this.avgOccupationLeg < that.avgOccupationLeg) return AFTER;

        //primitive numbers follow this form
        if (this.delay < that.delay) return BEFORE;
        if (this.delay > that.delay) return AFTER;

        return EQUAL;
    }

    public void setSetUsers(Set<User> setUsers) {
        this.setUsers = setUsers;
    }

    public void setSequenceVisits(LinkedList<Node> sequenceVisits) {
        this.sequenceVisits = sequenceVisits;
    }

    public void setSequenceArrivals(List<Integer> sequenceArrivals) {
        this.sequenceArrivals = sequenceArrivals;
    }

    @Override
    public String toString() {

        // Node strings
        List<String> nodes = new ArrayList<>();

        // If there is a sequence of visits
        if (getSequenceVisits() != null) {
            for (int i = 0; i < getSequenceVisits().size(); i++) {
                nodes.add(String.format("%s", getSequenceVisits().get(i))); // config.Config.sec2TStamp(sequenceArrivals.get(i))));
            }
        }
        return String.format("(delay: %5s - Occupation: %5s) %s ", String.valueOf(delay), String.valueOf(avgOccupationLeg), nodes);
    }

    public String getInfo() {
        return "(" + departureVehicleCurrent +
                ") Users: " + setUsers +
                " - Avg. Occupation: " + avgOccupationLeg +
                " - Visits: " + sequenceVisits +
                " - Arrivals: " + sequenceArrivals +
                " - Delay: " + delay +
                " - Idle: " + idle +
                " - Vehicle: " + vehicle.getInfo();
    }

    public void setDepartureVehicleCurrent(int departureVehicleCurrent) {
        this.departureVehicleCurrent = departureVehicleCurrent;
    }

    /*
    Update vehicle with data from current visit.
     */
    public void setup() {

        // Add visit to vehicle (circular)
        this.vehicle.setVisit(this);

        // Vehicle set of users
        this.vehicle.setUsers(this.getSetUsers());

        // Update departure time of vehicle from vehicle current node for new visit
        this.vehicle.setDepartureCurrent(this.departureVehicleCurrent);
    }
}