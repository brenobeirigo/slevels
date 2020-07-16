package simulation.matching;

import gurobi.*;
import model.User;
import model.Vehicle;
import model.Visit;
import model.graph.GraphRTV;
import org.jgrapht.graph.DefaultWeightedEdge;
import simulation.Method;
import simulation.rebalancing.Rebalance;

import java.util.*;
import java.util.stream.Collectors;

public class MatchingOptimal implements RideMatchingStrategy {


    protected int maxVehicleCapacityRTV;
    protected double timeoutVehicleRTV;
    protected double mipTimeLimit;
    protected double mipGap;
    protected int maxEdgesRV;
    protected int rejectionPenalty;
    // Model
    protected GRBEnv env;
    protected GRBModel model;
    protected List<Visit> visits;
    protected List<User> requests;
    // Some vehicles cannot access
    protected List<Vehicle> vehicles;
    protected Map<Visit, Integer> visitIndex;
    protected Map<User, Integer> requestIndex;
    // Assignment variables: x[r][v] == 1 if request r is assigned to trip v
    protected GRBVar[] varVisitSelected;
    protected GRBVar[] varRequestRejected;
    GraphRTV graphRTV;
    ResultAssignment result;

    public MatchingOptimal(int maxVehicleCapacityRTV, double mipTimeLimit, double timeoutVehicleRTV, double mipGap, int maxEdgesRV, int rejectionPenalty) {
        this.maxVehicleCapacityRTV = maxVehicleCapacityRTV;
        this.mipTimeLimit = mipTimeLimit;
        this.timeoutVehicleRTV = timeoutVehicleRTV;
        this.mipGap = mipGap;
        this.maxEdgesRV = maxEdgesRV;
        this.rejectionPenalty = rejectionPenalty;
    }

    /*public void assertInputState(){
        //assert thereAreNoRepeatedRequests(requests) : "There are repeated elements in request list!";
        //assert allVehicleVisitsAreValid() : "Invalid visits found.";
        //assert eachUserIsAssignedToSingleVehicle() : "User is assigned to two different vehicles.";
    }*/

