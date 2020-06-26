package simulation;

import config.Rebalance;
import model.User;
import model.Vehicle;
import model.Visit;
import model.graph.GraphRTV;
import model.graph.GraphRV;
import model.node.Node;
import org.jgrapht.graph.DefaultEdge;

import java.util.*;
import java.util.stream.Collectors;

public class SimulationRTV extends Simulation {


    /* RV, RTV */
    private int maxVehReqEdges;
    private int maxReqReqEdges;
    private int maxEdgesRTV;
    private int numberUsersPermute;
    private boolean findBestVisit;
    private int maxNumberPermutations;

    //TODO hot_PK_list

    /* Construct RTV simulation */
    public SimulationRTV(String methodName,
                         int initialFleet,
                         int vehicleMaxCapacity,
                         int maxRequestsIteration,
                         int timeWindow,
                         int timeHorizon,
                         boolean allowRebalancing,
                         int contractDuration,
                         boolean isAllowedToHire,
                         boolean isAllowedToLowerServiceLevel,
                         boolean sortWaitingUsersByClass,
                         String serviceRateScenarioLabel,
                         String segmentationScenarioLabel,
                         Rebalance rebalance) {


        // Build generic Simulation object
        super(initialFleet,
                vehicleMaxCapacity,
                maxRequestsIteration,
                timeWindow,
                timeHorizon,
                allowRebalancing,
                contractDuration,
                isAllowedToHire,
                isAllowedToLowerServiceLevel,
                sortWaitingUsersByClass,
                rebalance);


        // Service rate and segmentation scenarios
        this.serviceRateScenarioLabel = serviceRateScenarioLabel;
        this.segmentationScenarioLabel = segmentationScenarioLabel;

        // Initialize solution
        sol = new Solution(
                methodName,
                initialFleet,
                maxRequestsIteration,
                vehicleMaxCapacity,
                timeWindow,
                timeHorizon,
                allowRebalancing,
                contractDuration,
                isAllowedToHire,
                isAllowedToLowerServiceLevel,
                serviceRateScenarioLabel,
                segmentationScenarioLabel,
                rebalance);

        /* RV, RTV */
        maxVehReqEdges = 1000;
        maxReqReqEdges = 1000;
        maxEdgesRTV = 1000;

        /* Permutations */
        numberUsersPermute = 2;
        findBestVisit = false;
        maxNumberPermutations = 100;

    }

    private GraphRV graphRV;
    private GraphRTV graphRTV;

    // Set of requests scheduled to vehicles
    private Set<User> requestsOK;

    // Set of vehicles assigned to visits
    private Set<Vehicle> vehiclesOK;

    // Set of vehicles that interrupted routes and parked
    private Set<Vehicle> vehiclesDisrupted;

    // Set of visits chosen in round
    private Set<Visit> visitsOK;


    /**
     * Method: On-demand high-capacity ride-sharing via dynamic trip-vehicle assignment
     * Authors: Javier Alonso-Mora, Samitha Samaranayake, Alex Wallar, Emilio Frazzoli, and Daniela Rus
     *
     * @return users assigned
     */
    public Set<User> getServicedUsersDynamicSizedFleet(int currentTime) {

        // Set of requests scheduled to vehicles
        requestsOK = new HashSet<>();

        // Set of vehicles assigned to visits
        vehiclesOK = new HashSet<>();

        // Set of vehicles that interrupted routes and parked
        vehiclesDisrupted = new HashSet<>();

        // Set of visits chosen in round
        visitsOK = new HashSet<>();

        // Get all requests (matched and unmatched)
        List<User> requests = getExtendedRequestList();

        assert thereAreNoRepeatedRequests(requests) : "There are repeated elements in request list!";
        assert allVehicleVisitsAreValid() : "Invalid visits found.";
        assert eachUserIsAssignedToSingleVehicle() : "User is assigned to two different vehicles.";


        long start = System.nanoTime();
        graphRV = new GraphRV(requests, listVehicles, vehicleCapacity, maxVehReqEdges, maxReqReqEdges);
        System.out.println(String.format("RV created (%.2f sec)", (System.nanoTime() - start) / 1000000000.0));

        start = System.nanoTime();
        graphRTV = new GraphRTV(graphRV, vehicleCapacity, requests, listVehicles);
        System.out.println(String.format("RTV created (%.2f sec)", (System.nanoTime() - start) / 1000000000.0));

        Set<User> usersAssigned = getAssignedUsersGreedy();
        System.out.println(String.format("Users assigned (%.2f sec)", (System.nanoTime() - start) / 1000000000.0));

        Set<User> usersDisrupted = requests.stream().filter(user -> user.getNodePk().getArrivalSoFar() > 0).collect(Collectors.toSet());
        System.out.println(String.format("Users disrupted: %s", usersDisrupted));

        assert allVehicleVisitsAreValid() : "Invalid visits found.";
        assert eachUserIsAssignedToSingleVehicle() : "User is assigned to two different vehicles.";
        //assert allPassengersAreAssigned(): "Vehicle carrying passenger is not matched.";

        return usersAssigned;
    }


