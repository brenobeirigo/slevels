package model;

import config.Config;
import config.Qos;
import dao.Dao;
import gurobi.*;
import helper.Runtime;
import simulation.matching.Objective;
import simulation.matching.ResultAssignment;

import java.util.*;
import java.util.stream.Collectors;

public class AssignmentILP {

    // Model input
    private final double mipGap = 0.0001;
    private final int mipTimeLimit = 240;
    private final int rejectionPenalty = 1;
    private final String[] orderedListOfObjectiveLabels = new String[]{Objective.TOTAL_REJECTION, Objective.TOTAL_WAITING};

    private int varVisitId = 0;
    protected GRBEnv env;
    protected GRBModel model;
    protected Set<VisitObj> visits;
    protected Set<User> requests;

    // Some vehicles cannot access
    protected Set<Vehicle> vehicles;
    protected Map<VisitObj, Integer> visitIndex;
    protected Map<User, Integer> requestIndex;

    // Assignment variables: x[r][v] == 1 if request r is assigned to trip v
    protected GRBVar[] varVisitSelected;
    protected GRBVar[] varRequestRejected;
    protected int currentTime;
    protected Map<String, GRBLinExpr> penObjectives;
    public Map<Vehicle, Set<VisitObj>> vehicleVisitsMap;
    public Map<User, Set<VisitObj>> userVisitsMap;

    ResultAssignment result;
    public double objValTotalRejected;
    public double objValTotalServiced;
    public double objValueTotalWaiting;

    public AssignmentILP(Map<Vehicle, Set<VisitObj>> vehicleVisitsMap, Map<User, Set<VisitObj>> userVisitsMap) {
        this.penObjectives = new LinkedHashMap<>();
        this.vehicleVisitsMap = vehicleVisitsMap;
        this.userVisitsMap = userVisitsMap;
        this.visits = vehicleVisitsMap.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
        this.requests = userVisitsMap.keySet();
        this.vehicles = vehicleVisitsMap.keySet();
    }

    public AssignmentILP(Map<Vehicle, Set<VisitObj>> vehicleVisitsMap, Set<User> requests) {

        this.penObjectives = new LinkedHashMap<>();
        this.vehicleVisitsMap = vehicleVisitsMap;
        this.userVisitsMap = extractUserVisitsMap(vehicleVisitsMap, requests);

        this.visits = vehicleVisitsMap.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
        this.requests = userVisitsMap.keySet();
        this.vehicles = vehicleVisitsMap.keySet();
    }


    public static Map<User, Set<VisitObj>> extractUserVisitsMap(Map<Vehicle, Set<VisitObj>> vehicleVisitsMap, Set<User> requests) {
        Map<User, Set<VisitObj>> userVisits = new HashMap<>();
        requests.forEach(user -> userVisits.put(user, new HashSet<>()));
        vehicleVisitsMap.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()).stream().forEach(a -> {
            for (User u : a.getRequests()) {
                userVisits.get(u).add(a);
            }
        });

