package simulation;

import config.Rebalance;
import gurobi.*;
import model.User;
import model.Vehicle;
import model.Visit;
import model.graph.GraphRTV;
import model.graph.GraphRV;
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
                         String serviceRateScenarioLabel,
                         String segmentationScenarioLabel,
                         Rebalance rebalance,
                         Matching matchingSettings) {


        // Build generic Simulation object
        super(initialFleet,
                vehicleMaxCapacity,
                maxRequestsIteration,
                timeWindow,
                timeHorizon,
                contractDuration,
                isAllowedToHire,
                isAllowedToLowerServiceLevel,
                rebalance,
                matchingSettings);


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
                contractDuration,
                isAllowedToHire,
                isAllowedToLowerServiceLevel,
                serviceRateScenarioLabel,
                segmentationScenarioLabel,
                rebalance.strategy,
                matchingSettings.strategy);

        /* RV, RTV */
        maxVehReqEdges = 1000;
        maxReqReqEdges = 1000;
        maxEdgesRTV = 1000;

        /* Permutations */
        numberUsersPermute = 2;
        findBestVisit = false;
        maxNumberPermutations = 100;

    }

    /**
     * Method: On-demand high-capacity ride-sharing via dynamic trip-vehicle assignment
     * Authors: Javier Alonso-Mora, Samitha Samaranayake, Alex Wallar, Emilio Frazzoli, and Daniela Rus
     *
     * @return users assigned
     */
    public Set<User> getUsersAssigned(int currentTime) {

        // Consider all requests (assigned and unassigned)
        List<User> requests = new ArrayList<>(unassignedRequests);
        requests.addAll(getAssignedRequestsFrom(listVehicles));

        assert thereAreNoRepeatedRequests(requests) : "There are repeated elements in request list!";
        assert allVehicleVisitsAreValid() : "Invalid visits found.";
        assert eachUserIsAssignedToSingleVehicle() : "User is assigned to two different vehicles.";

        // BUILDING GRAPH STRUCTURE ////////////////////////////////////////////////////////////////////////////////////

        GraphRTV graphRTV = getGraphRTV(requests, listVehicles, vehicleCapacity);

        // ASSIGNMENT //////////////////////////////////////////////////////////////////////////////////////////////////

        this.runTimes.put(Solution.TIME_MATCHING, System.nanoTime());
        //ResultAssignment result = getAssignedUsersGreedy(graphRTV);
        ResultAssignment result = getAssignedUsersOptimal(graphRTV);
        this.runTimes.put(Solution.TIME_MATCHING, System.nanoTime() - this.runTimes.get(Solution.TIME_MATCHING));
        System.out.println(String.format("Users assigned (%.2f sec)", this.runTimes.get(Solution.TIME_MATCHING) / 1000000000.0));

        // Implement solutions
        result.getVisitsOK().forEach(this::realizeVisit);
        result.printRoundResult();

        assert allVehicleVisitsAreValid() : "Invalid visits found.";
        assert eachUserIsAssignedToSingleVehicle() : "User is assigned to two different vehicles.";
        //assert allPassengersAreAssigned(): "Vehicle carrying passenger is not matched.";

        return result.getRequestsOK();
    }

    private GraphRTV getGraphRTV(List<User> requests, List<Vehicle> listVehicles, int vehicleCapacity) {

        // REQUEST - TRIP (RV)
        this.runTimes.put(Solution.TIME_CREATE_RV, System.nanoTime());
        GraphRV graphRV = new GraphRV(requests, listVehicles, vehicleCapacity);
        this.runTimes.put(Solution.TIME_CREATE_RV, System.nanoTime() - this.runTimes.get(Solution.TIME_CREATE_RV));
        System.out.println(String.format("# RV created (%.2f sec)", (this.runTimes.get(Solution.TIME_CREATE_RV)) / 1000000000.0));

        // REQUEST - TRIP - VEHICLE (RTV)
        this.runTimes.put(Solution.TIME_CREATE_RTV, System.nanoTime());
        GraphRTV graphRTV = new GraphRTV(graphRV, vehicleCapacity, requests, listVehicles);
        this.runTimes.put(Solution.TIME_CREATE_RTV, System.nanoTime() - this.runTimes.get(Solution.TIME_CREATE_RTV));
        System.out.println(String.format("# RTV created (%.2f sec) - %s", (this.runTimes.get(Solution.TIME_CREATE_RTV) / 1000000000.0), graphRTV.getSummaryFeasibleTripsLevel()));
        return graphRTV;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // OPTIMAL /////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public ResultAssignment getAssignedUsersOptimal(GraphRTV graphRTV) {

        ResultAssignment result = new ResultAssignment(this.timeWindow);

        final int REJECTION_PENALTY = 100000;

        // To assure every vehicle is assigned to a visit, create dummy stop visit.
        graphRTV.addStopVisits();

        try {

            // Model
            GRBEnv env = new GRBEnv();

            // Turn off logging
            // env.set(GRB.IntParam.OutputFlag, 0);

            GRBModel model = new GRBModel(env);
            model.set(GRB.StringAttr.ModelName, "assignment_rtv");


            List<Visit> visits = graphRTV.getAllVisits();
            List<User> requests = graphRTV.getAllRequests();

            // Some vehicles cannot access
            List<Vehicle> vehicles = graphRTV.getListVehiclesFromRTV();

            for (Vehicle vehicle : vehicles) {
                assert !graphRTV.getListOfVisitsFromVehicle(vehicle).isEmpty() : "Vehicle is disconnected!" + vehicle;
            }
            System.out.println(requests);

            Map<Visit, Integer> visitIndex = new HashMap<>();
            for (int i = 0; i < visits.size(); i++) {
                visitIndex.put(visits.get(i), i);
            }

            Map<User, Integer> requestIndex = new HashMap<>();
            for (int i = 0; i < requests.size(); i++) {
                requestIndex.put(requests.get(i), i);
            }

            // Assignment variables: x[r][v] == 1 if request r is assigned to trip v
            GRBVar[] x = new GRBVar[visits.size()];
            GRBVar[] y = new GRBVar[requests.size()];

            // Set primary objective
            //GRBLinExpr obj0 = new GRBLinExpr();
            //GRBLinExpr obj1 = new GRBLinExpr();

            for (Visit visit : visits) {

                String label = String.format("x_%s", visit.getVarId());

                x[visitIndex.get(visit)] = model.addVar(0, 1, visit.getDelay(), GRB.BINARY, label);
                // obj0.addTerm(1, x[visitIndex.get(visit)]);
            }

            for (Vehicle vehicle : vehicles) {

                GRBLinExpr constrVehicleConservation = new GRBLinExpr();

                for (Visit visit : graphRTV.getListOfVisitsFromVehicle(vehicle)) {
                    constrVehicleConservation.addTerm(1, x[visitIndex.get(visit)]);
                }
                // A target can be visited by at most one vehicle

                model.addConstr(
                        constrVehicleConservation,
                        GRB.EQUAL, //vehicle.isCarryingPassengers() ? GRB.EQUAL : GRB.LESS_EQUAL,
                        1,
                        vehicle.toString().trim());
            }

            // model.setObjectiveN(obj0, 0, 2, 1.0, 2.0, 0.1, "TotalSlack");
            // model.setObjectiveN(obj1, 1, 1, 1.0, 0.0, 0.0, "Fairness");

            for (User request : requests) {

                GRBLinExpr constrRequestConservation = new GRBLinExpr();

                List<Visit> requestVisits = graphRTV.getListOfVisitsFromUser(request);
                for (Visit visit : requestVisits) {
                    constrRequestConservation.addTerm(1, x[visitIndex.get(visit)]);
                }

                y[requestIndex.get(request)] = model.addVar(0, 1, REJECTION_PENALTY, GRB.BINARY, "y_RE_" + request.toString().trim());
                constrRequestConservation.addTerm(1, y[requestIndex.get(request)]);


                // Requests are associated with only one visit
                model.addConstr(constrRequestConservation, GRB.EQUAL, 1, request.toString().trim());
            }

            // The objective is to minimize the total pay costs
            model.set(GRB.IntAttr.ModelSense, GRB.MINIMIZE);

            // Optimize
            model.optimize();
            int status = model.get(GRB.IntAttr.Status);

            if (status == GRB.Status.OPTIMAL) {

                /*if (config.showInfo) {
                    System.out.println("The optimal objective is " + model.get(GRB.DoubleAttr.ObjVal));
                    System.out.println("Optimal rebalancing:");
                }*/

                for (User request : requests) {

                    if (y[requestIndex.get(request)].get(GRB.DoubleAttr.X) > 0.99) {
                        result.requestsUnassigned.add(request);

                        // Rejected user was displaced from a routing plan
                        if (request.getCurrentVisit() != null) {
                            request.setCurrentVisit(null);
                            result.requestsDisplaced.add(request);
                        }
                    }
                }

                for (Visit visit : visits) {

                    if (x[visitIndex.get(visit)].get(GRB.DoubleAttr.X) > 0.99) {
                        result.addVisit(visit);
                    }
                }

                // Update unassigned vehicles that were previously carrying users.
                // Some vehicles might have lost users but were later associated to new visits (are in vehiclesOK).
                result.vehiclesDisrupted.removeAll(result.getVehiclesOK());

                // Requests, trips, and vehicles used are removed
                graphRTV.removeOkVerticesRTV(result);

                // Dispose of model and environment
                model.dispose();
                env.dispose();

                return result;
            }

            // Compute IIS
            System.out.println(String.format("ROUND = %s - The model is infeasible; computing IIS", this.roundCount));
            model.computeIIS();
            System.out.println("\nThe following constraint(s) cannot be satisfied:");
            for (GRBConstr c : model.getConstrs()) {
                if (c.get(GRB.IntAttr.IISConstr) == 1) {
                    System.out.println(c.get(GRB.StringAttr.ConstrName));
                }
            }

            // Save problem
            model.write(String.format("round_mip_model/assignment_5%d.lp", this.roundCount));

            graphRTV.printDetailedVisitsLevel();

        } catch (GRBException e) {
            System.out.println("Error code: " + e.getErrorCode() + ". " +
                    e.getMessage());
        }
        return result;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // GREEDY //////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Vehicles carrying passengers HAVE to continue to do so. Hence, they are matched to visits first, before flexible
     * vehicles (i.e., vehicles not carrying passengers).
     * @param result
     * @return
     */
    private ResultAssignment greedyAssignmentVehiclesCarryingPassengers(GraphRTV graphRTV, ResultAssignment result) {

        // Vehicles with passengers
        Set<Vehicle> vehiclesWithPassengers = Vehicle.getVehiclesWithPassengers(this.listVehicles);

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

            assert userCannotBePickedUpByIdleVehicles(graphRTV, new HashSet<>(unassignedVehicles), u) : "Other vehicles could pick up user.";
            assert graphRTV.containsVertex(u) : "There is no valid visit for user " + u + " but it still in graph." + graphRTV.edgesOf(u);
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
    public ResultAssignment getAssignedUsersGreedy(GraphRTV graphRTV) {

        ResultAssignment result = new ResultAssignment(this.timeWindow);


        System.out.println(graphRTV.getVisitCountSetVertex() + " = " + graphRTV.getFeasibleVisitCount());

        System.out.println("----------- Assigning vehicles carrying passengers");
        // Vehicles carrying passengers MUST continue carrying them.
        result = greedyAssignmentVehiclesCarryingPassengers(graphRTV, result);

        System.out.println("---------- Assigning flexible vehicles");
        // Vehicles assigned to requests only are flexible to have their visits completely changed
        result = greedyAssignmentFlexibleVehicles(graphRTV, result);

        result = fixDisplaced(graphRTV, result);

        // Update unassigned vehicles that were previously carrying users.
        // Some vehicles might have lost users but were later associated to new visits (are in vehiclesOK).
        result.vehiclesDisrupted.removeAll(result.getVehiclesOK());

        return result;
    }

    private boolean userCannotBePickedUpByIdleVehicles(GraphRTV graphRTV, Set<Vehicle> unassignedVehicles, User u) {

        for (DefaultEdge edge : graphRTV.edgesOf(u)) {
            Vehicle v = ((Visit) graphRTV.getEdgeTarget(edge)).getVehicle();
            if (unassignedVehicles.contains(v)) {
                System.out.println(unassignedVehicles);
                System.out.println(String.format("Free vehicle %s can service request %s: Visit = %s", v, u, v.getVisit()));
                return false;
            }
        }

        for (Vehicle v : unassignedVehicles) {
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

}