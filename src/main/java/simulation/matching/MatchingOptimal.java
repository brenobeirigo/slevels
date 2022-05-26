package simulation.matching;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import config.Config;
import config.Qos;
import dao.Dao;
import gurobi.*;
import helper.Runtime;
import model.*;
import model.graph.GraphRTV;
import model.graph.ParallelGraphRTV;
import model.learn.StateAction;
import model.node.Node;
import simulation.Method;
import simulation.Simulation;

import java.util.*;
import java.util.stream.Collectors;

public class MatchingOptimal implements RideMatchingStrategy {


    // There might have relocation trips to the same node, this variable helps creating unique labels
    private static int varVisitId = 0;
    protected String PDVisitGenerator;
    protected int maxVehicleCapacityRTV;
    protected double timeoutVehicleRTV;
    protected double mipTimeLimit;
    protected double mipGap;
    protected int maxEdgesRV;
    protected int maxEdgesRR;
    protected int rejectionPenalty;

    // Model
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
    protected String[] orderedListOfObjectiveLabels;
    protected int currentTime;
    protected Map<String, GRBLinExpr> penObjectives;
    GraphRTV graphRTV;
    ResultAssignment result;
    public Map<Vehicle, Set<VisitObj>> vehicleVisitsMap;
    public Map<User, Set<VisitObj>> userVisitsMap;

    public MatchingOptimal(int maxVehicleCapacityRTV, double mipTimeLimit, double timeoutVehicleRTV, double mipGap, int maxEdgesRV, int maxEdgesRR, int rejectionPenalty, String[] orderedListOfObjectiveLabels, String PDVisitGenerator) {
        this.maxVehicleCapacityRTV = maxVehicleCapacityRTV;
        this.mipTimeLimit = mipTimeLimit;
        this.timeoutVehicleRTV = timeoutVehicleRTV;
        this.mipGap = mipGap;
        this.maxEdgesRV = maxEdgesRV;
        this.maxEdgesRR = maxEdgesRR;
        this.rejectionPenalty = rejectionPenalty;
        this.orderedListOfObjectiveLabels = orderedListOfObjectiveLabels;
        this.penObjectives = new LinkedHashMap<>();
        this.PDVisitGenerator = PDVisitGenerator;
    }

    @Override
    public void realizeVisit(VisitObj visit) {

        // Does nothing if same visit chosen (e.g., continue rebalancing)
        if (visit.getVehicle().getVisit() == visit)
            return;

        // Dummy visit for parked or rebalancing vehicle
        if (visit instanceof VisitStop) {
            return;
        }

        // Vehicle drop scheduled requests and stop at the closest node
        if (visit instanceof VisitDisplaceAndStop) {
            visit.getVehicle().setVisit(visit);
            return;
        }

        // If vehicle was rebalancing, compute the rebalancing distance until middle
        if (visit.getVehicle().isRebalancing()) {
            visit.getVehicle().computeDistanceTraveledRebalancingUntilMiddle();
            visit.getVehicle().setStoppedRebalanceToPickup(true);
        }

        // Add visit to vehicle (circular)
        visit.getVehicle().setVisit(visit);

        // Update visit for users in vehicle
        for (User request : Iterables.concat(visit.getRequests(), visit.getPassengers())) {
            request.setCurrentVisit(visit);
        }

        // Go through nodes and update arrival so far
        visit.updateArrivalSoFarAtVisitNodes();

        // Vehicle is not idle
        visit.getVehicle().setRoundsIdle(0);

    }

    public String getVarUser(User user) {
        return user.toString().trim();
    }

    public String getVarNode(Node node) {
        return node.toString().trim();
    }

    public String getVarVehicle(Vehicle vehicle) {
        return vehicle.toString().trim().replace(" ", "_");
    }

    public String getVarVisit(VisitObj visit) {
        if (visit instanceof VisitStop) {
            return visit.getVehicle().toString().trim() + "_stay_" + visit.getLastVisitedNode().toString().replace(" ", "");
        } else if (visit instanceof VisitDisplaceAndStop || visit instanceof VisitRelocation) {
            return String.format("rebalance_%s_%d", getVarNode(visit.getTargetNode()), varVisitId++);
        } else {
            return String.format(
                    "%s_P=[%s]-R[%s]_[%s](%d)",
                    getVarVehicle(visit.getVehicle()),
                    visit.getPassengers().stream().map(this::getVarUser).collect(Collectors.joining("_")),
                    visit.getRequests().stream().map(this::getVarUser).collect(Collectors.joining("_")),
                    visit.isSetup() ? "S" : "D",
                    visit.getDelay());
        }
    }