    /**
     * Greedy assign of users to vehicles (preference to larger trips)
     * Starting from the largest vehicle capacity, tries to realize candidate visits.
     * Repeats the process until capacity 1 is reached and all visits have been evaluted.
     * <p>
     * 1 - Vehicles transporting passengers have to be selected
     * 2 -
     * After matching all passengers, the remaining vertices in the RTV graph refers to vehicles and users
     * that could not be matched.
     *
     * @return Set of users assigned
     */
    public Set<User> getAssignedUsersGreedy() {


        System.out.println(this.graphRTV.getVisitCountSetVertex() + " = " + this.graphRTV.getFeasibleVisitCount());

        System.out.println("----------- Assigning vehicles carrying passengers");
        // Vehicles carrying passengers MUST continue carrying them.
        greedyAssignmentVehiclesCarryingPassengers();

        System.out.println("---------- Assigning flexible vehicles");
        // Vehicles assigned to requests only are flexible to have their visits completely changed
        greedyAssignmentFlexibleVehicles();

        return requestsOK;
    }

    private void greedyAssignmentFlexibleVehicles() {

        // Loop visits starting from the longest (combining more requests)
        for (int k = graphRTV.getFeasibleTrips().size() - 1; k >= 0; k--) {

            // Set of ordered visits (shortest delay) in level k
            List<Visit> visitsLevelK = graphRTV.getFeasibleTrips().get(k);
            Collections.sort(visitsLevelK);

            // Loop all visits
            for (Visit visit : visitsLevelK) {

                // Visit was previously assigned (refers to vehicle with passengers)
                if (!graphRTV.containsVertex(visit))
                    continue;

                // Either the vehicle or the users were previously assigned
                if (vehiclesOK.contains(visit.getVehicle()) || !Collections.disjoint(requestsOK, visit.getRequests())) {
                    graphRTV.removeVisit(visit);
                    continue;
                }

                // Update requests, vehicles, and greedy solution
                setupVisitAndUpdate(visit);
            }
        }

        // Find users and vehicles left unassigned
        List<User> unassignedUsers = new ArrayList<>();
        List<Vehicle> unassignedVehicles = new ArrayList<>();
        for (Object o : graphRTV.vertexSet()) {
            if (o instanceof User) {
                unassignedUsers.add((User) o);
            }
            if (o instanceof Vehicle) {
                unassignedVehicles.add((Vehicle) o);
            }
        }

        List<User> displacedUsers = unassignedUsers.stream()
                .filter(user -> user.getCurrentVisit() != null)
                .collect(Collectors.toList());

        // Update unassigned vehicles that were previously carrying users.
        // Some vehicles might have lost users but were later associated to new visits (are in vehiclesOK).
        vehiclesDisrupted.removeAll(vehiclesOK);

        System.out.println(String.format("\n\n# Assigned vehicles (%d): %s", vehiclesOK.size(), vehiclesOK));
        System.out.println(String.format("# Unassigned vehicles (%d): %s", unassignedVehicles.size(), unassignedVehicles));
        System.out.println(String.format("# Unassigned users (%d): %s", unassignedUsers.size(), unassignedUsers));
        System.out.println(String.format("# Displaced users (%d): %s", displacedUsers.size(), displacedUsers));
        System.out.println(String.format("# Vehicles disrupted (%d) = %s", vehiclesDisrupted.size(), vehiclesDisrupted));

        for (Vehicle vehicle : vehiclesDisrupted) {
            System.out.println("####" + vehicle.getVisit());
        }

        // Unassigned users cannot be unmatched
        for (User u : unassignedUsers) {

            assert userCannotBePickedUpByIdleVehicles(new HashSet<>(unassignedVehicles), u) : "Other vehicles could pick up user.";
            assert graphRTV.containsVertex(u) : "There is no valid visit for user " + u + " but it still in graph." + graphRTV.edgesOf(u);
            //assert u.getCurrentVisit() == null : String.format("Rejected user %s, was previously in visit %s", u, u.getCurrentVisit());

            assert u.getCurrentVisit() == null : "Current visit is not null" + u.getCurrentVisit() + " - " + displacedUsers;

            // Erase user past visit
            u.setCurrentVisit(null);
        }

        assert displacedUsers.isEmpty() : "There are displaced " + displacedUsers;

        if (!displacedUsers.isEmpty())
            assert !Collections.disjoint(unassignedUsers, displacedUsers) : "Displaced users are not unassigned " + displacedUsers;

        // All vehicles that lost requests rebalance to closest node (middle or next target)
        for (Vehicle vehicleDisrupted : vehiclesDisrupted) {

            assert vehicleDisrupted.getVisit().getPassengers().isEmpty() : "Interrupted vehicle had passenger!" + vehicleDisrupted.getVisit().getUserInfo() + " - Visit:" + vehicleDisrupted.getVisit();
            assert disruptedUsersAreServicedByDifferentVehicles(vehicleDisrupted) : "Disrupted users (unmatched) are not inserted in different vehicles.";

            // Stop vehicle at middle point or next target
            vehicleDisrupted.rebalanceToClosestNode();

            // vehicleDisrupted.rebalanceToClosestNode();

            /*for (User u : requestsFormerlyServicedByDisruptedVehicle) {
                assert u.getCurrentVisit() != null && u.getCurrentVisit() != vehicleDisrupted.getVisit() : String.format("User %s in vehicle %s is still associated with the vehicle", u, vehicleDisrupted);
            }*/
        }

        assert unassignedVehicles.size() == (new HashSet<>(unassignedVehicles)).size() : "There are repeated vehicles in RTV graph.";
        assert unassignedUsers.size() == (new HashSet<>(unassignedUsers)).size() : "There are repeated users in RTV graph.";
        // assert usersAreNotDisplaced(unassignedUsers) : "User was displaced from vehicle:";
    }

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

