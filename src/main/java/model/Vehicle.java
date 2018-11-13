package model;

import dao.Dao;
import model.node.*;
import simulation.Method;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static config.Config.*;

public class Vehicle implements Comparable<Vehicle> {
    public static int count = Node.MAX_NUMBER_NODES * 2;
    private Node origin;
    private int id;
    private int capacity;
    private Integer load;
    private Set<User> users;
    private Set<User> enroute;
    private List<Node> journey;
    private Visit visit;
    private Node currentNode;
    private List<User> servicedUsers;
    private int departureCurrent; // Time vehicle leaves current node

    public static void reset() {
        count = Node.MAX_NUMBER_NODES * 2;
    }

    public Vehicle(int size, int id_network, double lat, double lon) {
        ++count;
        this.id = count;
        this.origin = new NodeOrigin(count, id_network, lat, lon, 0);
        this.currentNode = this.origin;
        this.capacity = size;
        this.enroute = new HashSet<>();
        this.servicedUsers = new ArrayList<>();
        this.users = new HashSet<>();
        this.journey = new ArrayList<>();
        this.journey.add(this.currentNode);
        this.load = 0;
    }

    public Vehicle(int size, int id_network) {
        ++count;
        this.id = count;
        this.origin = new NodeOrigin(count, id_network, 0);
        this.currentNode = this.origin;
        this.capacity = size;
        this.enroute = new HashSet<>();
        this.servicedUsers = new ArrayList<>();
        this.users = new HashSet<>();
        this.journey = new ArrayList<>();
        this.journey.add(this.currentNode);
        this.load = 0;
    }

    public int getDepartureCurrent() {
        return departureCurrent;
    }

    public void setDepartureCurrent(int departureCurrent) {
        this.departureCurrent = departureCurrent;
    }

    public Node getOrigin() {
        return origin;
    }

