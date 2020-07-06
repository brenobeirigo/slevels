package simulation;

import config.Config;
import config.Qos;
import gurobi.*;
import model.User;
import model.Vehicle;
import model.Visit;
import model.graph.GraphRTV;
import org.jgrapht.graph.DefaultEdge;

import java.util.*;
import java.util.stream.Collectors;

public class MatchingOptimal implements RideMatchingStrategy {

    //TODO make arguments
    final int SL_VIOLATION_PENALTY = 1000;
    final int REJECTION_PENALTY = 10000;
    final int BAD_SERVICE_PENALTY = 10;
    final int VEHICLE_CAPACITY = 4;
    final double MIP_TIME_LIMIT = 15;
    final double MIP_GAP = 0.01;
    final int SQ_USER_REJECTED = 0;
    GraphRTV graphRTV;

    ResultAssignment result;
    // Model
    private GRBEnv env;
    private GRBModel model;
    private List<Visit> visits;
    private List<User> requests;
    // Some vehicles cannot access
    private List<Vehicle> vehicles;
    private Map<Visit, Integer> visitIndex;
    private Map<User, Integer> requestIndex;
    // Assignment variables: x[r][v] == 1 if request r is assigned to trip v
    private GRBVar[] varVisitSelected;
    private GRBVar[] varRequestRejected;
    private GRBVar[] varFirstTierMet;
    private GRBVar[] varSecondTierMet;
    private GRBVar[] varClassServiceLevelViolation;
    private int[] nOfRequestsPerClass;

    /*public void assertInputState(){
        //assert thereAreNoRepeatedRequests(requests) : "There are repeated elements in request list!";
        //assert allVehicleVisitsAreValid() : "Invalid visits found.";
        //assert eachUserIsAssignedToSingleVehicle() : "User is assigned to two different vehicles.";
    }*/