    private boolean userCannotBePickedUpByIdleVehicles(Set<Vehicle> vehicleOk, User u) {

        for (DefaultEdge edge : graphRTV.edgesOf(u)) {
            Vehicle v = ((Visit) graphRTV.getEdgeTarget(edge)).getVehicle();
            if (!vehicleOk.contains(v)) {
                System.out.println(String.format("Free vehicle %s can service request %s: Visit = %s", v, u, v.getVisit()));
                return false;
            }
        }

        Set<Vehicle> freeVehicles = new HashSet<>(listVehicles);
        freeVehicles.removeAll(vehicleOk);

        for (Vehicle v : freeVehicles) {
            Visit candidateVisit = Method.getBestVisitFor(v, new HashSet<>(Arrays.asList(u)));
            if (candidateVisit != null) {
                System.out.println(String.format("(CANDIDATE VISIT) Free vehicle %s can service request %s: Visit = %s", v, u, candidateVisit));
                return false;
            }
        }
        return true;
    }

    private boolean usersAreNotDisplaced(List<User> unassignedUsers) {
        List<User> displacedUsers = unassignedUsers.stream().filter(user -> user.getCurrentVisit() != null).collect(Collectors.toList());
        if (!displacedUsers.isEmpty()) {
            for (User displacedUser : displacedUsers) {
                System.out.println(displacedUser + " - " + displacedUser.getCurrentVisit());
            }
            return false;
        }
        return true;
    }

