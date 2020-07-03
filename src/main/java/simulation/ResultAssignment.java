package simulation;

import config.Qos;
import model.User;
import model.Vehicle;
import model.Visit;

import java.awt.datatransfer.SystemFlavorMap;
import java.util.*;
import java.util.stream.Collectors;

public class ResultAssignment {

    public Set<User> requestsServicedLevelAchieved;
    public Map<Qos, Integer> unmetServiceLevelClass;
    public Map<Qos, Integer> totalServiceLevelClass;
    // Set of requests scheduled to vehicles
    private Set<User> requestsOK;

    // Unassigned requests
    protected Set<User> requestsUnassigned;

    // Set of users that were displaced from their current rides (is in unassgigned)
    protected Set<User> requestsDisplaced;

    // Set of vehicles assigned to visits
    private Set<Vehicle> vehiclesOK;

    // Set of vehicles that interrupted routes and parked
    protected Set<Vehicle> vehiclesDisrupted;

    // New vehicle is added in list
    protected Set<Vehicle> vehiclesHired;

    // Users who were picked up by hired vehicle
    protected List<User> roundPrivateRides;

    // Set of visits chosen in round
    private Set<Visit> visitsOK;

    public ResultAssignment() {

        // Set of requests scheduled to vehicles
        requestsOK = new HashSet<>();
        requestsDisplaced = new HashSet<>();
        requestsUnassigned = new HashSet<>();
        requestsServicedLevelAchieved = new HashSet<>();
        unmetServiceLevelClass = new HashMap<>();
        totalServiceLevelClass = new HashMap<>();

        // Set of vehicles assigned to visits
        vehiclesOK = new HashSet<>();
        vehiclesHired = new HashSet<>();
        roundPrivateRides = new ArrayList<>();

        // Set of vehicles that interrupted routes and parked
        vehiclesDisrupted = new HashSet<>();

        // Set of visits chosen in round
        visitsOK = new HashSet<>();
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // PRINTS //////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void printRoundResult() {

        System.out.println(String.format("\n\n# Assigned vehicles  (%d)  = %s", vehiclesOK.size(), vehiclesOK));
        System.out.println(String.format("# Unassigned users   (%d)  = %s", requestsUnassigned.size(), requestsUnassigned));
        System.out.println(String.format("# Displaced users    (%d)  = %s", requestsDisplaced.size(), requestsDisplaced));
        System.out.println(String.format("# Vehicles disrupted (%d)  = %s", vehiclesDisrupted.size(), vehiclesDisrupted));
        System.out.println("# Class service quality    = " + totalServiceLevelClass.keySet().stream().map(qos -> String.format("%s = %d/%d (%.2f)", qos.id, unmetServiceLevelClass.get(qos), totalServiceLevelClass.get(qos), servicedFirstTier(qos))).collect(Collectors.joining(" - ")));
        for (Vehicle vehicle : vehiclesDisrupted) {
            System.out.println("#### Disrupted = " + vehicle.getVisit());
        }
    }

    double servicedFirstTier(Qos qos) {
        return (double)(totalServiceLevelClass.get(qos) - unmetServiceLevelClass.get(qos)) / totalServiceLevelClass.get(qos);
    }

    public void addHiredVehicle(Vehicle vehicle) {
        this.vehiclesHired.add(vehicle);
    }

    public void addVisit(Visit visit) {

        // Update requests, vehicles, and visitsOK solution
        this.requestsOK.addAll(visit.getRequests());
        this.vehiclesOK.add(visit.getVehicle());
        this.visitsOK.add(visit);


        // Remove vehicle and users matched from graph
        for (User user : visit.getRequests()) {

            // If user changes visit
            if (user.getCurrentVisit() != null && visit != user.getCurrentVisit()) {

                // Visit of vehicle formerly carrying request will be changed
                this.vehiclesDisrupted.add(user.getCurrentVisit().getVehicle());
            }
        }

        System.out.println(String.format("Setting up %s - User: %s", visit, visit.getUserInfo()));
        printCurrentStatus();
    }

    private void printCurrentStatus() {
        System.out.println(String.format("# Requests (%d): %s", this.requestsOK.size(), this.requestsOK));
        System.out.println(String.format("# Vehicles (%d): %s", this.vehiclesOK.size(), this.vehiclesOK));
        System.out.println(String.format("# Disrupted (%d): %s", this.vehiclesDisrupted.size(), this.vehiclesDisrupted));
        System.out.println(String.format("# Visits: %d", this.visitsOK.size()));
        System.out.println(String.format("# QoS unmet"));
    }

    private void unassignedUsersCannotBeServiced() {
        // All vehicles that lost requests rebalance to closest node (middle or next target)
        for (Vehicle vehicleDisrupted : this.vehiclesDisrupted) {

            assert vehicleDisrupted.getVisit().getPassengers().isEmpty() : "Interrupted vehicle had passenger!" + vehicleDisrupted.getVisit().getUserInfo() + " - Visit:" + vehicleDisrupted.getVisit();
            assert disruptedUsersAreServicedByDifferentVehicles(vehicleDisrupted) : "Disrupted users (unmatched) are not inserted in different vehicles.";
            /*for (User u : requestsFormerlyServicedByDisruptedVehicle) {
                assert u.getCurrentVisit() != null && u.getCurrentVisit() != vehicleDisrupted.getVisit() : String.format("User %s in vehicle %s is still associated with the vehicle", u, vehicleDisrupted);
            }*/

            /*for (User u : requestsFormerlyServicedByDisruptedVehicle) {
                assert u.getCurrentVisit() != null && u.getCurrentVisit() != vehicleDisrupted.getVisit() : String.format("User %s in vehicle %s is still associated with the vehicle", u, vehicleDisrupted);
            }*/
        }
    }

    public Set<User> getRequestsOK() {
        return requestsOK;
    }

    public void setRequestsOK(Set<User> requestsOK) {
        this.requestsOK = requestsOK;
    }

    public Set<Vehicle> getVehiclesOK() {
        return vehiclesOK;
    }

    public void setVehiclesOK(Set<Vehicle> vehiclesOK) {
        this.vehiclesOK = vehiclesOK;
    }

    public Set<Visit> getVisitsOK() {
        return visitsOK;
    }

    public void setVisitsOK(Set<Visit> visitsOK) {
        this.visitsOK = visitsOK;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // ASSERTIONS //////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private boolean disruptedUsersAreServicedByDifferentVehicles(Vehicle vehicleDisrupted) {

        Set<User> requestsFormerlyServicedByDisruptedVehicle = vehicleDisrupted.getVisit().getRequests();

        Set<User> requestsServicedByDifferentVehicles = requestsFormerlyServicedByDisruptedVehicle.stream()
                .filter(user -> user.getCurrentVisit().getVehicle() != vehicleDisrupted)
                .collect(Collectors.toSet());

        // Are all requests serviced in another visits?
        if (requestsServicedByDifferentVehicles.size() != requestsFormerlyServicedByDisruptedVehicle.size()) {
            requestsFormerlyServicedByDisruptedVehicle.removeAll(requestsServicedByDifferentVehicles);
            System.out.println(String.format(" - Users %s from vehicle %s were left unmatched", requestsFormerlyServicedByDisruptedVehicle, vehicleDisrupted));
            return false;
        }

        return true;
    }
}