        return userVisits;
    }


    public String getVarVisit(VisitObj visit) {
//        if (visit instanceof VisitStop) {
//            return visit.getVehicle().toString().trim() + "_stay_" + visit.getLastVisitedNode().toString().replace(" ", "");
//        } else if (visit instanceof VisitDisplaceAndStop || visit instanceof VisitRelocation) {
//            return String.format("rebalance_%s_%d", getVarNode(visit.getTargetNode()), varVisitId++);
//        } else {
//            return String.format(
//                    "%s_P=[%s]-R[%s]_[%s](%d)",
//                    getVarVehicle(visit.getVehicle()),
//                    visit.getPassengers().stream().map(this::getVarUser).collect(Collectors.joining("_")),
//                    visit.getRequests().stream().map(this::getVarUser).collect(Collectors.joining("_")),
//                    visit.isSetup() ? "S" : "D",
//                    visit.getDelay());
//        }
        return String.valueOf(visit.hashCode());
    }

    protected void addConstraintsStandardAssignment() throws GRBException {
        oneVisitForEveryVehicle();
        eachRequestToOneVehicle();
        previouslyAssignedMustBeServiced();
    }

    protected void saveModel(int currentTime) {
        try {
            model.write(String.format("assignment%s_%d.lp", this.toString(), currentTime));
        } catch (GRBException e) {
            e.printStackTrace();
        }
    }

    protected boolean isTimeLimitReached() throws GRBException {
        return this.model.get(GRB.IntAttr.Status) == GRB.Status.TIME_LIMIT;
    }

    protected boolean isModelOptimal() throws GRBException {
        return this.model.get(GRB.IntAttr.Status) == GRB.Status.OPTIMAL;
    }

    protected void disposeModelEnvironmentAndSave() {

        if (Config.saveRoundMIPInfo())
            saveModel(currentTime);

        // Dispose of model and environment
        model.dispose();
        try {
            env.dispose();
        } catch (GRBException e) {
            e.printStackTrace();
        }
    }

    public ResultAssignment getResult() {
        return result;
    }

    public ResultAssignment run() {

        result = new ResultAssignment(currentTime);
//        if (this.requests.isEmpty()) {
//            return result;
//        }
//
//        // Create post-decision state space object
//        Map<Vehicle, Set<VisitObj>> vehicleVisitsMap = this.graphRTV.getVehicleVisitsMap();
//        StateSpace preDecisionStateSpace = new StateSpace(listVehicles, unassignedRequests, vehicleVisitsMap, Dao.getInstance());
//        DecisionSpaceObject preDecisionSpaceObj = preDecisionStateSpace.getStateObject();
//
//        assert vehiclesVisitsSameOrder(vehicleVisitsMap, preDecisionStateSpace.getVehicleDecisionsMap());
//
//        int timestepSec = Simulation.timeWindow;
//        PostDecisionStateSpace postDecisionStateSpace = new PostDecisionStateSpace(preDecisionStateSpace, timestepSec);
//        DecisionSpaceObject postDecisionSpaceObj = postDecisionStateSpace.getStateObject();
//
//
//        HelperIO.saveJSON(preDecisionSpaceObj, String.format("Input%04d.json", Simulation.rightTW));
//        HelperIO.saveJSON(postDecisionSpaceObj, String.format("Output%04d.json", Simulation.rightTW));

        try {
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // Building the model //////////////////////////////////////////////////////////////////////////////////////
            Dao.getInstance().getRunTimes().startTimerFor(Runtime.TIME_ILP_BUILDING);
            createGurobiModelAndEnvironment();
            initVarsStandardAssignment();
            addConstraintsStandardAssignment();
            setupObjectives();
            Dao.getInstance().getRunTimes().endTimerFor(Runtime.TIME_ILP_BUILDING);

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // Building the model //////////////////////////////////////////////////////////////////////////////////////
            Dao.getInstance().getRunTimes().startTimerFor(Runtime.TIME_ILP_SOLVING);
            model.optimize();
            Dao.getInstance().getRunTimes().endTimerFor(Runtime.TIME_ILP_SOLVING);

            System.out.printf("\n# Matching - ILP - Building time = %.2f", Dao.getInstance().getRunTimes().getExecutionTimeSecFor(Runtime.TIME_ILP_BUILDING));
            System.out.printf("\n# Matching - ILP - Opt. time     = %.2f\n", Dao.getInstance().getRunTimes().getExecutionTimeSecFor(Runtime.TIME_ILP_SOLVING));
            if (isModelOptimal() || isTimeLimitReached()) {

                if (isTimeLimitReached()) {
                    System.out.printf("## TIME LIMIT REACHED = %.2f seconds - Solution count: %s%n", mipTimeLimit, model.get(GRB.IntAttr.SolCount));
                }

                extractObj();
                extractResult();

                assert this.result.getRequestsOK().size() == (int) this.objValTotalServiced;
                assert this.result.getRequestsUnassigned().size() == (int) this.objValTotalRejected;
                assert this.result.getTotalDelay() == this.objValueTotalWaiting: "Total delay:" + this.result.getTotalDelay() + "-- Obj. delay: " + this.objValueTotalWaiting;

            } else {
                computeIIS();
            }
        } catch (GRBException e) {
            System.out.println("# Matching - ILP - TIME IS OVER - No solution found, keep previous assignment. Gurobi error code: " + e.getErrorCode() + ". " + e.getMessage());
            keepPreviousAssignment();
        } finally {
            disposeModelEnvironmentAndSave();
        }


        //this.runTimes.put(Solution.TIME_MATCHING, System.nanoTime() - this.runTimes.get(Solution.TIME_MATCHING));
        //System.out.println(String.format("Users assigned (%.2f sec)", this.runTimes.get(Solution.TIME_MATCHING) / 1000000000.0));

        // Implement solutions
        //result.getVisitsOK().forEach(this::realizeVisit);
        // result.printRoundResult();

        //assert allVehicleVisitsAreValid() : "Invalid visits found.";
        //assert eachUserIsAssignedToSingleVehicle() : "User is assigned to two different vehicles.";
        //assert allPassengersAreAssigned(): "Vehicle carrying passenger is not matched.";

//        System.out.println("##### BEST VISIT");
//        for (VisitObj bestVisit:result.visitsOK) {
//            System.out.println(bestVisit.getVehicleState());
//            System.out.println("Request:" + bestVisit.getRequests().size());
//            System.out.println("Request load:" + bestVisit.getRequestsTotalLoad());
//        }
        return result;
    }

    public void printObj() {
        System.out.println("## INPUT");
        System.out.println("User count: " + this.userVisitsMap.keySet().size());
        System.out.println("  Vehicles: " + this.vehicleVisitsMap.keySet().size());

        System.out.println("## OBJECTIVES");
        for (int i = 0; i < this.orderedListOfObjectiveLabels.length; i++) {

            if (orderedListOfObjectiveLabels[i].equals(Objective.TOTAL_REJECTION)) {
                System.out.println(" - " + Objective.TOTAL_REJECTION + " = " + this.objValTotalRejected + " - Serviced = " + this.objValTotalServiced);

            } else if (orderedListOfObjectiveLabels[i].equals(Objective.TOTAL_WAITING)) {
                System.out.println(" - " + Objective.TOTAL_WAITING + " = " + this.objValueTotalWaiting);
            }
        }
    }

    private void extractObj() throws GRBException {

        // System.out.println("## OBJECTIVES");
        for (int i = 0; i < this.orderedListOfObjectiveLabels.length; i++) {
            int idxObj = this.orderedListOfObjectiveLabels.length - i - 1;
            double valueObj = model.getObjective(idxObj).getValue();
            // System.out.println(" - " + this.orderedListOfObjectiveLabels[i] + " = " + valueObj);
            if (orderedListOfObjectiveLabels[i].equals(Objective.TOTAL_REJECTION)) {
                this.objValTotalRejected = valueObj;
                this.objValTotalServiced = this.requests.size() - valueObj;
            } else if (orderedListOfObjectiveLabels[i].equals(Objective.TOTAL_WAITING)) {
                this.objValueTotalWaiting = valueObj;
            }
        }
    }

    protected void keepPreviousAssignment() {

        result.getRequestsUnassigned().addAll(User.getUnassigned(requests));

        // Keep the previously picked up requests
        List<User> serviced = User.getAssigned(requests);
        serviced.forEach(user -> result.addVisit(user.getCurrentVisit()));

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
        model.write(String.format("IIS_assignment_%s.lp", this.toString()));

        //graphRTV.printDetailedVisitsLevel();
    }

    protected void computeIISReduceUntilCanBeSolved() throws GRBException {
        int status = 0;
        System.out.println("The model is infeasible; computing IIS");
        LinkedList<String> removed = new LinkedList<String>();

        int count = 0;
        // Loop until we reduce to a model that can be solved
        while (true) {
            count++;
            model.computeIIS();
            System.out.println("\nThe following constraint cannot be satisfied:");
            for (GRBConstr c : model.getConstrs()) {
                if (c.get(GRB.IntAttr.IISConstr) == 1) {
                    System.out.println(c.get(GRB.StringAttr.ConstrName));
                    // Remove a single constraint from the model
                    removed.add(c.get(GRB.StringAttr.ConstrName));
                    model.remove(c);
                    break;
                }
            }

            System.out.println();

            model.write(String.format("IIS_assignment_%s_count_%d.lp", this.toString(), count));

            model.optimize();
            status = model.get(GRB.IntAttr.Status);

            if (status == GRB.Status.UNBOUNDED) {
                System.out.println("The model cannot be solved "
                        + "because it is unbounded");
                return;
            }
            if (status == GRB.Status.OPTIMAL) {
                break;
            }
            if (status != GRB.Status.INF_OR_UNBD &&
                    status != GRB.Status.INFEASIBLE) {
                System.out.println("Optimization was stopped with status " +
                        status);
                return;
            }
        }

        System.out.println("\nThe following constraints were removed "
                + "to get a feasible LP:");
        for (String s : removed) {
            System.out.print(s + " ");
        }
        System.out.println();

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // OBJECTIVE ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void addObjective(String objective) {
        switch (objective) {
//            case Objective.HIERARCHICAL_WAITING_AND_REJECTION:
//                objHierarchicalWaitingAndRejection();
//                break;
//            case Objective.HIERARCHICAL_WAITING:
//                objHierarchicalWaiting();
//                break;
            case Objective.HIERARCHICAL_REJECTION:
                objHierarchicalRejection();
                break;
            case Objective.TOTAL_WAITING_AND_REJECTION:
                objTotalWaitingAndRejection();
                break;
            case Objective.TOTAL_REJECTION:
                objRejection();
                break;
            case Objective.TOTAL_WAITING:
                objTotalWaitingTime();
                break;
        }
    }

    protected void setupObjectives() throws GRBException {

        for (String objective : orderedListOfObjectiveLabels) {
            addObjective(objective);
        }
        System.out.println("All objectives: " + penObjectives.keySet());
        addHierarchicalObjectivesToModel();

    }

    protected void addHierarchicalObjectivesToModel() throws GRBException {
        int i = penObjectives.size();

        for (Map.Entry<String, GRBLinExpr> e : penObjectives.entrySet()) {
            i--;
            model.setObjectiveN(e.getValue(), i, i, 1.0, 0.0, 0.0, "OBJ_" + e.getKey());
        }

        // The objective is to minimize the total pay costs
        model.set(GRB.IntAttr.ModelSense, GRB.MINIMIZE);
    }

    protected void objTotalWaitingAndRejection() {

        String label = "TOT_WAIT_REJ";
        penObjectives.put(label, new GRBLinExpr());

        for (VisitObj visit : visits) {
            penObjectives.get(label).addTerm(visit.getDelay(), varVisitSelected(visit));
        }

        for (User request : requests) {
            penObjectives.get(label).addTerm(rejectionPenalty, varRequestRejected(request));
        }
    }


    protected void objHierarchicalRejection() {
        // Sort QoS order = A, B, C
        List<Qos> sortedQos = Config.getInstance().getSortedQosList();

        // Violation penalty
        String label = "H_REJ_";
        for (Qos qos : sortedQos) {
            penObjectives.put(label + qos.id, new GRBLinExpr());
        }

        for (User request : requests) {
            penObjectives.get(label + request.qos.id).addTerm(1, varRequestRejected(request));
        }
    }

    protected void objRejection() {
        // Violation penalty
        String label = "TOT_REJ";
        penObjectives.put(label, new GRBLinExpr());

        for (User request : requests) {
            penObjectives.get(label).addTerm(1, varRequestRejected(request));
        }
    }

    protected void objTotalWaitingTime() {
        String label = "TOT_WAIT";
        penObjectives.put(label, new GRBLinExpr());
        for (VisitObj visit : visits) {
            penObjectives.get(label).addTerm(visit.getDelay(), varVisitSelected(visit));
        }
    }

