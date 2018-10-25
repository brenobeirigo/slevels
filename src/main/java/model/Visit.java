package model;

import model.node.Node;

import java.util.*;

public class Visit implements Comparable<Visit> {
    public static int visitCount = 0;
    private int arrival;
    private Set<User> setUsers;
    protected LinkedList<Node> sequenceVisits;
    private List<Integer> sequenceArrivals;
    protected Vehicle vehicle;
    private List<Integer> sequenceLoads;
    protected int departureVehicleCurrent; // Departure time of vehicle current node

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
        this.sequenceLoads = sequenceLoads;
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

    public int compareTo(Visit v) {
        return this.getDelay() - v.getDelay();
    }

    public List<Integer> getSequenceArrivals() {
        return sequenceArrivals;
    }

    public void setSetUsers(Set<User> setUsers) {
        this.setUsers = setUsers;
    }

    public void setSequenceVisits(LinkedList<Node> sequenceVisits) {
        this.sequenceVisits = sequenceVisits;
    }

    @Override
    public String toString() {
        /*
        return "Model.Visit{" +
                "\narrival=" + arrival +
                "\nsetUsers=" + setUsers +
                "\nsequenceVisits=" + sequenceVisits +
                "\nsequenceArrivals=" + Arrays.toString(sequenceArrivals) +
                "\nvehicle=" + vehicle +
                "\ndelay=" + delay +
                "\nidle=" + idle +
                '}';
        */
        //return String.format("%s | $s (delay: %d - idle: %d)", sequenceVisits,  delay, idle);
        List<String> nodes = new ArrayList<>();
        if (getSequenceVisits() != null) {
            for (int i = 0; i < getSequenceVisits().size(); i++) {
                /*
                nodes.add(String.format("%s(%s)",
                        getSequenceVisits().get(i),
                        getSequenceArrivals().get(i))); // config.Config.sec2TStamp(sequenceArrivals.get(i))));
                */
                nodes.add(String.format("%s",
                        getSequenceVisits().get(i))); // config.Config.sec2TStamp(sequenceArrivals.get(i))));
            }
        }
        return String.format("(delay: %5s - idle: %5s) %s ", String.valueOf(delay), String.valueOf(idle), nodes);
    }

    public void setSequenceArrivals(List<Integer> sequenceArrivals) {
        this.sequenceArrivals = sequenceArrivals;
    }

    public String getInfo() {
        return "(" + departureVehicleCurrent +
                ") Users: " + setUsers +
                " - Visits: " + sequenceVisits +
                " - Arrivals: " + sequenceArrivals +
                " - Loads: " + sequenceLoads +
                " - Delay: " + delay +
                " - Idle: " + idle +
                " - Vehicle: " + vehicle.get_info() +
                '}';
    }

    public int getDepartureVehicleCurrent() {
        return departureVehicleCurrent;
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