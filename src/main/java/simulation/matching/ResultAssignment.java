package simulation.matching;

import config.Config;
import config.Qos;
import model.User;
import model.Vehicle;
import model.Visit;

import java.util.*;
import java.util.stream.Collectors;

public class ResultAssignment {

    public Set<User> requestsServicedLevelAchieved;
    public Map<Qos, Integer> unmetServiceLevelClass;
    public Map<Qos, Integer> nOfRequestsClass;
    public Map<Qos, Integer> rejectedServiceLevelClass;
    public Set<User> requestsServicedLevelNotAchieved;
    public Map<Qos, Double> violationCountClassServiceLevel;
    // Set of users that were displaced from their current rides (is in unassgigned)
    protected Set<User> requestsDisplaced;
    // Set of vehicles that interrupted routes and parked
    protected Set<Vehicle> vehiclesDisrupted;
    // Users who were picked up by hired vehicle
    protected List<User> roundPrivateRides;
    // Set of requests scheduled to vehicles
    protected Set<User> requestsOK;
    // Set of vehicles assigned to visits
    protected Set<Vehicle> vehiclesOK;
    // Set of visits chosen in round
    protected Set<Visit> visitsOK;
    // Unassigned requests
    private Set<User> requestsUnassigned;
    // New vehicle is added in list
    private Set<Vehicle> vehiclesHired;
    // Result refers to current simulation time
    private int currentTime;