    public int getId() {
        return id;
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

    public Set<User> getUsers() {
        return users;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setUsers(Set<User> users) {
        this.users = users;
    }

    /**
     * Sort vehicles according to load (lower first).
     *
     * @param that
     * @return
     */
    @Override
    public int compareTo(Vehicle that) {
        if (this.load < that.load) return BEFORE;
        if (this.load > that.load) return AFTER;
        return EQUAL;
    }

    /**
     * Get set of users serviced until current time.
     * Loop visit sequence and check if:
     * 1 - node arrival is lower than pk time, AND
     * 2 - node is DP
     *
     * If so, than costumer was serviced.
     *
     * @param currentTime current time
     * @return Set of users serviced until current time
     */
    public Set<User> getServicedUsersUntil(int currentTime) {

        // If vehicle has no customers to service
        if (visit == null ||
                visit.getSequenceVisits() == null ||
                visit.getSequenceVisits().isEmpty()) {
            return null;
        }

        // Serviced users in current execution round [last current time, current time]
        Set<User> serviced = new HashSet<>();

        int nRemoved = 0;
        for (Node nextNode : visit.getSequenceVisits()) {

            // Get arrival time at first node of visit sequenceVisits
            int arrivalNext = this.departureCurrent + Dao.getInstance().getDistSec(currentNode, nextNode);

            // If current time is lower than arrival at next node, the node was not visited yet
            if (arrivalNext > currentTime) {
                break;
            }

            // Node will be removed from sequence
            nRemoved++;

            // Set arrival time for last node
            nextNode.setArrival(arrivalNext);

            // Update current load in vehicle
            this.load += nextNode.getLoad();

            // Update data of current node
            currentNode = nextNode;
            this.journey.add(currentNode);
            this.departureCurrent = arrivalNext;

            // If DP node is visited, it means request is finished
            if (currentNode instanceof NodeDP) {

                int tripId = currentNode.getTripId();

                /*####################### Update list of serviced users #############################################*/
                // Eliminate serviced user from visit
                this.visit.getSetUsers().remove(User.mapOfUsers.get(tripId));

                // Add serviced user to vehicle
                this.servicedUsers.add(User.mapOfUsers.get(tripId));

                // Update serviced users
                serviced.add(User.mapOfUsers.get(tripId));

                /*###################################################################################################*/

                // Set arrival time at DP node for request
                User.status[tripId][1] = arrivalNext;

                // Save DP delay
                User.status[tripId][3] = currentNode.getDelay();

                // Node visited is removed from vehicle
                this.users.remove(User.mapOfUsers.get(tripId));

                // User is locked in vehicle (cannot change to other)
                this.enroute.remove(User.mapOfUsers.get(tripId));

                // TODO improve representation
                // Locked node can be removed
                //this.enrouteNode.remove(currentNode);

                // Save total ride time in vehicle
                int rideTime = User.mapOfUsers.get(tripId).getNodeDp().getArrival() - User.mapOfUsers.get(tripId).getNodePk().getArrival();

                // Save ride trip ride time
                User.mapOfUsers.get(tripId).setRideTime(rideTime);

            } else if (currentNode instanceof NodePK) {
                int tripId = currentNode.getTripId();

                // Set arrival time at PK node for request
                User.status[tripId][0] = arrivalNext;

                // Save PK delay
                User.status[tripId][2] = currentNode.getDelay();

                // User is locked in vehicle (cannot change to other)
                this.enroute.add(User.mapOfUsers.get(tripId));

                // TODO: improve this representation
                // This nodes must be visited by vehicle
                //this.enrouteNode.add(User.mapOfUsers.get(tripId).getNodeDp());
            }
        }

        // Remove first "nRemoved" elements
        for (int j = 0; j < nRemoved; j++) {
            visit.getSequenceVisits().removeFirst();
            //visit.getSequenceArrivals().remove(0);
        }

        // If all nodes were visited
        if (this.users.isEmpty()) {

            // Signalize that vehicle is stopped at last visited node in a stop point
            this.currentNode = new NodeStop(this.currentNode, this.id);

            // Update vehicle current time
            this.departureCurrent = currentTime;

            // Model.Vehicle has no routing plan
            this.setVisit(null);

        } else {
            // Update visit current time
            this.visit.setDepartureVehicleCurrent(departureCurrent);
        }

        // Serviced users in an execution round
        return serviced;
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

    /**
     *
     * @return Journey
     */
    public String getJourneyInfo() {

        StringBuilder str = new StringBuilder();

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

    public String getInfo() {

        return String.format("%s(%2s/%d) - %s(%s) - Users: %30s -> Journey: %s - Attended: %s",
                this,
                (!users.isEmpty() ? String.valueOf(load) : "-"),
                capacity,
                currentNode,
                currentNode.getArrival(),
                users,
                (this.visit != null ? visit : "---"),
                servicedUsers);
    }

    /**
     * Get best insertion of candidate user in vehicle at current time.
     *
     * @param candidateUser
     * @param currentTime
     * @return Best visit or null
     */
    public Visit getBestInsertion(User candidateUser,
                                  int currentTime) {

        // Candidate sequence that will be formed
        List<Node> visitSequence;

        // First best including new users is initiated blank
        Visit bestVisit = new Visit();

        // If vehicle has NO visits, place first user in candidate sequence
        if (this.getVisit() == null ||
                this.getVisit().getSequenceVisits() == null ||
                this.getVisit().getSequenceVisits().isEmpty()) {

            // Start empty candidate sequence
            visitSequence = new ArrayList<>();

            // Best visit is empty
            //bestVisit = new Visit();

        } else {

            // Copy elements in vehicle visit to candidate sequence
            visitSequence = new ArrayList<>(this.getVisit().getSequenceVisits());

            // Start best visit with vehicle visit
            //bestVisit = this.getVisit();
        }

        // Loop all insertion positions
        for (int pkPos = 0; pkPos <= visitSequence.size(); pkPos++) {
            for (int dpPos = pkPos; dpPos <= visitSequence.size(); dpPos++) {

                // Try to get a visit by inserting candidate user in sequence
                Visit candidateVisit = Method.getVisitByInsertPosition(candidateUser,
                        visitSequence,
                        this,
                        pkPos,
                        dpPos,
                        currentTime);

                //System.out.println(String.format("Insert %d and %d -%s %s (%s)", pkPos, dpPos, candidateUser, visitSequence, candidateVisit));

                // Update best visit
                if (candidateVisit != null && candidateVisit.compareTo(bestVisit) < 0) {
                    bestVisit = candidateVisit;
                }
            }
        }

        // If best visit was not found
        if (bestVisit.getSequenceVisits() == null) {
            return null;
        }

        // Assign vehicle to best
        bestVisit.setVehicle(this);

        // Update user set of best visit
        Set<User> candidateRequests = new HashSet<>(this.getUsers());
        candidateRequests.add(candidateUser);
        bestVisit.setSetUsers(candidateRequests);

        return bestVisit;
    }

    @Override
    public String toString() {
        return String.format("%6s", "V" + String.valueOf(id - Node.MAX_NUMBER_NODES * 2));
    }
}