    @Override
    public ResultAssignment match(int currentTime, List<User> unassignedRequests, List<Vehicle> listVehicles, Set<Vehicle> hired, Matching configMatching) {

        buildGraphRTV(unassignedRequests, listVehicles, this.maxVehicleCapacityRTV, timeoutVehicleRTV);

        result = new ResultAssignment(currentTime);

        if (this.requests.isEmpty())
            return result;

        try {

            createModel();
            initVarsStandard();
            setupVehicleConservationConstraints();
            setupRequestConservationConstraints();
            setupDelayObjective();

            // model.write(String.format("round_mip_model/assignment_%d.lp", currentTime));
            model.optimize();

            int status = model.get(GRB.IntAttr.Status);
            if (status == GRB.Status.OPTIMAL || status == GRB.Status.TIME_LIMIT) {

                if (status == GRB.Status.TIME_LIMIT) {
                    System.out.println(String.format("## TIME LIMIT REACHED = %.2f seconds - Solution count: %s", mipTimeLimit, model.get(GRB.IntAttr.SolCount)));
                }

                /*if (config.showInfo) {
                    System.out.println("The optimal objective is " + model.get(GRB.DoubleAttr.ObjVal));
                    System.out.println("Optimal simulation.rebalancing:");
                }*/

                extractResult();

            } else {
                computeIIS();
            }
        } catch (GRBException e) {
            System.out.println("TIME IS OVER - No solution found, keep previous assignment. Gurobi error code: " + e.getErrorCode() + ". " + e.getMessage());
            keepPreviousAssignement();
        } finally {
            // Dispose of model and environment
            model.dispose();
            try {
                env.dispose();
            } catch (GRBException e) {
                e.printStackTrace();
            }
        }


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

    @Override
    public void realize(Set<Visit> visits, Rebalance rebalanceUtil, int currentTime) {
        visits.forEach(visit->Visit.realize(visit, rebalanceUtil, currentTime));
    }

    protected void keepPreviousAssignement() {

        result.getRequestsUnassigned().addAll(User.getUnassigned(requests));

        // Keep the previously picked up requests
        List<User> serviced = User.getAssigned(requests);
        serviced.forEach(user -> result.addVisit(user.getCurrentVisit()));

    }

    protected void buildGraphRTV(List<User> unassignedRequests, List<Vehicle> listVehicles, int maxVehicleCapacity, double timeoutVehicle) {

        // BUILDING GRAPH STRUCTURE ////////////////////////////////////////////////////////////////////////////////////
        this.graphRTV = new GraphRTV(unassignedRequests, listVehicles, maxVehicleCapacity, timeoutVehicle);
        // To assure every vehicle is assigned to a visit, create dummy stop visits.
        this.graphRTV.addStopVisits();


        this.visits = graphRTV.getAllVisits();
        this.requests = graphRTV.getAllRequests();
        this.vehicles = graphRTV.getListVehiclesFromRTV();
    }

    protected void computeIIS() throws GRBException {
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
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // OBJECTIVE ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    protected void setupDelayObjective() throws GRBException {

        // Set primary objective
        //GRBLinExpr obj = new GRBLinExpr();

        /*for (Visit visit : visits) {
            obj.addTerm(visit.getDelay(), varVisitSelected(visit));
        }*/

        /*for (User request : requests)
            obj.addTerm(rejectionPenalty, varRequestRejected(request));

        model.setObjectiveN(obj, 0, 0, 1.0, 0.0, 0.0, "OBJ_DELAY");

        model.set(GRB.IntAttr.ModelSense, GRB.MINIMIZE);

        model.setObjective(obj, GRB.MINIMIZE);*/

        model.set(GRB.IntAttr.ModelSense, GRB.MINIMIZE);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // VAR ACCESS BY ID ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    protected GRBVar varRequestRejected(User request) {
        return varRequestRejected[requestIndex.get(request)];
    }

    protected GRBVar varVisitSelected(Visit visit) {
        return varVisitSelected[visitIndex.get(visit)];
    }

    protected void addIsRejectedVar(User request) throws GRBException {
        varRequestRejected[requestIndex.get(request)] = model.addVar(0, 1, rejectionPenalty, GRB.BINARY, "y_REJECTED_" + request.toString().trim());
    }

    protected void addIsVisitChosenVar(Visit visit) throws GRBException {
        String label = String.format("x_%s", visit.getVarId());
        varVisitSelected[visitIndex.get(visit)] = model.addVar(0, 1, visit.getDelay(), GRB.BINARY, label);
    }

    protected void createModel() throws GRBException {

        // Model
        env = new GRBEnv();

        // Turn off logging
        // env.set(GRB.IntParam.OutputFlag, 0);

        model = new GRBModel(env);
        model.set(GRB.StringAttr.ModelName, "assignment_rtv");
        model.set(GRB.DoubleParam.TimeLimit, mipTimeLimit);
        model.set(GRB.DoubleParam.MIPGap, mipGap);

        for (Vehicle vehicle : vehicles) {
            assert !graphRTV.getListOfVisitsFromVehicle(vehicle).isEmpty() : "Vehicle is disconnected!" + vehicle;
        }
    }

    protected void initVarsStandard() throws GRBException {

        visitIndex = new HashMap<>();
        for (int i = 0; i < visits.size(); i++) {
            visitIndex.put(visits.get(i), i);
        }

        requestIndex = new HashMap<>();
        for (int i = 0; i < requests.size(); i++) {
            requestIndex.put(requests.get(i), i);
        }

        // Assignment variables: x[r][v] == 1 if request r is assigned to trip v
        varVisitSelected = new GRBVar[visits.size()];
        varRequestRejected = new GRBVar[requests.size()];

        for (User request : requests) {
            addIsRejectedVar(request);
        }

        for (Visit visit : visits) {
            addIsVisitChosenVar(visit);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // CONSTRAINTS /////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void setupVehicleConservationConstraints() throws GRBException {
        for (Vehicle vehicle : vehicles) {

            GRBLinExpr constrVehicleConservation = new GRBLinExpr();

            for (Visit visit : graphRTV.getListOfVisitsFromVehicle(vehicle)) {
                constrVehicleConservation.addTerm(1, varVisitSelected(visit));
            }
            // A target can be visited by at most one vehicle

            model.addConstr(
                    constrVehicleConservation,
                    GRB.EQUAL, //vehicle.isCarryingPassengers() ? GRB.EQUAL : GRB.LESS_EQUAL,
                    1,
                    vehicle.toString().trim());
        }
    }

    protected void setupRequestConservationConstraints() throws GRBException {
        for (User request : requests) {

            GRBLinExpr constrRequestConservation = new GRBLinExpr();
            GRBLinExpr constrRequestPreviouslyAssignedHaveToBeServiced = new GRBLinExpr();

            List<Visit> requestVisits = graphRTV.getListOfVisitsFromUser(request);

            for (Visit visit : requestVisits) {

                constrRequestConservation.addTerm(1, varVisitSelected(visit));

                if (request.isPreviouslyAssigned()) {
                    constrRequestPreviouslyAssignedHaveToBeServiced.addTerm(1, varVisitSelected(visit));
                }
            }

            constrRequestConservation.addTerm(1, varRequestRejected(request));

            // Requests are associated with only one visit
            //model.addConstr(constrRequestServiceLevelConservation, GRB.EQUAL, 1, request.toString().trim());
            model.addConstr(constrRequestConservation, GRB.EQUAL, 1, "conservation_" + request.toString().trim());

            if (request.isPreviouslyAssigned()) {
                String label = String.format("request_previously_assigned_is_serviced_%s", request.toString().trim());
                model.addConstr(constrRequestPreviouslyAssignedHaveToBeServiced, GRB.EQUAL, 1, label);
            }
        }
    }

    protected void extractResult() throws GRBException {

        for (User request : requests) {

            if (varRequestRejected(request).get(GRB.DoubleAttr.X) > 0.99) {
                result.getRequestsUnassigned().add(request);
                // System.out.println(request + " - REJECTED");

                // Rejected user was displaced from a routing plan
                if (request.getCurrentVisit() != null) {
                    request.setCurrentVisit(null);
                    result.requestsDisplaced.add(request);
                }
            }
        }

        for (Visit visit : visits) {

            if (varVisitSelected(visit).get(GRB.DoubleAttr.X) > 0.99) {
                result.addVisit(visit);
            }
        }

        // Update unassigned vehicles that were previously carrying users.
        // Some vehicles might have lost users but were later associated to new visits (are in vehiclesOK).
        result.vehiclesDisrupted.removeAll(result.getVehiclesOK());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // ASSERTIONS //////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    protected boolean userCannotBePickedUpByIdleVehicles(GraphRTV graphRTV, Set<Vehicle> unassignedVehicles, User u) {

        for (DefaultWeightedEdge edge : graphRTV.edgesOf(u)) {
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

    protected boolean usersAreNotDisplaced(List<User> unassignedUsers) {
        List<User> displacedUsers = unassignedUsers.stream().filter(user -> user.getCurrentVisit() != null).collect(Collectors.toList());
        if (!displacedUsers.isEmpty()) {
            for (User displacedUser : displacedUsers) {
                System.out.println(displacedUser + " - " + displacedUser.getCurrentVisit());
            }
            return false;
        }
        return true;
    }

    public List<User> getRequests() {
        return requests;
    }

    @Override
    public String toString() {
        return "_OPT-JAVIER";
    }
}