    public ResultAssignment(int currentTime) {
        this.currentTime = currentTime;
        // Set of requests scheduled to vehicles
        requestsOK = new HashSet<>();
        requestsDisplaced = new HashSet<>();
        requestsUnassigned = new HashSet<>();
        requestsServicedLevelAchieved = new HashSet<>();
        requestsServicedLevelNotAchieved = new HashSet<>();

        // Class structures
        unmetServiceLevelClass = new HashMap<>();
        rejectedServiceLevelClass = new HashMap<>();
        nOfRequestsClass = new HashMap<>();
        violationCountClassServiceLevel = new HashMap<>();

        // Set of vehicles assigned to visits
        vehiclesOK = new HashSet<>();
        vehiclesHired = new HashSet<>();
        roundPrivateRides = new ArrayList<>();

        // Set of vehicles that interrupted routes and parked
        vehiclesDisrupted = new HashSet<>();

        // Set of visits chosen in round
        visitsOK = new HashSet<>();

        for (Qos qos : Config.getInstance().qosDic.values()) {
            unmetServiceLevelClass.put(qos, 0);
            nOfRequestsClass.put(qos, 0);
            rejectedServiceLevelClass.put(qos, 0);
            violationCountClassServiceLevel.put(qos, 0.0);
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // PRINTS //////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public Map<Qos, List<User>> getClassUnassignedMap() {

        Map<Qos, List<User>> classUnassigned = new HashMap<>();

        for (Qos qos : Config.getInstance().qosDic.values()) {
            classUnassigned.put(qos, new ArrayList<>());
        }

        for (User request : this.requestsUnassigned) {
            classUnassigned.get(request.qos).add(request);
        }

        return classUnassigned;
    }

    public Map<Qos, List<User>> getClassSecondTierMap() {

        Map<Qos, List<User>> classSecondTier = new HashMap<>();

        for (Qos qos : Config.getInstance().qosDic.values()) {
            classSecondTier.put(qos, new ArrayList<>());
        }

        for (User request : this.requestsServicedLevelNotAchieved) {
            classSecondTier.get(request.qos).add(request);
        }

        return classSecondTier;
    }

    public void printRoundResult() {
        System.out.println("######## Round current time: " + this.currentTime);
        System.out.printf("\n\n# Assigned vehicles  (%d)  = %s%n", vehiclesOK.size(), vehiclesOK);
        System.out.printf("# Unassigned users   (%d)  = %s%n", requestsUnassigned.size(), requestsUnassigned);
        System.out.printf("# Assigned users     (%d)  = %s%n", requestsOK.size(), requestsOK);
        System.out.printf("# Displaced users    (%d)  = %s%n", requestsDisplaced.size(), requestsDisplaced);
        System.out.printf("# Class service quality    = \n%s%n", overallServiceLevelDistribution());
        System.out.printf("# Vehicles disrupted (%d)  = %s%n", vehiclesDisrupted.size(), vehiclesDisrupted);
        System.out.printf("# Vehicles hired           = %s%n", getVehiclesHired().size());
        for (Vehicle vehicle : vehiclesDisrupted) {
            System.out.println("#### Disrupted = " + vehicle.getVisit());
        }
    }

    private String overallServiceLevelDistribution() {
        return nOfRequestsClass.keySet().stream().map(this::getConstraintCheck).collect(Collectors.joining("\n"));
    }

    private String getSummaryStats(Qos qos) {
        return String.format(
                "\n[%s] sl violation = %3d/ min=%3d (%.1f * %3d), met=%3d, unmet=%3d, unassigned=%3d",
                qos.id,
                unmetServiceLevelClass.get(qos) + rejectedServiceLevelClass.get(qos),
                (int) Math.ceil(qos.serviceRate * nOfRequestsClass.get(qos)),
                qos.serviceRate,
                nOfRequestsClass.get(qos),
                metServiceLevelClass(qos),
                unmetServiceLevelClass.get(qos),
                rejectedServiceLevelClass.get(qos));
    }

    private String getConstraintCheck(Qos qos) {
        return String.format(
                "[%s] = %3d (met) +  %.0f (violation) >= %3d (%.1f * %3d) - unmet=%3d, rejected=%3d",
                qos.id,
                metServiceLevelClass(qos),
                violationCountClassServiceLevel.get(qos),
                (int) Math.ceil(qos.serviceRate * nOfRequestsClass.get(qos)),
                qos.serviceRate,
                nOfRequestsClass.get(qos),
                unmetServiceLevelClass.get(qos),
                rejectedServiceLevelClass.get(qos));
    }

    int metServiceLevelClass(Qos qos) {
        return nOfRequestsClass.get(qos) - unmetServiceLevelClass.get(qos) - rejectedServiceLevelClass.get(qos);
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

        //System.out.println(String.format("Setting up %s - User: %s", visit, visit.getUserInfo()));
        //printCurrentStatus();
    }

    private void printCurrentStatus() {
        System.out.printf("# Requests (%d): %s%n", this.requestsOK.size(), this.requestsOK);
        System.out.printf("# Vehicles (%d): %s%n", this.vehiclesOK.size(), this.vehiclesOK);
        System.out.printf("# Disrupted (%d): %s%n", this.vehiclesDisrupted.size(), this.vehiclesDisrupted);
        System.out.printf("# Visits: %d%n", this.visitsOK.size());
        System.out.println("# QoS unmet");
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

    public List<User> getUnmetServiceLevelRequests(){
        return User.filterSecondTier(this.requestsOK);
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
            System.out.printf(" - Users %s from vehicle %s were left unmatched%n", requestsFormerlyServicedByDisruptedVehicle, vehicleDisrupted);
            return false;
        }

        return true;
    }

    public void showSecondTierAssignedUsers() {
        List<User> listAssigned = getSortedListOfAssignedUsers();
        for (User user : listAssigned) {
            if (!user.isFirstTier())
                System.out.println(user.getCurrentAssigmentInfo());
        }
    }

    public boolean assignedAndUnassignedAreDisjoint() {
        return Collections.disjoint(this.requestsOK, this.requestsUnassigned);

    }

    public void showFirstTierFromAssigned() {
        List<User> listAssigned = getSortedListOfAssignedUsers();
        for (User user : listAssigned) {
            if (user.isFirstTier())
                System.out.println(user.getCurrentAssigmentInfo());
        }
    }

    private List<User> getSortedListOfAssignedUsers() {
        List<User> listAssigned = new ArrayList<>(getRequestsOK());
        listAssigned.sort(Comparator.comparing(User::getPerformanceClass).thenComparing(User::getReqTime));
        return listAssigned;
    }

    protected void createFirstSecondTierListsFromServiced() {
        requestsServicedLevelAchieved.addAll(Visit.filterFirstTier(getVisitsOK()));
        requestsServicedLevelNotAchieved.addAll(Visit.filterSecondTier(getVisitsOK()));
    }

    public Set<User> getRequestsUnassigned() {
        return requestsUnassigned;
    }

    public void setRequestsUnassigned(Set<User> requestsUnassigned) {
        this.requestsUnassigned = requestsUnassigned;
    }

    public Set<Vehicle> getVehiclesHired() {
        return vehiclesHired;
    }

    public void setVehiclesHired(Set<Vehicle> vehiclesHired) {
        this.vehiclesHired = vehiclesHired;
    }

    public void accountRejected(User request) {
        getRequestsUnassigned().add(request);
        rejectedServiceLevelClass.computeIfPresent(request.qos, (qos, countRejected) -> countRejected + 1);

        // Rejected user was displaced from a routing plan
        if (request.getCurrentVisit() != null) {
            request.setCurrentVisit(null);
            requestsDisplaced.add(request);
        }
    }

    public void accountMetServiceLevel(User request) {
        requestsServicedLevelAchieved.add(request);
    }

    public void accountUnmetServiceLevel(User request) {
        unmetServiceLevelClass.computeIfPresent(request.qos, (qos, countUnmet) -> countUnmet + 1);
        requestsServicedLevelNotAchieved.add(request);
    }

    public void accountNumberOfServiceLevelViolations(Qos qos, double nOfServiceLevelViolations) {
        violationCountClassServiceLevel.put(qos, nOfServiceLevelViolations);
    }

    public void accountNumberOfRequestsClass(Qos qos, int nOfRequestsPerClass) {
        nOfRequestsClass.put(qos, nOfRequestsPerClass);
    }
}
