package simulation;

import gurobi.*;
import model.User;
import model.Vehicle;
import model.Visit;
import model.graph.GraphRTV;
import model.graph.GraphRV;
import org.jgrapht.graph.DefaultEdge;

import java.util.*;
import java.util.stream.Collectors;

public class MatchingOptimal implements RideMatchingStrategy {


    @Override
    public ResultAssignment match(int currentTime, List<User> unassignedRequests, List<Vehicle> listVehicles, Matching configMatching) {


        // Consider all requests (assigned and unassigned)
        List<User> requests = new ArrayList<>(unassignedRequests);
        requests.addAll(Vehicle.getAssignedRequestsFrom(listVehicles));

        //assert thereAreNoRepeatedRequests(requests) : "There are repeated elements in request list!";
        //assert allVehicleVisitsAreValid() : "Invalid visits found.";
        //assert eachUserIsAssignedToSingleVehicle() : "User is assigned to two different vehicles.";

        // BUILDING GRAPH STRUCTURE ////////////////////////////////////////////////////////////////////////////////////
        GraphRTV graphRTV = new GraphRTV(requests, listVehicles, 4);

        // ASSIGNMENT //////////////////////////////////////////////////////////////////////////////////////////////////

        //this.runTimes.put(Solution.TIME_MATCHING, System.nanoTime());
        //ResultAssignment result = getAssignedUsersGreedy(graphRTV);
        ResultAssignment result = getAssignedUsersOptimal(graphRTV);
        //this.runTimes.put(Solution.TIME_MATCHING, System.nanoTime() - this.runTimes.get(Solution.TIME_MATCHING));
        //System.out.println(String.format("Users assigned (%.2f sec)", this.runTimes.get(Solution.TIME_MATCHING) / 1000000000.0));

        // Implement solutions
        //result.getVisitsOK().forEach(this::realizeVisit);
        result.printRoundResult();

        //assert allVehicleVisitsAreValid() : "Invalid visits found.";
        //assert eachUserIsAssignedToSingleVehicle() : "User is assigned to two different vehicles.";
        //assert allPassengersAreAssigned(): "Vehicle carrying passenger is not matched.";

        return result;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // OPTIMAL /////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public ResultAssignment getAssignedUsersOptimal(GraphRTV graphRTV) {

        ResultAssignment result = new ResultAssignment();

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
            //System.out.println(String.format("ROUND = %s - The model is infeasible; computing IIS", this.roundCount));
            model.computeIIS();
            System.out.println("\nThe following constraint(s) cannot be satisfied:");
            for (GRBConstr c : model.getConstrs()) {
                if (c.get(GRB.IntAttr.IISConstr) == 1) {
                    System.out.println(c.get(GRB.StringAttr.ConstrName));
                }
            }

            // Save problem
            //model.write(String.format("round_mip_model/assignment_5%d.lp", this.roundCount));

            graphRTV.printDetailedVisitsLevel();

        } catch (GRBException e) {
            System.out.println("Error code: " + e.getErrorCode() + ". " +
                    e.getMessage());
        }
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
