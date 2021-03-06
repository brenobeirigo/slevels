package simulation.matching;

import dao.Logging;
import model.*;
import model.VisitObj;
import model.graph.StandardGraphRTV;

import java.util.*;
import java.util.stream.Collectors;


public class MatchingGreedy implements RideMatchingStrategy {
    protected int maxVehicleCapacityRTV;
    protected double timeoutVehicleRTV;
    protected double mipTimeLimit;
    protected double mipGap;
    protected int maxEdgesRV;
    protected int maxEdgesRR;

    public MatchingGreedy(int maxVehicleCapacityRTV, double timeLimit, double timeoutVehicle, double mipGap, int maxEdgesRV, int maxEdgesRR) {
        this.maxVehicleCapacityRTV = maxVehicleCapacityRTV;
        this.mipTimeLimit = timeLimit;
        this.timeoutVehicleRTV = timeoutVehicle;
        this.mipGap = mipGap;
        this.maxEdgesRV = maxEdgesRV;
        this.maxEdgesRR = maxEdgesRR;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // GREEDY //////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Vehicles carrying passengers HAVE to continue to do so. Hence, they are matched to visits first, before flexible
     * vehicles (i.e., vehicles not carrying passengers).
     */

    private ResultAssignment greedyAssignmentVehiclesCarryingPassengers2(StandardGraphRTV graphRTV, List<Vehicle> listVehicles, ResultAssignment result) {

        // Vehicles with passengers
        Set<Vehicle> vehiclesWithPassengers = Vehicle.getVehiclesWithPassengers(listVehicles);
        Set<User> allPassengers = Vehicle.getAllPassengersFromVehicles(listVehicles);

        if (!vehiclesWithPassengers.isEmpty()) {
            Logging.logger.info("Vehicles with passenger: " + vehiclesWithPassengers.size());
        }

        // Setup visits of vehicle carrying passengers. Notice that many
        for (Vehicle vehicleCarryingPassenger : vehiclesWithPassengers) {

            List<VisitObj> visitsVehicleCarryingPassenger = graphRTV.getListOfSortedVisitsFromVehicle(vehicleCarryingPassenger);

            // Passengers from visit are all unmatched
            // Loop candidate visits for vehicle carrying passenger
            for (VisitObj visit : visitsVehicleCarryingPassenger) {

                // VisitObj is only accepted if requests are not displaced
                if (!visit.getRequests().containsAll(visit.getVehicle().getVisit().getRequests())) {
                    continue;
                }

                // VisitObj is valid only if all requests are still unassigned up to this point
                if (graphRTV.allRequestsUnmatched(visit)) {
                    result.addVisit(visit);
                    graphRTV.removeVisit(visit);
                    // Best visit found, jump to next vehicle
                    break;
                }
            }
        }

        Logging.logger.info("#### Vehicles CARRYING passengers:");
        Logging.logger.info("{}", String.format("         # Requests: %s", result.getRequestsOK()));
        Logging.logger.info("{}", String.format("Passengers (before): %s", Vehicle.getAllPassengersFromVehicles(listVehicles)));
        Logging.logger.info("{}", String.format("      (vehicles OK): %s", Vehicle.getAllPassengersFromVehicles(result.getVehiclesOK())));
        Logging.logger.info("{}", String.format("         # Vehicles: %s", result.getVehiclesOK()));
        Logging.logger.info("{}", String.format("           # Visits: %s", result.getVisitsOK().size()));

        // Some visits carrying passengers cannot be setup because the trips they
        Set<Vehicle> unmatchedVehiclesWithPassengers = new HashSet<>(vehiclesWithPassengers);
        unmatchedVehiclesWithPassengers.removeAll(result.getVehiclesOK());
        Logging.logger.info("# Unmatched: " + unmatchedVehiclesWithPassengers.size());
        assert unmatchedVehiclesWithPassengers.isEmpty() : String.format("There are still unmatched vehicles = %s", unmatchedVehiclesWithPassengers);

        /*for (Vehicle vehicleUnassignedAndCarrying : unmatchedVehiclesWithPassengers) {
            Logging.logger.info("{}", String.format("Setting up vehicle with passenger. %s - VisitObj: %s", vehicleUnassignedAndCarrying.getVisit().getUserInfo(), vehicleUnassignedAndCarrying.getVisit()));

            // Refurbishing user set (remove from visit user that have been serviced)
            Set<User> unassignedRequestFromVehicleCarrying = new HashSet<>(vehicleUnassignedAndCarrying.getVisit().getRequests());
            unassignedRequestFromVehicleCarrying.removeAll(requestsOK);
            Logging.logger.info("Unassigned requests from vehicle carrying: " + unassignedRequestFromVehicleCarrying);

            assert unassignedRequestFromVehicleCarrying.size() == 0 : "There are unassigned request from vehicle carrying " + unassignedRequestFromVehicleCarrying;
            // There should always exist a visit for a vehicle carrying passengers

            Logging.logger.info("{}", String.format("Best visit %s", visitWithoutRequests));
            setupVisitAndUpdate(visitWithoutRequests);
        }*/

        assert result.getVehiclesOK().size() == vehiclesWithPassengers.size() : String.format("Vehicles %s HAVE passengers and could not be assigned!", unmatchedVehiclesWithPassengers);
        return result;
    }



    /**
     * Vehicles carrying passengers HAVE to continue to do so. Hence, they are matched to visits first, before flexible
     * vehicles (i.e., vehicles not carrying passengers).
     */

    private ResultAssignment greedyAssignmentVehiclesCarryingPassengers(StandardGraphRTV graphRTV, Set<Vehicle> listVehicles, ResultAssignment result) {

        // Vehicles with passengers
        List<Vehicle> vehiclesWithPassengers = Vehicle.getVehiclesServicing(listVehicles);

        if (!vehiclesWithPassengers.isEmpty()) {
            Logging.logger.info("Vehicles with passenger: " + vehiclesWithPassengers.size());
        }

        // Setup visits of vehicle carrying passengers. Notice that many
        for (Vehicle vehicleCarryingPassenger : vehiclesWithPassengers) {

            List<VisitObj> visitsVehicleCarryingPassenger = graphRTV.getListOfSortedVisitsFromVehicle(vehicleCarryingPassenger);

            // Passengers from visit are all unmatched
            // Loop candidate visits for vehicle carrying passenger
            for (VisitObj visit : visitsVehicleCarryingPassenger) {

                // VisitObj is only accepted if requests are not displaced
                if (!visit.getRequests().containsAll(visit.getVehicle().getVisit().getRequests())) {
                    continue;
                }

                // VisitObj is valid only if all requests are still unassigned up to this point
                if (graphRTV.allRequestsUnmatched(visit)) {
                    result.addVisit(visit);
                    graphRTV.removeVisit(visit);
                    // Best visit found, jump to next vehicle
                    break;
                }
            }
        }

        Logging.logger.info("#### Vehicles CARRYING passengers:");
        Logging.logger.info("{}", String.format("         # Requests: %s", result.getRequestsOK()));
        Logging.logger.info("{}", String.format("Passengers (before): %s", Vehicle.getAllPassengersFromVehicles(listVehicles)));
        Logging.logger.info("{}", String.format("      (vehicles OK): %s", Vehicle.getAllPassengersFromVehicles(result.getVehiclesOK())));
        Logging.logger.info("{}", String.format("         # Vehicles: %s", result.getVehiclesOK()));
        Logging.logger.info("{}", String.format("           # Visits: %s", result.getVisitsOK().size()));

        // Some visits carrying passengers cannot be setup because the trips they
        Set<Vehicle> unmatchedVehiclesWithPassengers = new HashSet<>(vehiclesWithPassengers);
        unmatchedVehiclesWithPassengers.removeAll(result.getVehiclesOK());
        Logging.logger.info("# Unmatched: " + unmatchedVehiclesWithPassengers.size());
        assert unmatchedVehiclesWithPassengers.isEmpty() : String.format("There are still unmatched vehicles = %s", unmatchedVehiclesWithPassengers);

        /*for (Vehicle vehicleUnassignedAndCarrying : unmatchedVehiclesWithPassengers) {
            Logging.logger.info("{}", String.format("Setting up vehicle with passenger. %s - VisitObj: %s", vehicleUnassignedAndCarrying.getVisit().getUserInfo(), vehicleUnassignedAndCarrying.getVisit()));

            // Refurbishing user set (remove from visit user that have been serviced)
            Set<User> unassignedRequestFromVehicleCarrying = new HashSet<>(vehicleUnassignedAndCarrying.getVisit().getRequests());
            unassignedRequestFromVehicleCarrying.removeAll(requestsOK);
            Logging.logger.info("Unassigned requests from vehicle carrying: " + unassignedRequestFromVehicleCarrying);

            assert unassignedRequestFromVehicleCarrying.size() == 0 : "There are unassigned request from vehicle carrying " + unassignedRequestFromVehicleCarrying;
            // There should always exist a visit for a vehicle carrying passengers

            Logging.logger.info("{}", String.format("Best visit %s", visitWithoutRequests));
            setupVisitAndUpdate(visitWithoutRequests);
        }*/

        assert result.getVehiclesOK().size() == vehiclesWithPassengers.size() : String.format("Vehicles %s HAVE passengers and could not be assigned!", unmatchedVehiclesWithPassengers);
        return result;
    }

    private ResultAssignment greedyAssignmentFlexibleVehicles(StandardGraphRTV graphRTV, ResultAssignment result) {

        // Loop visits starting from the longest (combining more requests)
        for (int k = graphRTV.getFeasibleTrips().size() - 1; k >= 0; k--) {

            // Set of ordered visits (shortest delay) in level k
            List<VisitObj> visitsLevelK = graphRTV.getFeasibleTrips().get(k);
            //Collections.sort(visitsLevelK);

            // Loop all visits
            for (VisitObj visit : visitsLevelK) {

                // VisitObj is only accepted if requests are not displaced
                /*Logging.logger.info("Requests (visit):" + visit.getRequests());
                Logging.logger.info("Vehicle:" + visit.getVehicle());
                Logging.logger.info("Vehicle.visit:" + visit.getVehicle().getVisit());
                Logging.logger.info("vehicle.visit.requests:" + visit.getVehicle().getVisit().getRequests());*/
                if (visit.getVehicle().getVisit() != null && !visit.getRequests().containsAll(visit.getVehicle().getVisit().getRequests())) {
                    continue;
                }

                // VisitObj was previously assigned (refers to vehicle with passengers)
                if (!graphRTV.containsVertex(visit))
                    continue;

                // Either the vehicle or the users were previously assigned
                if (result.getVehiclesOK().contains(visit.getVehicle()) || !Collections.disjoint(result.getRequestsOK(), visit.getRequests())) {
                    graphRTV.removeVisit(visit);
                    continue;
                }

                // Update requests, vehicles, and greedy solution
                result.addVisit(visit);
            }
        }
        return result;
    }

    private ResultAssignment fixDisplaced(StandardGraphRTV graphRTV, ResultAssignment result) {

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

        // Displaced user were associated to a visit earlier
        List<User> displacedUsers = unassignedUsers.stream()
                .filter(user -> user.getCurrentVisit() != null)
                .collect(Collectors.toList());

        // Update unassigned vehicles that were previously carrying users.
        // Some vehicles might have lost users but were later associated to new visits (are in vehiclesOK).
        result.vehiclesDisrupted.removeAll(result.getVehiclesOK());
        assert displacedUsers.isEmpty() : "There are displaced users!";
        Logging.logger.info("{}", String.format("\n\n# Assigned vehicles (%d): %s", result.getVehiclesOK().size(), result.getVehiclesOK()));
        Logging.logger.info("{}", String.format("# Unassigned vehicles (%d): %s", unassignedVehicles.size(), unassignedVehicles));
        Logging.logger.info("{}", String.format("# Unassigned users (%d): %s", unassignedUsers.size(), unassignedUsers));
        Logging.logger.info("{}", String.format("# Displaced users (%d): %s", displacedUsers.size(), displacedUsers));
        Logging.logger.info("{}", String.format("# Vehicles disrupted (%d) = %s", result.vehiclesDisrupted.size(), result.vehiclesDisrupted));

        for (Vehicle vehicle : result.vehiclesDisrupted) {
            Logging.logger.info("####" + vehicle.getVisit());
        }

        // Unassigned users cannot be unmatched
        for (User u : unassignedUsers) {

            //assert userCannotBePickedUpByIdleVehicles(graphRTV, new HashSet<>(unassignedVehicles), u) : "Other vehicles could pick up user.";
            //assert graphRTV.containsVertex(u) : "There is no valid visit for user " + u + " but it still in graph." + graphRTV.edgesOf(u);
            //assert u.getCurrentVisit() == null : String.format("Rejected user %s, was previously in visit %s", u, u.getCurrentVisit());

            assert u.getCurrentVisit() == null : "Current visit is not null" + u.getCurrentVisit() + " - " + displacedUsers;

            // Erase user past visit
            u.setCurrentVisit(null);
        }

        assert displacedUsers.isEmpty() : "There are displaced " + displacedUsers;

        // All vehicles that lost requests rebalance to closest node (middle or next target)
        for (Vehicle vehicleDisrupted : result.vehiclesDisrupted) {

            // Stop vehicle at middle point or next target
            vehicleDisrupted.rebalanceToClosestNode();

        }

        assert unassignedVehicles.size() == (new HashSet<>(unassignedVehicles)).size() : "There are repeated vehicles in RTV graph.";
        assert unassignedUsers.size() == (new HashSet<>(unassignedUsers)).size() : "There are repeated users in RTV graph.";
        // assert usersAreNotDisplaced(unassignedUsers) : "User was displaced from vehicle:";

        result.setRequestsUnassigned(new HashSet<>(unassignedUsers));
        return result;
    }

    /**
     * Greedy assign of users to vehicles (preference to larger trips). Starting from the largest vehicle capacity,
     * tries to realize candidate visits. Repeats the process until capacity 1 is reached and all visits have been evaluted.
     * 1 - Vehicles transporting passengers have to be selected
     * 2 -
     * After simulation.matching all passengers, the remaining vertices in the RTV graph refers to vehicles and users
     * that could not be matched.
     *
     * @return Set of users assigned
     */

    @Override
    public ResultAssignment match(int currentTime, Set<User> unassignedRequests, Set<Vehicle> vehicles, Set<Vehicle> hired) {


        //assert thereAreNoRepeatedRequests(requests) : "There are repeated elements in request list!";
        //assert allVehicleVisitsAreValid() : "Invalid visits found.";
        //assert eachUserIsAssignedToSingleVehicle() : "User is assigned to two different vehicles.";

        // BUILDING GRAPH STRUCTURE ////////////////////////////////////////////////////////////////////////////////////

        StandardGraphRTV graphRTV = new StandardGraphRTV(unassignedRequests, vehicles, maxVehicleCapacityRTV, timeoutVehicleRTV, maxEdgesRV, maxEdgesRR);

        ResultAssignment result = new ResultAssignment(currentTime);

        Logging.logger.info("Unassigned requests: " + unassignedRequests.size());

        if (unassignedRequests.isEmpty())
            return result;

        Logging.logger.info(graphRTV.getVisitCountSetVertex() + " = " + graphRTV.getFeasibleVisitCount());

        Logging.logger.info("----------- Assigning vehicles carrying passengers");
        // Vehicles carrying passengers MUST continue carrying them.
        result = greedyAssignmentVehiclesCarryingPassengers(graphRTV, vehicles, result);

        Logging.logger.info("---------- Assigning flexible vehicles");
        // Vehicles assigned to requests only are flexible to have their visits completely changed
        result = greedyAssignmentFlexibleVehicles(graphRTV, result);

        // result = fixDisplaced(graphRTV, result);

        // Update unassigned vehicles that were previously carrying users.
        // Some vehicles might have lost users but were later associated to new visits (are in vehiclesOK).
        result.setRequestsUnassigned(new HashSet<>(unassignedRequests));
        result.getRequestsUnassigned().removeAll(result.getRequestsOK());
        result.vehiclesDisrupted.removeAll(result.getVehiclesOK());
        result.requestsServicedLevelAchieved.addAll(Visit.filterFirstTier(result.getVisitsOK()));
        result.requestsServicedLevelNotAchieved.addAll(Visit.filterSecondTier(result.getVisitsOK()));
        result.requestsDisplaced = result.getRequestsUnassigned().stream()
                .filter(user -> user.getCurrentVisit() != null)
                .collect(Collectors.toSet());

        result.requestsDisplaced.forEach(user -> user.setCurrentVisit(null));

        if (!result.assignedAndUnassignedAreDisjoint()) {
            Logging.logger.info("Not Disjoint!");
        }
        /*for (Qos qos : Config.getInstance().qosDic.values()) {
            result.unmetServiceLevelClass.put(qos, User.filterUsersOfQos(User.filterSecondTier(requests), qos).size());
            result.totalServiceLevelClass.put(qos, User.filterUsersOfQos(requests, qos).size());
        }*/
        result.printRoundResult();
        return result;
    }

    @Override
    public void realize(Set<VisitObj> visits) {
        visits.forEach(visit -> realizeVisit(visit));
    }

    @Override
    public void realizeVisit(VisitObj visit) {

    }

    @Override
    public String toString() {
        return "_GREEDY";
    }
}
