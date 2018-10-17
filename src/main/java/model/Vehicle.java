package model;

import dao.Dao;
import helper.MethodHelper;
import model.node.*;

import java.util.*;

public class Vehicle implements Comparable<Vehicle> {
    private static int count = Node.MAX_NUMBER_NODES * 2;
    private Node origin;
    private int id;
    private int capacity;
    private Integer load;
    private List<User> listUsers;
    private List<Node> journey;
    private Visit visit;
    private Node currentNode;
    private int totalDelay;
    private int totalWaiting;
    private int current_time;
    private List<User> servicedUsers;


    public Vehicle(int size, int id_network, double lat, double lon) {
        ++count;
        this.id = count;
        this.origin = new NodeOrigin(count, id_network, lat, lon, 0);
        this.currentNode = this.origin;
        this.capacity = size;
        this.servicedUsers = new ArrayList<>();
        this.listUsers = new LinkedList<>();
        this.journey = new ArrayList<>();
        this.journey.add(this.currentNode);
        this.load = 0;
    }

    public int getLoad() {
        return load;
    }

    public Visit getVisit() {
        return visit;
    }

    public void setVisit(Visit visit) {
        this.visit = visit;
    }

    public Node getCurrentNode() {
        return currentNode;
    }

    public List<User> getListUsers() {
        return listUsers;
    }

    public int getCapacity() {
        return capacity;
    }

    @Override
    public int compareTo(Vehicle o) {
        return this.currentNode.getArrival() - o.currentNode.getArrival();
    }

    /**
     * Get set of users serviced until current time.
     * Loop visit sequence and check if:
     * 1 - node arrival is lower than pk time, AND
     * 2 - node is DP
     * <p>
     * If so, than costumer was serviced.
     *
     * @param currentTime current time
     * @return Set of users serviced until current time
     */
    public Set<User> getServicedUsersUntil(int currentTime) {

        //TODO jump to next node and update arrival time over current? Prevents changing next
        // Arrival time at the node to be visited after sequence
        //int arrival_first = visit.getSequenceArrivals().get(0);

        // Arrival time at the currentNode node
        //int arrival_current = this.getCurrentNode().getArrival();


        // if arrival_current <= currentNode <= arrival_first
        // Jump to first node in sequence
        /*
        if (currentNode >= arrival_current && currentNode <= arrival_first) {
                // Current time is the arrival time of the first node in the sequence
                currentNode = arrival_first;
        }
        */

        // If vehicle has no customers to service
        if (visit == null || visit.getSequenceVisits().isEmpty()) {
            return null;
        }

        // Find last visited node given current time
        int start = MethodHelper.bisect_right(this.visit.getSequenceArrivals(), currentTime);

        // current_t < first element of sequenceVisits
        // Model.Vehicle is still between current_node and first element
        if (start == 0) {
            return null;
        }

        // Serviced users in current execution round [last current time, current time]
        Set<User> serviced = new HashSet<>();

        // Remove first "start" nodes
        for (int i = 0; i < start; i++) {

            this.load = this.load + this.visit.getSequenceVisits().get(0).getLoad();

            // Get first node of visit sequenceVisits
            currentNode = this.visit.getSequenceVisits().remove(0);
            this.journey.add(currentNode);
            int arrival_last = this.visit.getSequenceArrivals().remove(0);

            // Set arrival time for last node
            currentNode.setArrival(arrival_last);

            // If DP node is visited, it means request is finished
            if (currentNode instanceof NodeDP) {

                int tripId = currentNode.getTripId();

                /*####################### Update list of serviced users #############################################*/
                // Eliminate serviced user from visit
                this.visit.getListUsers().remove(User.all_users.get(tripId));

                // Add serviced user to vehicle
                this.servicedUsers.add(User.all_users.get(tripId));

                // Update serviced users
                serviced.add(User.all_users.get(tripId));

                /*###################################################################################################*/

                // Set arrival time at DP node for request
                User.status[tripId][1] = arrival_last;

                // Save DP delay
                User.status[tripId][3] = currentNode.getDelay();

                // Model.Node visited is removed from vehicle
                this.listUsers.remove(User.all_users.get(tripId));

                // Save total ride time in vehicle
                int rideTime = User.all_users.get(tripId).getNodeDp().getArrival() - User.all_users.get(tripId).getNodePk().getArrival();

                // Save ride trip ride time
                User.all_users.get(tripId).setRideTime(rideTime);

            } else if (currentNode instanceof NodePK) {
                int tripId = currentNode.getTripId();

                // Set arrival time at PK node for request
                User.status[tripId][0] = arrival_last;

                // Save PK delay
                User.status[tripId][2] = currentNode.getDelay();

            }
        }


        // If all nodes were visited
        if (this.listUsers.isEmpty()) {

            // Signalize that vehicle is stopped at last visited node in a stop point
            this.currentNode = new NodeStop(this.currentNode, this.id);

            // Model.Vehicle has no routing plan
            this.setVisit(null);
        }

        // Serviced users in an execution round
        return serviced;
    }