    private void greedyAssignmentVehiclesCarryingPassengers() {

        // Vehicles with passengers
        Set<Vehicle> vehiclesWithPassengers = Vehicle.getVehiclesWithPassengers(this.listVehicles);

        if (!vehiclesWithPassengers.isEmpty()) {
            System.out.println("Vehicles with passenger: " + vehiclesWithPassengers.size());
        }

        for (Vehicle vehicleCarryingPassenger : vehiclesWithPassengers) {

            List<Visit> visitsVehicleCarryingPassenger = graphRTV.getListOfSortedVisitsFromVehicle(vehicleCarryingPassenger);

            // Loop candidate visits for vehicle carrying passenger
            for (Visit visit : visitsVehicleCarryingPassenger) {

                if (allRequestsUnmatched(visit)) {
                    setupVisitAndUpdate(visit);
                    // Vehicle is setup, jump to next vehicle
                    break;
                }
            }
        }

        System.out.println("#### Vehicles CARRYING passengers:");
        System.out.println("# Requests: " + requestsOK);
        System.out.println("# Vehicles: " + vehiclesOK);
        System.out.println("# Visits: " + visitsOK.size());

        Set<Vehicle> unmatchedVehiclesWithPassengers = new HashSet<>(vehiclesWithPassengers);
        unmatchedVehiclesWithPassengers.removeAll(vehiclesOK);
        System.out.println("# Unmatched: " + unmatchedVehiclesWithPassengers.size());

        for (Vehicle vehicleUnassignedAndCarrying : unmatchedVehiclesWithPassengers) {
            System.out.println(String.format("Setting up vehicle with passenger. %s - Visit: %s", vehicleUnassignedAndCarrying.getVisit().getUserInfo(), vehicleUnassignedAndCarrying.getVisit()));

            // Refurbishing user set (remove from visit user that have been serviced)
            Set<User> unassignedRequestFromVehicleCarrying = new HashSet<>(vehicleUnassignedAndCarrying.getVisit().getRequests());
            unassignedRequestFromVehicleCarrying.removeAll(requestsOK);
            System.out.println("Unassigned requests from vehicle carrying: " + unassignedRequestFromVehicleCarrying);

            Visit visitWithoutRequests = Method.getBestVisitFor(vehicleUnassignedAndCarrying, unassignedRequestFromVehicleCarrying);
            if (visitWithoutRequests == null) {
                System.out.println(vehicleUnassignedAndCarrying + " - " + visitWithoutRequests);
            }
            System.out.println(String.format("Best visit %s", visitWithoutRequests));
            setupVisitAndUpdate(visitWithoutRequests);
        }

        assert vehiclesOK.size() == vehiclesWithPassengers.size() : String.format("Vehicles %s HAVE passengers and could not be assigned!", unmatchedVehiclesWithPassengers);
    }

    private boolean allRequestsUnmatched(Visit visit) {

        for (User request : visit.getRequests()) {

            // If user is not in graph, it means another trip picked up user
            if (!graphRTV.containsVertex(request)) {
                return false;
            }
        }
        return true;
    }

    private void setupVisitAndUpdate(Visit visit) {

        // Update requests, vehicles, and visitsOK solution
        this.requestsOK.addAll(visit.getRequests());
        this.vehiclesOK.add(visit.getVehicle());
        this.visitsOK.add(visit);

        // Update RTV graph and setup visit
        this.graphRTV.removeVisit(visit);

        // Remove vehicle and users matched from graph
        for (User user : visit.getRequests()) {

            // If user changes visit
            if (user.getCurrentVisit() != null && visit != user.getCurrentVisit()) {

                // Visit of vehicle formerly carrying request will be changed
                this.vehiclesDisrupted.add(user.getCurrentVisit().getVehicle());
            }
        }

        System.out.println(String.format("Setting up %s - User: %s", visit, visit.getUserInfo()));
        System.out.println(String.format("# Requests (%d): %s", requestsOK.size(), requestsOK));
        System.out.println(String.format("# Vehicles (%d): %s", vehiclesOK.size(), vehiclesOK));
        System.out.println(String.format("# Disrupted (%d): %s", vehiclesDisrupted.size(), vehiclesDisrupted));
        System.out.println(String.format("# Visits: %d", visitsOK.size()));

        this.setup(visit);
    }
}