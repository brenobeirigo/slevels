package simulation;

import model.User;
import model.Vehicle;
import model.Visit;
import model.graph.GraphRTV;

import java.util.*;
import java.util.stream.Collectors;

public class MatchingGreedy implements RideMatchingStrategy {

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // GREEDY //////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Vehicles carrying passengers HAVE to continue to do so. Hence, they are matched to visits first, before flexible
     * vehicles (i.e., vehicles not carrying passengers).
     *
     * @param result
     * @return
     */


    private ResultAssignment greedyAssignmentVehiclesCarryingPassengers(GraphRTV graphRTV, List<Vehicle> listVehicles, ResultAssignment result) {

        // Vehicles with passengers
        Set<Vehicle> vehiclesWithPassengers = Vehicle.getVehiclesWithPassengers(listVehicles);

        if (!vehiclesWithPassengers.isEmpty()) {
            System.out.println("Vehicles with passenger: " + vehiclesWithPassengers.size());
        }

        // Setup visits of vehicle carrying passengers. Notice that many
        for (Vehicle vehicleCarryingPassenger : vehiclesWithPassengers) {

            List<Visit> visitsVehicleCarryingPassenger = graphRTV.getListOfSortedVisitsFromVehicle(vehicleCarryingPassenger);

            // Loop candidate visits for vehicle carrying passenger
            for (Visit visit : visitsVehicleCarryingPassenger) {

                // Visit is valid only if all requests are still unassigned up to this point
                if (graphRTV.allRequestsUnmatched(visit)) {
                    result.addVisit(visit);
                    // Best visit found, jump to next vehicle
                    break;
                }
            }
        }

        System.out.println("#### Vehicles CARRYING passengers:");
        System.out.println("# Requests: " + result.getRequestsOK());
        System.out.println("# Vehicles: " + result.getVehiclesOK());
        System.out.println("# Visits: " + result.getVisitsOK().size());

        // Some visits carrying passengers cannot be setup because the trips they
        Set<Vehicle> unmatchedVehiclesWithPassengers = new HashSet<>(vehiclesWithPassengers);
        unmatchedVehiclesWithPassengers.removeAll(result.getVehiclesOK());
        System.out.println("# Unmatched: " + unmatchedVehiclesWithPassengers.size());
        assert unmatchedVehiclesWithPassengers.isEmpty() : String.format("There are still unmatched vehicles = %s", unmatchedVehiclesWithPassengers);
//
//        for (Vehicle vehicleUnassignedAndCarrying : unmatchedVehiclesWithPassengers) {
//            System.out.println(String.format("Setting up vehicle with passenger. %s - Visit: %s", vehicleUnassignedAndCarrying.getVisit().getUserInfo(), vehicleUnassignedAndCarrying.getVisit()));
//
//            // Refurbishing user set (remove from visit user that have been serviced)
//            Set<User> unassignedRequestFromVehicleCarrying = new HashSet<>(vehicleUnassignedAndCarrying.getVisit().getRequests());
//            unassignedRequestFromVehicleCarrying.removeAll(requestsOK);
//            System.out.println("Unassigned requests from vehicle carrying: " + unassignedRequestFromVehicleCarrying);
//
//            assert unassignedRequestFromVehicleCarrying.size() == 0 : "There are unassigned request from vehicle carrying " + unassignedRequestFromVehicleCarrying;
//            // There should always exist a visit for a vehicle carrying passengers
//
//            System.out.println(String.format("Best visit %s", visitWithoutRequests));
//            setupVisitAndUpdate(visitWithoutRequests);
//        }

        assert result.getVehiclesOK().size() == vehiclesWithPassengers.size() : String.format("Vehicles %s HAVE passengers and could not be assigned!", unmatchedVehiclesWithPassengers);
        return result;
    }

    private ResultAssignment greedyAssignmentFlexibleVehicles(GraphRTV graphRTV, ResultAssignment result) {

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

    private ResultAssignment fixDisplaced(GraphRTV graphRTV, ResultAssignment result) {

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

        System.out.println(String.format("\n\n# Assigned vehicles (%d): %s", result.getVehiclesOK().size(), result.getVehiclesOK()));
        System.out.println(String.format("# Unassigned vehicles (%d): %s", unassignedVehicles.size(), unassignedVehicles));
        System.out.println(String.format("# Unassigned users (%d): %s", unassignedUsers.size(), unassignedUsers));
        System.out.println(String.format("# Displaced users (%d): %s", displacedUsers.size(), displacedUsers));
        System.out.println(String.format("# Vehicles disrupted (%d) = %s", result.vehiclesDisrupted.size(), result.vehiclesDisrupted));

        for (Vehicle vehicle : result.vehiclesDisrupted) {
            System.out.println("####" + vehicle.getVisit());
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
        return result;
    }

    /**
     * Greedy assign of users to vehicles (preference to larger trips). Starting from the largest vehicle capacity,
     * tries to realize candidate visits. Repeats the process until capacity 1 is reached and all visits have been evaluted.
     * 1 - Vehicles transporting passengers have to be selected
     * 2 -
     * After matching all passengers, the remaining vertices in the RTV graph refers to vehicles and users
     * that could not be matched.
     *
     * @return Set of users assigned
     */

    @Override
    public ResultAssignment match(int currentTime, List<User> setUnassignedRequests, List<Vehicle> vehicles, Matching configMatching) {

        // Consider all requests (assigned and unassigned)
        List<User> requests = new ArrayList<>(setUnassignedRequests);
        //requests.addAll(getAssignedRequestsFrom(listVehicles));

        //assert thereAreNoRepeatedRequests(requests) : "There are repeated elements in request list!";
        //assert allVehicleVisitsAreValid() : "Invalid visits found.";
        //assert eachUserIsAssignedToSingleVehicle() : "User is assigned to two different vehicles.";

        // BUILDING GRAPH STRUCTURE ////////////////////////////////////////////////////////////////////////////////////

        GraphRTV graphRTV = new GraphRTV(requests, vehicles, 4);


        ResultAssignment result = new ResultAssignment(currentTime);


        System.out.println(graphRTV.getVisitCountSetVertex() + " = " + graphRTV.getFeasibleVisitCount());

        System.out.println("----------- Assigning vehicles carrying passengers");
        // Vehicles carrying passengers MUST continue carrying them.
        result = greedyAssignmentVehiclesCarryingPassengers(graphRTV, vehicles, result);

        System.out.println("---------- Assigning flexible vehicles");
        // Vehicles assigned to requests only are flexible to have their visits completely changed
        result = greedyAssignmentFlexibleVehicles(graphRTV, result);

        result = fixDisplaced(graphRTV, result);

        // Update unassigned vehicles that were previously carrying users.
        // Some vehicles might have lost users but were later associated to new visits (are in vehiclesOK).
        result.vehiclesDisrupted.removeAll(result.getVehiclesOK());

        return result;
    }


}
