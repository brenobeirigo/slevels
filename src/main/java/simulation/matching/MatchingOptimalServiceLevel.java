package simulation.matching;

import config.Config;
import config.Qos;
import gurobi.GRB;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBVar;
import model.User;
import model.Vehicle;
import model.VisitObj;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class MatchingOptimalServiceLevel extends MatchingOptimal {

    protected final int badServicePenalty;
    protected GRBVar[] varClassServiceLevelViolation;
    protected GRBVar[] varFirstTierMet;
    protected int[] nOfRequestsPerClass;


    public MatchingOptimalServiceLevel(int maxVehicleCapacityRTV, int badServicePenalty, double mipTimeLimit, double timeoutVehicleRTV, double mipGap, int maxEdgesRV, int maxEdgesRR, int rejectionPenalty, String[] objectives, String PDVisitGenerator) {
        super(maxVehicleCapacityRTV, mipTimeLimit, timeoutVehicleRTV, mipGap, maxEdgesRV, maxEdgesRR, rejectionPenalty, objectives, PDVisitGenerator);
        this.badServicePenalty = badServicePenalty;
    }

    @Override
    public ResultAssignment match(int currentTime, Set<User> unassignedRequests, Set<Vehicle> vehicles, Set<Vehicle> hired) {
        this.currentTime = currentTime;
        this.result = new ResultAssignment(currentTime);

        Set<Vehicle> allAvailableVehicles = new HashSet<>(vehicles);
        allAvailableVehicles.addAll(hired);

        buildGraphRTV(unassignedRequests, allAvailableVehicles, this.maxVehicleCapacityRTV, timeoutVehicleRTV, maxEdgesRV, maxEdgesRR);

        if (this.requests.isEmpty())
            return result;

        try {
            createGurobiModelAndEnvironment();
            initVarsServiceLevel();
            initSlacks();
            addConstraintsServiceLevels();
            setupObjectives();

            this.model.optimize();

            if (isModelOptimal() || isTimeLimitReached()) {
                if (isTimeLimitReached()) {
                    System.out.printf("## TIME LIMIT REACHED = %.2f seconds // Solution count: %s%n", mipTimeLimit, model.get(GRB.IntAttr.SolCount));
                }

                extractResultServiceLevel();

            } else {
                computeIISReduceUntilCanBeSolved();
            }
        } catch (GRBException e) {
            System.out.println("TIME IS OVER - No solution found, keep previous assignment. Gurobi error code: " + e.getErrorCode() + ". " + e.getMessage());
            keepPreviousAssignment();
        } finally {
            disposeModelEnvironmentAndSave();
        }

        result.printRoundResultSummary("Online assignment");

        return result;
    }

    protected void initSlacks() throws GRBException {
        for (Qos qos : Config.getInstance().qosDic.values()) {
            addClassServiceLevelSlack(qos);
        }
    }


    protected void addConstraintsServiceLevels() throws GRBException {
        addConstraintsStandardAssignment();
        activateVarRequestMetSL();
        guaranteeClassMinimumSLRelaxedGreaterEqual();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // OBJECTIVE ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void addObjective(String objective) {
        super.addObjective(objective);
        switch (objective) {
            case Objective.HIERARCHICAL_REJECTION_SERVICE_LEVEL -> objHierarchicalRejectionServiceLevel();
            case Objective.HIERARCHICAL_SERVICE_LEVEL -> objHierarchicalServiceLevel();
            case Objective.HIERARCHICAL_SLACK -> objHierarchicalSlack();
        }
    }

    protected void objHierarchicalRejectionServiceLevel() {
        // Sort QoS order = A, B, C
        List<Qos> sortedQos = Config.getInstance().getSortedQosList();

        // Violation penalty
        String objGoal = "REJ_VIOL";
        for (Qos qos : sortedQos) {
            String objLabel = String.format("%s_%s", objGoal, qos.id);
            penObjectives.put(objLabel, new GRBLinExpr());
        }

        for (User request : requests) {
            String objLabel = String.format("%s_%s", objGoal, request.qos.id);
            penObjectives.get(objLabel).addTerm(rejectionPenalty, varRequestRejected(request));
            penObjectives.get(objLabel).addTerm(-badServicePenalty, varRequestServiceLevelAchieved(request));
            penObjectives.get(objLabel).addConstant(badServicePenalty);
        }
    }

    protected void objHierarchicalServiceLevel() {
        // Sort QoS order = A, B, C
        List<Qos> sortedQos = Config.getInstance().getSortedQosList();

        // Violation penalty
        String objGoal = "VIOL";
        for (Qos qos : sortedQos) {
            String objLabel = String.format("%s_%s", objGoal, qos.id);
            penObjectives.put(objLabel, new GRBLinExpr());
        }

        for (User request : requests) {
            String objLabel = String.format("%s_%s", objGoal, request.qos.id);
            penObjectives.get(objLabel).addTerm(-badServicePenalty, varRequestServiceLevelAchieved(request));
            penObjectives.get(objLabel).addConstant(badServicePenalty);
        }
    }

    protected void objHierarchicalSlack() {
        // Sort QoS order = A, B, C
        List<Qos> sortedQos = Config.getInstance().getSortedQosList();

        String label = "SLACK_";
        // Violation penalty
        for (Qos qos : sortedQos) {
            penObjectives.put(label + qos.id, new GRBLinExpr());
            penObjectives.get(label + qos.id).addTerm(1, varClassServiceLevelViolation[qos.code]);
        }
    }

    protected void keepPreviousAssignment() {

        result.getRequestsUnassigned().addAll(User.getUnassigned(requests));

        // Keep the previously picked up requests
        List<User> serviced = User.getAssigned(requests);
        serviced.forEach(user -> result.addVisit(user.getCurrentVisit()));

        // Classify serviced in first- and second-tier
        result.requestsServicedLevelAchieved.addAll(User.filterFirstTier(serviced));
        result.requestsServicedLevelNotAchieved.addAll(User.filterSecondTier(serviced));

        for (Qos qos : Config.getInstance().qosDic.values()) {
            result.unmetServiceLevelClass.put(qos, User.filterUsersOfQos(User.filterSecondTier(requests), qos).size());
            result.nOfRequestsClass.put(qos, User.filterUsersOfQos(requests, qos).size());
        }
    }

    protected void initVarsServiceLevel() throws GRBException {

        super.initVarsStandardAssignment();

        varFirstTierMet = new GRBVar[requests.size()];
        varClassServiceLevelViolation = new GRBVar[Config.getInstance().getQosCount()];
        nOfRequestsPerClass = new int[Config.getInstance().getQosCount()];

        for (User request : requests) {
            addIsTargetServiceLevelMetVar(request);
            nOfRequestsPerClass[request.getQoSCode()]++;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // CONSTRAINTS /////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void guaranteeClassMinimumSL() {

        GRBLinExpr[] constrGaranteeClassServiceLevel = initConstGuaranteeClassServiceLevel();

        // Sum of all user that got first tier service levels of each class
        addRequestServiceLevelAchievedVars(constrGaranteeClassServiceLevel);

        Config.getInstance().qosDic.forEach((s, qos) -> {
            // Number of picked up users has to be higher than service rate promised
            addConstrGreaterEqualMinServiceLevelFromClass(constrGaranteeClassServiceLevel, qos);
        });
    }

    protected void guaranteeClassMinimumSLRelaxedGreaterEqual() {

        GRBLinExpr[] constrGuaranteeClassServiceLevel = initConstGuaranteeClassServiceLevel();
        addRequestServiceLevelAchievedVars(constrGuaranteeClassServiceLevel);

        Config.getInstance().qosDic.forEach((s, qos) -> {

            // Add slack to each service level class (i.e., number of user who received second tier or were rejected)
            addSlack(constrGuaranteeClassServiceLevel, qos);

            // Number of picked up users has to be higher than service rate promised
            addConstrGreaterEqualMinServiceLevelFromClass(constrGuaranteeClassServiceLevel, qos);
        });
    }

    private void addSlack(GRBLinExpr[] constrGuaranteeClassServiceLevel, Qos qos) {
        constrGuaranteeClassServiceLevel[qos.code].addTerm(1, varClassServiceLevelViolation[qos.code]);
    }

    private GRBLinExpr[] initConstGuaranteeClassServiceLevel() {
        GRBLinExpr[] constrGuaranteeClassServiceLevel = new GRBLinExpr[Config.getInstance().getQosCount()];

        for (int i = 0; i < Config.getInstance().getQosCount(); i++) {
            constrGuaranteeClassServiceLevel[i] = new GRBLinExpr();
        }
        return constrGuaranteeClassServiceLevel;
    }

    private void addRequestServiceLevelAchievedVars(GRBLinExpr[] constrGuaranteeClassServiceLevel) {
        // Sum of all user that got first tier service levels of each class
        for (User request : requests) {

            // GOOD SERVICE = desired service level
            GRBVar slAchieved = varRequestServiceLevelAchieved(request);
            if (slAchieved != null) {
                constrGuaranteeClassServiceLevel[request.getQoSCode()].addTerm(1, slAchieved);
            }
        }
    }

    private void addConstrGreaterEqualMinServiceLevelFromClass(GRBLinExpr[] constrGuaranteeClassServiceLevel, Qos qos) {
        try {
            int minRequestsSL = getMinRequestsToMeetServiceLevelFromClass(qos);
            String label = getLabelGuaranteeClassMinimumSL(qos);
            model.addConstr(constrGuaranteeClassServiceLevel[qos.code], GRB.GREATER_EQUAL, minRequestsSL, label);
        } catch (GRBException e) {
            e.printStackTrace();
        }
    }

    private String getLabelGuaranteeClassMinimumSL(Qos qos) {
        int minRequestsSL = getMinRequestsToMeetServiceLevelFromClass(qos);
        return String.format("class_%s_sr_%.2f_total_%d_min_%d", qos.id, qos.serviceRate, nOfRequestsPerClass[qos.code], minRequestsSL);
    }

    protected int getMinRequestsToMeetServiceLevelFromClass(Qos qos) {
        return (int) Math.ceil(qos.serviceRate * nOfRequestsPerClass[qos.code]);
    }


    private boolean isFirstTier(User request, VisitObj visit) {

        double pickupDelay = getDelayOfRequestInVisit(request, visit);
        return request.isDelayFirstTier(pickupDelay);
    }

    protected void activateVarRequestMetSL() throws GRBException {

        for (User request : requests) {
            GRBLinExpr constrRequestServiceLevel = new GRBLinExpr();

            Set<VisitObj> requestVisits = graphRTV.getListOfVisitsFromUser(request);

            for (VisitObj visit : requestVisits) {
                if (isFirstTier(request, visit)) {
                    constrRequestServiceLevel.addTerm(1, varVisitSelected(visit));
                }
            }

            constrRequestServiceLevel.addTerm(-1, varRequestServiceLevelAchieved(request));
            String label = String.format("first_tier_visits_request_%s", getVarUser(request));
            model.addConstr(constrRequestServiceLevel, GRB.EQUAL, 0, label);

        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // VARIABLES ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    protected GRBVar varRequestServiceLevelAchieved(User request) {
        return varFirstTierMet[requestIndex.get(request)];
    }

    private void addIsTargetServiceLevelMetVar(User request) throws GRBException {
        String label = "x_sl_" + getVarUser(request);
        varFirstTierMet[requestIndex.get(request)] = model.addVar(0, 1, 1, GRB.BINARY, label);
    }

    private void addClassServiceLevelSlack(Qos qos) throws GRBException {
        String label = "x_slack_bad_service" + qos.id;
        int minRequestsSL = getMinRequestsToMeetServiceLevelFromClass(qos);
        varClassServiceLevelViolation[qos.code] = model.addVar(0, minRequestsSL, 1, GRB.INTEGER, label);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // RESULT PROCESSING ///////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void extractResultServiceLevel() throws GRBException {

        super.extractResult();

        for (Qos qos : Config.getInstance().qosDic.values()) {
            result.accountNumberOfServiceLevelViolations(qos, getValue(varClassServiceLevelViolation[qos.code]));
            result.accountNumberOfRequestsClass(qos, nOfRequestsPerClass[qos.code]);
        }

        for (User request : requests) {
            if (!isRequestRejected(request)) {
                if (isServiceLevelMet(request)) {
                    result.accountMetServiceLevel(request);
                } else {
                    result.accountUnmetServiceLevel(request);
                }
            }
        }
    }

    protected double getValue(GRBVar grbVar) throws GRBException {
        return grbVar.get(GRB.DoubleAttr.X);
    }

    private boolean isServiceLevelMet(User request) throws GRBException {
        return getValue(varRequestServiceLevelAchieved(request)) > 0.99;
    }

    @Override
    public String toString() {
        return "_OPT-JAVIERSL";
    }

}