    @Override
    public ResultAssignment match(int currentTime, Set<User> unassignedRequests, Set<Vehicle> vehicles, Set<Vehicle> hired) {
        buildGraphRTV(unassignedRequests, vehicles, this.maxVehicleCapacityRTV, timeoutVehicleRTV, maxEdgesRV, maxEdgesRR);
        //Map<User, Set<VisitObj>> userVisitMapRTV = graphRTV.getUserVisitsMap();
        Map<Vehicle, Set<VisitObj>> vehicleVisitMapRTV = graphRTV.getVehicleVisitsMap();
        AssignmentILP visitToVehiclesAssignment = new AssignmentILP(currentTime,vehicleVisitMapRTV,  unassignedRequests, true);
        visitToVehiclesAssignment.run(new String[]{Objective.TOTAL_REQUESTS, Objective.TOTAL_WAITING});
        this.result = visitToVehiclesAssignment.getResult();
        //this.result = assign(currentTime, vehicleVisitMapRTV, userVisitMapRTV);

        // assert assertRTVUserVisitMapCanBeReconstructedFromVehicleVisitMap(unassignedRequests, userVisitMapRTV, vehicleVisitMapRTV);
        // assert assertConsecutiveAssignmentsProduceSameResults(unassignedRequests, userVisitMapRTV, vehicleVisitMapRTV);


//        MatchingSimple ms = new MatchingSimple(
//                maxVehicleCapacityRTV,
//                mipTimeLimit,
//                timeoutVehicleRTV,
//                mipGap,
//                maxEdgesRV,
//                maxEdgesRR,
//                rejectionPenalty,
//                orderedListOfObjectiveLabels,
//                PDVisitGenerator);
//
//        ResultAssignment result = ms.match(currentTime, unassignedRequests, vehicles, hired);
//        return result;
        return this.result;
    }

    private boolean assertConsecutiveAssignmentsProduceSameResults(Set<User> unassignedRequests, Map<User, Set<VisitObj>> userVisitMapRTV, Map<Vehicle, Set<VisitObj>> vehicleVisitMapRTV) {
        AssignmentILP a = new AssignmentILP(Simulation.rightTW, vehicleVisitMapRTV, userVisitMapRTV, true);
        AssignmentILP that = new AssignmentILP(Simulation.rightTW, vehicleVisitMapRTV, unassignedRequests, true);
        for (User u : a.userVisitsMap.keySet()) {
            if (!a.userVisitsMap.get(u).isEmpty() && !a.userVisitsMap.get(u).containsAll(that.userVisitsMap.get(u))) {
                System.out.println(a.userVisitsMap.get(u));
                System.out.println(that.userVisitsMap.get(u));
                return false;
            }
        }
        for (Vehicle v : a.vehicleVisitsMap.keySet()) {
            if (!a.vehicleVisitsMap.get(v).isEmpty() && !a.vehicleVisitsMap.get(v).containsAll(that.vehicleVisitsMap.get(v))) {
                return false;
            }
        }
        a.run(new String[]{Objective.TOTAL_REJECTION, Objective.TOTAL_WAITING});
        that.run(new String[]{Objective.TOTAL_REJECTION, Objective.TOTAL_WAITING});
        if (!a.getResult().equals(that.getResult())) {

            System.out.println("# A:");
            a.printObj();
            System.out.println("# B:");
            that.printObj();

            //Objects.equal(unmetServiceLevelClass, that.getResult().unmetServiceLevelClass) &&
            //Objects.equal(nOfRequestsClass, that.getResult().nOfRequestsClass) &&
            //Objects.equal(rejectedServiceLevelClass, that.getResult().rejectedServiceLevelClass &&
            //&& violationCountClassServiceLevel.equals(that.getResult().violationCountClassServiceLevel) &&
            System.out.println(a.getResult().requestsServicedLevelAchieved.equals(that.getResult().requestsServicedLevelAchieved));
            System.out.println(a.getResult().requestsServicedLevelNotAchieved.equals(that.getResult().requestsServicedLevelNotAchieved));
            System.out.println(a.getResult().requestsDisplaced.equals(that.getResult().requestsDisplaced));
            System.out.println(a.getResult().getVehiclesDisrupted().equals(that.getResult().getVehiclesDisrupted()));
            System.out.println(a.getResult().roundPrivateRides.equals(that.getResult().roundPrivateRides));
            System.out.println(a.getResult().getRequestsOK().equals(that.getResult().getRequestsOK()));
            System.out.println(a.getResult().getVehiclesOK().equals(that.getResult().getVehiclesOK()));
            System.out.println(a.getResult().getVisitsOK().equals(that.getResult().getVisitsOK()));
            System.out.println(Sets.difference(a.getResult().getVisitsOK(), that.getResult().getVisitsOK()));
            System.out.println(a.getResult().getRequestsUnassigned().equals(that.getResult().getRequestsUnassigned()));
            System.out.println(Sets.difference(a.getResult().getRequestsUnassigned(), that.getResult().getRequestsUnassigned()));
            System.out.println(a.getResult().getVehiclesHired().equals(that.getResult().getVehiclesHired()));
            return false;
        }
        return true;
    }

