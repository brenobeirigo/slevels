package model;

import model.node.Node;

import java.util.ArrayList;
import java.util.List;

public class Visit implements Comparable<Visit> {

    private int arrival;
    private List<User> listUsers;
    private List<Node> sequenceVisits;
    private List<Integer> sequenceArrivals;
    private Vehicle vehicle;
    private List<Integer> sequenceLoads;

    // Delays and idle times
    private int delay, idle;

    public Visit(int arrival, List<User> listUsers, List<Node> sequenceArrivals, int delay, Vehicle vehicle, int idle) {
        this.arrival = arrival;
        this.listUsers = listUsers;
        this.sequenceVisits = sequenceArrivals;
        this.delay = delay;
        this.vehicle = vehicle;
        this.idle = idle;
    }

    public Visit(List<Node> sequenceVisits, int delay, List<Integer> sequenceArrivals, int idle, List<Integer> sequenceLoads) {
        this.arrival = 0;
        this.listUsers = new ArrayList<>();
        this.sequenceVisits = sequenceVisits;
        this.delay = delay;
        this.sequenceArrivals = sequenceArrivals;
        this.idle = idle;
        this.sequenceLoads = sequenceLoads;
    }

    public Visit() {
        this.arrival = 0;
        this.listUsers = null;
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
    public static List<Integer> getIdPairListFromUsers(List<User> passengers) {

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

    public List<User> getListUsers() {
        return listUsers;
    }

    public List<Node> getSequenceVisits() {
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

    @Override
    public String toString() {
        /*
        return "Model.Visit{" +
                "\narrival=" + arrival +
                "\nlistUsers=" + listUsers +
                "\nsequenceVisits=" + sequenceVisits +
                "\nsequenceArrivals=" + Arrays.toString(sequenceArrivals) +
                "\nvehicle=" + vehicle +
                "\ndelay=" + delay +
                "\nidle=" + idle +
                '}';
        */
        //return String.format("%s | $s (delay: %d - idle: %d)", sequenceVisits,  delay, idle);
        List<String> nodes = new ArrayList<>();
        for (int i = 0; i < sequenceVisits.size(); i++) {
            nodes.add(String.format("%s(%s)",
                    sequenceVisits.get(i),
                    sequenceArrivals.get(i))); // config.Config.sec2TStamp(sequenceArrivals.get(i))));
        }
        return String.format("(delay: %5s - idle: %5s) %s ", String.valueOf(delay), String.valueOf(idle), nodes);
    }

}