    @Override
    public String toString() {
        return String.format("%6s", "V" + String.valueOf(id - Node.MAX_NUMBER_NODES * 2));
    }

    public String get_info() {
        return String.format("%s(%2s/%d) - %s(%s) - Users: %30s -> Journey: %s - Attended: %s",
                this,
                (!listUsers.isEmpty() ? String.valueOf(load) : "-"),
                capacity,
                currentNode,
                currentNode.getArrival(),
                listUsers,
                (this.visit != null ? visit : "---"),
                servicedUsers);
    }

    public List<Node> getJourney() {
        return this.journey;
    }

    public String getStats() {

        // Initial time at origin
        int startTime = origin.getArrival();

        // Last arrival
        int endTime = journey.get(journey.size() - 1).getArrival();

        // Total operating time
        int operatingTW = endTime - startTime;

        // Delays
        int delayPK = 0, delayDP = 0;

        for (User u : servicedUsers) {
            delayPK += u.getNodePk().getDelay();
            delayDP += u.getNodeDp().getDelay();
        }

        // Operating time
        int operatingTime = 0;
        for (int i = 0; i < journey.size() - 1; i++) {
            int fromId = journey.get(i).getNetworkId();
            int toId = journey.get(i + 1).getNetworkId();
            int tripDuration = Dao.getInstance().getDistSec(fromId, toId);
            operatingTime += tripDuration;
        }

        // Set up total waiting time
        int totalWaiting = operatingTW - operatingTime;

        return String.format("###### %4s [%4s,%4s] => %4s (work) + %4s (wait) = %4s (%.2f%%) #### PK:%6s  DP:%6s",
                String.valueOf(this),
                String.valueOf(startTime),
                String.valueOf(endTime),
                String.valueOf(operatingTime),
                String.valueOf(totalWaiting),
                String.valueOf(operatingTW),
                (double) operatingTime * 100 / operatingTW,
                String.valueOf(delayPK),
                String.valueOf(delayDP));
    }


    public String getJourneyInfo() {

        StringBuffer str = new StringBuffer();

        str.append("\n########################################################################################");
        str.append("\n" + getStats());
        str.append("\n########################################################################################");

        for (int i = 0; i < journey.size() - 1; i++) {

            int fromId = journey.get(i).getNetworkId();
            int toId = journey.get(i + 1).getNetworkId();
            int dist = Dao.getInstance().getDistSec(fromId, toId);
            int waiting = journey.get(i + 1).getArrival() - dist - journey.get(i).getArrival();

            str.append("\n" + journey.get(i).getInfo());
            str.append(String.format("\nTravel time: %7s", String.valueOf(dist)));
            str.append(String.format("\n    Waiting: %7s", String.valueOf(waiting)));
        }

        // Print last node
        str.append("\n" + journey.get(journey.size() - 1).getInfo());

        return str.toString();
    }


}