    private boolean assertRTVUserVisitMapCanBeReconstructedFromVehicleVisitMap(Set<User> unassignedRequests, Map<User, Set<VisitObj>> userVisitMapRTV, Map<Vehicle, Set<VisitObj>> vehicleVisitMapRTV) {
        Map<User, Set<VisitObj>> userVisitMap = AssignmentILP.extractUserVisitsMap(vehicleVisitMapRTV, unassignedRequests);
        if (!userVisitMapRTV.keySet().containsAll(userVisitMap.keySet())) {
            Set<User> diff = userVisitMap.keySet();
            diff.removeAll(userVisitMapRTV.keySet());
            System.out.printf("Diff: %s", diff);
            return false;
        }
        if (!userVisitMap.keySet().containsAll(userVisitMapRTV.keySet())) {
            Set<User> diff = userVisitMapRTV.keySet();
            diff.removeAll(userVisitMap.keySet());
            System.out.printf("Diff: %s", diff);
            return false;
        }

        for (User u : unassignedRequests) {
            if (!userVisitMapRTV.get(u).containsAll(userVisitMap.get(u)) || !userVisitMap.get(u).containsAll(userVisitMapRTV.get(u))) {
                return false;
            }
        }
        return true;
    }

    private ResultAssignment assign(int currentTime, Map<Vehicle, Set<VisitObj>> vehicleVisitsMap, Map<User, Set<VisitObj>> userVisitsMap) {
        this.vehicleVisitsMap = vehicleVisitsMap;
        this.userVisitsMap = userVisitsMap;
        this.visits = vehicleVisitsMap.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
        this.requests = userVisitsMap.keySet();
        this.vehicles = vehicleVisitsMap.keySet();
        this.result = new ResultAssignment(currentTime);

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

                /*if (config.showInfo) {
                    System.out.println("The optimal objective is " + modbefel.get(GRB.DoubleAttr.ObjVal));
                    System.out.println("Optimal simulation.rebalancing:");
                }*/

                extractResult();

            } else {
                computeIIS();
            }
        } catch (GRBException e) {
            System.out.println("# Matching - ILP - TIME IS OVER - No solution found, keep previous assignment. Gurobi error code: " + e.getErrorCode() + ". " + e.getMessage());
            keepPreviousAssignment();
        } catch (Exception e) {
            System.out.println("XXXXXXX:" + e.getMessage());

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

    @Override
    public void realize(Set<VisitObj> visits) {
        visits.forEach(this::realizeVisit);
    }

    protected void keepPreviousAssignment() {

        result.getRequestsUnassigned().addAll(User.getUnassigned(requests));

        // Keep the previously picked up requests
        List<User> serviced = User.getAssigned(requests);
        serviced.forEach(user -> result.addVisit(user.getCurrentVisit()));

    }

    protected void buildGraphRTV(Set<User> unassignedRequests, Set<Vehicle> listVehicles, int maxVehicleCapacity, double timeoutVehicle, int maxVehReqEdges, int maxReqReqEdges) {

        // BUILDING GRAPH STRUCTURE ////////////////////////////////////////////////////////////////////////////////////
        this.graphRTV = new ParallelGraphRTV(unassignedRequests, listVehicles, maxVehicleCapacity, timeoutVehicle, maxVehReqEdges, maxReqReqEdges);
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
            case Objective.HIERARCHICAL_WAITING_AND_REJECTION:
                objHierarchicalWaitingAndRejection();
                break;
            case Objective.HIERARCHICAL_WAITING:
                objHierarchicalWaiting();
                break;
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

    protected void objHierarchicalWaiting() {
        // Sort QoS order = A, B, C
        List<Qos> sortedQos = Config.getInstance().getSortedQosList();

        String label = "H_WAIT";

        // Violation penalty
        for (Qos qos : sortedQos) {
            penObjectives.put(String.format("%s_%s", label, qos.id), new GRBLinExpr());
        }

        for (User request : requests) {

            String labelQos = String.format("%s_%s", label, request.qos.id);
            Set<VisitObj> requestVisits = this.getListOfVisitsFromUser(request);
            for (VisitObj visit : requestVisits) {
                double delay = getDelayOfRequestInVisit(request, visit);
                penObjectives.get(labelQos).addTerm(delay, varVisitSelected(visit));
            }
        }
    }

    protected void objHierarchicalWaitingAndRejection() {
        // Sort QoS order = A, B, C
        List<Qos> sortedQos = Config.getInstance().getSortedQosList();

        String label = "H_WAIT_REJ";

        // Violation penalty
        for (Qos qos : sortedQos) {
            penObjectives.put(String.format("%s_%s", label, qos.id), new GRBLinExpr());
        }

        for (User request : requests) {

            String labelQos = String.format("%s_%s", label, request.qos.id);
            penObjectives.get(labelQos).addTerm(rejectionPenalty, varRequestRejected(request));

            Set<VisitObj> requestVisits = getListOfVisitsFromRequest(request);
            for (VisitObj visit : requestVisits) {
                double delay = getDelayOfRequestInVisit(request, visit);
                penObjectives.get(labelQos).addTerm(delay, varVisitSelected(visit));
            }
        }
    }

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
        String label = String.format("x_rejected_%s", request.toString().replace(" ", "_").trim());
        varRequestRejected[requestIndex.get(request)] = model.addVar(0, 1, 1, GRB.BINARY, label);
    }

    protected void addIsVisitChosenVar(VisitObj visit) throws GRBException {
        String label = String.format("x_visit_%s", getVarVisit(visit).replace(" ", "_").trim());
        varVisitSelected[visitIndex.get(visit)] = model.addVar(0, 1, visit.getDelay(), GRB.BINARY, label);
    }

    protected void createGurobiModelAndEnvironment() throws GRBException {

        // Model
        env = new GRBEnv();

        if (config.Config.showRoundMIPInfo()){
            // Turn off logging
            env.set(GRB.IntParam.OutputFlag, 0);
        }

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

    protected double getDelayOfRequestInVisit(User request, VisitObj visit) {
        double weight = graphRTV.getWeightFromRequestVisitEdge(request, visit);
        return weight;
    }

    protected Set<VisitObj> getListOfVisitsFromRequest(User request) {
        return this.getListOfVisitsFromUser(request);
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
            String label = String.format("request_visit_conservation_%s", request.toString().replace(" ", "_").trim());
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

            String label = String.format("request_previously_assigned_is_serviced_%s", request.toString().replace(" ", "_").trim());
            model.addConstr(constrRequestPreviouslyAssignedHaveToBeServiced, GRB.EQUAL, 1, label);
        }
    }

    private List<User> getPreviouslyAssignedUsers() {
        return User.filterPreviouslyAssigned(requests);
    }

    protected void extractResult() throws GRBException {

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
        result.vehiclesDisrupted.removeAll(result.getVehiclesOK());
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

    Set<VisitObj> getListOfVisitsFromUser(User u) {
        assert this.graphRTV.getListOfVisitsFromUser(u).containsAll(userVisitsMap.get(u));
        return this.userVisitsMap.get(u);
    }

    Set<VisitObj> getListOfVisitsFromVehicle(Vehicle v) {
        assert this.graphRTV.getListOfVisitsFromVehicle(v).containsAll(this.vehicleVisitsMap.get(v));
        return this.vehicleVisitsMap.get(v);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // ASSERTIONS //////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /*public void assertInputState(){
        assert thereAreNoRepeatedRequests(requests) : "There are repeated elements in request list!";
        assert allVehicleVisitsAreValid() : "Invalid visits found.";
        assert eachUserIsAssignedToSingleVehicle() : "User is assigned to two different vehicles.";
    }*/


    protected boolean userCannotBePickedUpByIdleVehicles(GraphRTV graphRTV, Set<Vehicle> unassignedVehicles, User u) {

        for (VisitObj visit : this.getListOfVisitsFromUser(u)) {
            Vehicle v = visit.getVehicle();
            if (unassignedVehicles.contains(v)) {
                System.out.println(unassignedVehicles);
                System.out.println(String.format("Free vehicle %s can service request %s: VisitObj = %s", v, u, v.getVisit()));
                return false;
            }
        }

        for (Vehicle v : unassignedVehicles) {
            VisitObj candidateVisit = Method.getBestVisitFromPDPermutations(v, new HashSet<>(Arrays.asList(u)));
            if (candidateVisit != null) {
                System.out.println(String.format("(CANDIDATE VISIT) Free vehicle %s can service request %s: VisitObj = %s", v, u, candidateVisit));
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

    public boolean vehiclesVisitsSameOrder(Map<Vehicle, Set<VisitObj>> a, Map<Vehicle, Set<StateAction>> b) {
        if (a.size() != b.size()) return false;

        List<Vehicle> la = new ArrayList<Vehicle>(a.keySet());
        List<Vehicle> lb = new ArrayList<Vehicle>(b.keySet());
        if (!la.equals(lb)) return false;

        for (Vehicle v : la) {
            List<VisitObj> lva = new ArrayList<VisitObj>(a.get(v));
            List<VisitObj> lvb = new ArrayList<VisitObj>(b.get(v).stream().map(vehicleState -> vehicleState.getVisit()).collect(Collectors.toList()));
            if (!lva.equals(lvb)) return false;
        }
        return true;
    }


}