    @Override
    public ResultAssignment match(int currentTime, List<User> unassignedRequests, List<Vehicle> listVehicles, Matching configMatching) {

        buildGraphRTV(unassignedRequests, listVehicles);

        result = new ResultAssignment(currentTime);

        if (this.requests.isEmpty())
            return result;

        try {

            createModel();
            initVarsStandard();
            //initVarsSL();
            //initVars();
            setupVehicleConservationConstraints();
            setupRequestConservationConstraints();
            //setupRequestServiceLevelConstraints();
            //setupClassTargetServiceLevelConstraints();
            //setupRequestStatusConstraints();
            //setupObjective();
            setupDelayObjective();

            // model.write(String.format("round_mip_model/assignment_%d.lp", currentTime));
            model.optimize();

            int status = model.get(GRB.IntAttr.Status);
            if (status == GRB.Status.OPTIMAL || status == GRB.Status.TIME_LIMIT) {

                if (status == GRB.Status.TIME_LIMIT) {
                    System.out.println("TIME LIMIT11111!!!!!!!");
                }

                /*if (config.showInfo) {
                    System.out.println("The optimal objective is " + model.get(GRB.DoubleAttr.ObjVal));
                    System.out.println("Optimal rebalancing:");
                }*/

                extractResult();
                //extractResultSL();
                // env.set(GRB.IntParam.OutputFlag, 0);
//            } else if (status == GRB.Status.TIME_LIMIT) {
//                System.out.println("TIME LIMIT!!!!!!!");
//            }
            } else {
                computeIIS();
            }


            // Dispose of model and environment
            model.dispose();
            env.dispose();

        } catch (GRBException e) {
            System.out.println("Error code: " + e.getErrorCode() + ". " + e.getMessage());
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

    private void buildGraphRTV(List<User> unassignedRequests, List<Vehicle> listVehicles) {
        // Consider all requests (assigned and unassigned)
        List<User> requests = new ArrayList<>(unassignedRequests);
        requests.addAll(Vehicle.getAssignedRequestsFrom(listVehicles));

        // BUILDING GRAPH STRUCTURE ////////////////////////////////////////////////////////////////////////////////////
        this.graphRTV = new GraphRTV(requests, listVehicles, VEHICLE_CAPACITY);
        // To assure every vehicle is assigned to a visit, create dummy stop visits.
        this.graphRTV.addStopVisits();


        this.visits = graphRTV.getAllVisits();
        this.requests = graphRTV.getAllRequests();
        this.vehicles = graphRTV.getListVehiclesFromRTV();
    }

    private void computeIIS() throws GRBException {
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

    private void setupObjective() throws GRBException {

        Map<Qos, GRBLinExpr> penObjectives = new HashMap<>();

        // Sort QoS reverse order = C, B, A
        List<String> sortedQos = Config.getInstance().qosDic.values().stream()
                .map(qos -> qos.id)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        // Violation penalty
        for (Qos qos : Config.getInstance().qosDic.values()) {
            penObjectives.put(qos, new GRBLinExpr());
            penObjectives.get(qos).addTerm(SL_VIOLATION_PENALTY, varClassServiceLevelViolation[qos.code]);
        }

        /*for (Visit visit : visits) {
            obj1.addTerm(visit.getDelay(), varVisitSelected(visit));
        }*/

        for (User request : requests) {
            penObjectives.get(request.qos).addTerm(REJECTION_PENALTY, varRequestRejected(request));
            penObjectives.get(request.qos).addTerm(BAD_SERVICE_PENALTY, varRequestServiceLevelNotAchieved(request));
        }

        for (int i = 0; i < sortedQos.size(); i++) {
            String classLabel = sortedQos.get(i);
            model.setObjectiveN(penObjectives.get(Config.getInstance().qosDic.get(classLabel)), i, i, 1.0, 0.0, 0.0, "OBJ_" + classLabel);
        }

        // The objective is to minimize the total pay costs
        model.set(GRB.IntAttr.ModelSense, GRB.MINIMIZE);
    }

    private void setupDelayObjective() throws GRBException {

        // Set primary objective
        //GRBLinExpr obj = new GRBLinExpr();

        /*for (Visit visit : visits) {
            obj.addTerm(visit.getDelay(), varVisitSelected(visit));
        }*/

        /*for (User request : requests)
            obj.addTerm(REJECTION_PENALTY, varRequestRejected(request));

        model.setObjectiveN(obj, 0, 0, 1.0, 0.0, 0.0, "OBJ_DELAY");

        model.set(GRB.IntAttr.ModelSense, GRB.MINIMIZE);

        model.setObjective(obj, GRB.MINIMIZE);*/

        model.set(GRB.IntAttr.ModelSense, GRB.MINIMIZE);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // VAR ACCESS BY ID ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private GRBVar varRequestRejected(User request) {
        return varRequestRejected[requestIndex.get(request)];
    }

    private GRBVar varRequestServiceLevelAchieved(User request) {
        return varFirstTierMet[requestIndex.get(request)];
    }

    private GRBVar varRequestServiceLevelNotAchieved(User request) {
        return varSecondTierMet[requestIndex.get(request)];
    }

    private GRBVar varVisitSelected(Visit visit) {
        return varVisitSelected[visitIndex.get(visit)];
    }

    private void addIsRejectedVar(User request) throws GRBException {
        varRequestRejected[requestIndex.get(request)] = model.addVar(0, 1, REJECTION_PENALTY, GRB.BINARY, "y_REJECTED_" + request.toString().trim());
    }

    private void addIsTargetServiceLevelMetVar(User request) throws GRBException {
        varFirstTierMet[requestIndex.get(request)] = model.addVar(0, 1, 1, GRB.BINARY, "w_SL1_MET_" + request.toString().trim());
    }

    private void addIsTargetServiceLevelNotMetVar(User request) throws GRBException {
        varSecondTierMet[requestIndex.get(request)] = model.addVar(0, 1, 1, GRB.BINARY, "z_SL2_MET_" + request.toString().trim());
    }

    private void addClassServiceLevelSlack(Qos qos, int userCount) throws GRBException {
        varClassServiceLevelViolation[qos.code] = model.addVar(0, userCount, 1, GRB.INTEGER, "slack_SL2" + qos.id);
    }

    private void addIsVisitChosenVar(Visit visit) throws GRBException {
        String label = String.format("x_%s", visit.getVarId());
        varVisitSelected[visitIndex.get(visit)] = model.addVar(0, 1, visit.getDelay(), GRB.BINARY, label);
    }

    private void initVars() throws GRBException {
        // Model
        env = new GRBEnv();

        // Turn off logging
        // env.set(GRB.IntParam.OutputFlag, 0);

        model = new GRBModel(env);
        model.set(GRB.StringAttr.ModelName, "assignment_rtv");
        model.set(GRB.DoubleParam.TimeLimit, MIP_TIME_LIMIT);
        model.set(GRB.DoubleParam.MIPGap, MIP_GAP);

        visits = graphRTV.getAllVisits();
        requests = graphRTV.getAllRequests();

        // Some vehicles cannot access
        vehicles = graphRTV.getListVehiclesFromRTV();

        for (Vehicle vehicle : vehicles) {
            assert !graphRTV.getListOfVisitsFromVehicle(vehicle).isEmpty() : "Vehicle is disconnected!" + vehicle;
        }

        System.out.println(requests);

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

        varFirstTierMet = new GRBVar[requests.size()];
        varSecondTierMet = new GRBVar[requests.size()];
        varClassServiceLevelViolation = new GRBVar[requests.size()];

        nOfRequestsPerClass = new int[Config.getInstance().getQosCount()];

        for (User request : requests) {
            addIsRejectedVar(request);
            addIsTargetServiceLevelMetVar(request);
            addIsTargetServiceLevelNotMetVar(request);
            nOfRequestsPerClass[request.getQoSCode()]++;
        }

        for (Qos qos : Config.getInstance().qosDic.values()) {
            addClassServiceLevelSlack(qos, nOfRequestsPerClass[qos.code]);
        }

        for (Visit visit : visits) {
            addIsVisitChosenVar(visit);
        }
    }

    private void createModel() throws GRBException {

        // Model
        env = new GRBEnv();

        // Turn off logging
        // env.set(GRB.IntParam.OutputFlag, 0);

        model = new GRBModel(env);
        model.set(GRB.StringAttr.ModelName, "assignment_rtv");
        model.set(GRB.DoubleParam.TimeLimit, MIP_TIME_LIMIT);
        model.set(GRB.DoubleParam.MIPGap, MIP_GAP);

        for (Vehicle vehicle : vehicles) {
            assert !graphRTV.getListOfVisitsFromVehicle(vehicle).isEmpty() : "Vehicle is disconnected!" + vehicle;
        }
    }

    private void initVarsStandard() throws GRBException {

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
        nOfRequestsPerClass = new int[Config.getInstance().getQosCount()];

        for (User request : requests) {
            addIsRejectedVar(request);
            nOfRequestsPerClass[request.getQoSCode()]++;
        }

        for (Visit visit : visits) {
            addIsVisitChosenVar(visit);
        }
    }

    private void initVarsSL() throws GRBException {

        varFirstTierMet = new GRBVar[requests.size()];
        varSecondTierMet = new GRBVar[requests.size()];
        varClassServiceLevelViolation = new GRBVar[requests.size()];

        for (User request : requests) {
            addIsTargetServiceLevelMetVar(request);
            addIsTargetServiceLevelNotMetVar(request);
        }

        for (Qos qos : Config.getInstance().qosDic.values()) {
            addClassServiceLevelSlack(qos, nOfRequestsPerClass[qos.code]);
        }

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // CONSTRAINTS /////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void extractResultSL() throws GRBException {

        for (Qos qos : Config.getInstance().qosDic.values()) {
            double unmetService = varClassServiceLevelViolation[qos.code].get(GRB.DoubleAttr.X);
            result.unmetServiceLevelClass.put(qos, (int) unmetService);
            result.totalServiceLevelClass.put(qos, nOfRequestsPerClass[qos.code]);
        }

        for (User request : requests) {

            if (varRequestServiceLevelAchieved(request).get(GRB.DoubleAttr.X) > 0.99) {
                result.requestsServicedLevelAchieved.add(request);
                // System.out.println(request + " - ACHIEVED");
            }

            if (varRequestServiceLevelNotAchieved(request).get(GRB.DoubleAttr.X) > 0.99) {
                result.requestsServicedLevelNotAchieved.add(request);
                // System.out.println(request + " - NOT ACHIEVED");
            }
        }
    }

    private void extractResult() throws GRBException {


        for (User request : requests) {

            if (varRequestRejected(request).get(GRB.DoubleAttr.X) > 0.99) {
                result.requestsUnassigned.add(request);
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

    private void setupRequestConservationConstraints() throws GRBException {
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

    private boolean isFirstTier(User request, Visit visit) {

        double pickupDelay = graphRTV.getWeightFromRequestVisitEdge(request, visit);
        return request.isDelayFirstTier(pickupDelay);
    }

    private void setupRequestServiceLevelConstraints() throws GRBException {


        for (User request : requests) {

            GRBLinExpr constrRequestServiceLevel = new GRBLinExpr();

            List<Visit> requestVisits = graphRTV.getListOfVisitsFromUser(request);

            for (Visit visit : requestVisits) {
                if (isFirstTier(request, visit)) {
                    constrRequestServiceLevel.addTerm(1, varVisitSelected(visit));
                }
            }

            constrRequestServiceLevel.addTerm(-1, varRequestServiceLevelAchieved(request));
            String label = String.format("first_tier_visits_request_%s", request.toString().trim());
            model.addConstr(constrRequestServiceLevel, GRB.EQUAL, 0, label);

        }
    }

    private void setupRequestStatusConstraints() throws GRBException {
        for (User request : requests) {

            GRBLinExpr constrRequestServiceLevelNotAchieved = new GRBLinExpr();
            constrRequestServiceLevelNotAchieved.addTerm(1, varRequestServiceLevelNotAchieved(request));
            constrRequestServiceLevelNotAchieved.addTerm(1, varRequestServiceLevelAchieved(request));
            constrRequestServiceLevelNotAchieved.addTerm(1, varRequestRejected(request));

            model.addConstr(constrRequestServiceLevelNotAchieved, GRB.EQUAL, 1, "second_tier_" + request.toString().trim());
        }
    }

    private void setupVehicleConservationConstraints() throws GRBException {
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

    private void setupClassTargetServiceLevelConstraints() {

        GRBLinExpr[] constrFirstClass = new GRBLinExpr[Config.getInstance().getQosCount()];
        for (int i = 0; i < Config.getInstance().getQosCount(); i++) {
            constrFirstClass[i] = new GRBLinExpr();
        }

        // Sum of all user that got first tier service levels of each class
        for (User request : requests) {
            GRBVar slAchieved = varRequestServiceLevelAchieved(request);
            if (slAchieved != null)
                constrFirstClass[request.getQoSCode()].addTerm(1, slAchieved);
        }

        Config.getInstance().qosDic.forEach((s, qos) -> {

            // Add slack to each service level class (i.e., number of user who received second tier or were rejected)
            constrFirstClass[qos.code].addTerm(1, varClassServiceLevelViolation[qos.code]);

            // Number of picked up users has to be higher than service rate promised
            try {
                model.addConstr(
                        constrFirstClass[qos.code],
                        GRB.GREATER_EQUAL,
                        (int) Math.ceil(qos.serviceRate * nOfRequestsPerClass[qos.code]),
                        String.format("class_%s", qos.id));
            } catch (GRBException e) {
                e.printStackTrace();
            }
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // ASSERTIONS //////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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