//    protected void objHierarchicalWaiting() {
//        // Sort QoS order = A, B, C
//        List<Qos> sortedQos = Config.getInstance().getSortedQosList();
//
//        String label = "H_WAIT";
//
//        // Violation penalty
//        for (Qos qos : sortedQos) {
//            penObjectives.put(String.format("%s_%s", label, qos.id), new GRBLinExpr());
//        }
//
//        for (User request : requests) {
//
//            String labelQos = String.format("%s_%s", label, request.qos.id);
//            Set<VisitObj> requestVisits = this.getListOfVisitsFromUser(request);
//            for (VisitObj visit : requestVisits) {
//                double delay = getDelayOfRequestInVisit(request, visit);
//                penObjectives.get(labelQos).addTerm(delay, varVisitSelected(visit));
//            }
//        }
//    }

    Set<VisitObj> getListOfVisitsFromUser(User u) {
        return this.userVisitsMap.get(u);
    }

//    protected void objHierarchicalWaitingAndRejection() {
//        // Sort QoS order = A, B, C
//        List<Qos> sortedQos = Config.getInstance().getSortedQosList();
//
//        String label = "H_WAIT_REJ";
//
//        // Violation penalty
//        for (Qos qos : sortedQos) {
//            penObjectives.put(String.format("%s_%s", label, qos.id), new GRBLinExpr());
//        }
//
//        for (User request : requests) {
//
//            String labelQos = String.format("%s_%s", label, request.qos.id);
//            penObjectives.get(labelQos).addTerm(rejectionPenalty, varRequestRejected(request));
//
//            Set<VisitObj> requestVisits = getListOfVisitsFromRequest(request);
//            for (VisitObj visit : requestVisits) {
//                double delay = getDelayOfRequestInVisit(request, visit);
//                penObjectives.get(labelQos).addTerm(delay, varVisitSelected(visit));
//            }
//        }
//    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // VAR ACCESS BY ID ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    protected GRBVar varRequestRejected(User request) {
        return varRequestRejected[requestIndex.get(request)];
    }

    protected GRBVar varVisitSelected(VisitObj visit) {
        return varVisitSelected[visitIndex.get(visit)];
    }

    protected void addIsRejectedVar(User request) throws GRBException {
//        String label = String.format("x_rejected_%s", request.toString().replace(" ", "_").trim());
        Integer idxRequest = requestIndex.get(request);
        String label = String.format("x_rejected_%d", idxRequest);
        varRequestRejected[idxRequest] = model.addVar(0, 1, 1, GRB.BINARY, label);
    }

    protected void addIsVisitChosenVar(VisitObj visit) throws GRBException {
//        String label = String.format("x_visit_%s", getVarVisit(visit).replace(" ", "_").trim());
        Integer idxVisit = visitIndex.get(visit);
        String label = String.format("x_visit_%d", idxVisit);
        varVisitSelected[idxVisit] = model.addVar(0, 1, visit.getDelay(), GRB.BINARY, label);
    }

    protected void createGurobiModelAndEnvironment() throws GRBException {

        // Model
        env = new GRBEnv();

        // Turn off logging
        // env.set(GRB.IntParam.OutputFlag, 0);

        model = new GRBModel(env);
        model.set(GRB.StringAttr.ModelName, "assignment_rtv");
        model.set(GRB.DoubleParam.TimeLimit, mipTimeLimit);
        model.set(GRB.DoubleParam.MIPGap, mipGap);

    }

    protected void initVarsStandardAssignment() throws GRBException {
        visitIndex = new HashMap<>();
        int i = 0;
        for (VisitObj v : visits) {
            visitIndex.put(v, i);
            i++;
        }

        requestIndex = new HashMap<>();
        int j = 0;
        for (User request : requests) {
            requestIndex.put(request, j);
            j++;
        }

        // Assignment variables: x[r][v] == 1 if request r is assigned to trip v
        varVisitSelected = new GRBVar[visits.size()];
        varRequestRejected = new GRBVar[requests.size()];

        for (User request : requests) {
            addIsRejectedVar(request);
        }

        for (VisitObj visit : visits) {
            addIsVisitChosenVar(visit);
        }
    }

//    protected double getDelayOfRequestInVisit(User request, VisitObj visit) {
//        double weight = graphRTV.getWeightFromRequestVisitEdge(request, visit);
//        return weight;
//    }

    protected Set<VisitObj> getListOfVisitsFromRequest(User request) {
        return this.getListOfVisitsFromUser(request);
    }

    private Set<VisitObj> getListOfVisitsFromVehicle(Vehicle vehicle) {
        return this.vehicleVisitsMap.get(vehicle);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // CONSTRAINTS /////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void oneVisitForEveryVehicle() throws GRBException {
        for (Vehicle vehicle : vehicles) {

            GRBLinExpr constrVehicleConservation = new GRBLinExpr();

            for (VisitObj visit : this.getListOfVisitsFromVehicle(vehicle)) {
                constrVehicleConservation.addTerm(1, varVisitSelected(visit));
            }
            // A target can be visited by at most one vehicle

            String label = String.format("conservation_%s", vehicle.toString().replace(" ", "_").trim());
            model.addConstr(constrVehicleConservation, GRB.EQUAL, 1, label);
        }
    }

    protected void eachRequestToOneVehicle() throws GRBException {
        // Requests are associated with only one visit
        for (User request : requests) {

            Set<VisitObj> requestVisits = this.getListOfVisitsFromUser(request);

            GRBLinExpr constrRequestConservation = new GRBLinExpr();
            for (VisitObj visit : requestVisits) {
                constrRequestConservation.addTerm(1, varVisitSelected(visit));
            }

            if (!request.isPreviouslyAssigned()) {
                constrRequestConservation.addTerm(1, varRequestRejected(request));
            }
            String label = String.format("request_visit_conservation_%d", requestIndex.get(request));
            model.addConstr(constrRequestConservation, GRB.EQUAL, 1, label);
        }
    }

    protected void previouslyAssignedMustBeServiced() throws GRBException {

        for (User request : getPreviouslyAssignedUsers()) {

            Set<VisitObj> requestVisits = this.getListOfVisitsFromUser(request);
            GRBLinExpr constrRequestPreviouslyAssignedHaveToBeServiced = new GRBLinExpr();

            for (VisitObj visit : requestVisits) {
                if (request.isPreviouslyAssigned()) {
                    constrRequestPreviouslyAssignedHaveToBeServiced.addTerm(1, varVisitSelected(visit));
                }
            }

            String label = String.format("request_previously_assigned_is_serviced_%s", requestIndex.get(request));
            model.addConstr(constrRequestPreviouslyAssignedHaveToBeServiced, GRB.EQUAL, 1, label);
        }
    }

    private List<User> getPreviouslyAssignedUsers() {
        return User.filterPreviouslyAssigned(requests);
    }

    protected void extractResult() throws GRBException {

        result.setObjValTotalRejected(this.objValTotalRejected);
        result.setObjValTotalWaiting(this.objValueTotalWaiting);
        result.setObjValTotalServiced(this.objValTotalServiced);

        for (User request : requests) {

            if (isRequestRejected(request)) {
                result.accountRejected(request);
            }
        }

        for (VisitObj visit : visits) {

            if (isVisitSelected(visit)) {
                result.addVisit(visit);
            }
        }

        // Update unassigned vehicles that were previously carrying users.
        // Some vehicles might have lost users but were later associated to new visits (are in vehiclesOK).
        result.getVehiclesDisrupted().removeAll(result.getVehiclesOK());
        assert result.getTotalDelay() == objValueTotalWaiting;
    }

    private boolean isVisitSelected(VisitObj visit) throws GRBException {
        return varVisitSelected(visit).get(GRB.DoubleAttr.X) > 0.99;
    }

    protected boolean isRequestRejected(User request) throws GRBException {
        return varRequestRejected(request).get(GRB.DoubleAttr.X) > 0.99;
    }

    @Override
    public String toString() {
        return "_OPT-JAVIER";
    }
}

