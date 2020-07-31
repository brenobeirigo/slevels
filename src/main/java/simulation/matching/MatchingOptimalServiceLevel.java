package simulation.matching;

import config.Config;
import config.Qos;
import gurobi.GRB;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBVar;
import model.*;

import java.util.*;


public class MatchingOptimalServiceLevel extends MatchingOptimal {

    private final int badServicePenalty;
    private GRBVar[] varFirstTierMet;
    private GRBVar[] varClassServiceLevelViolation;
    private int[] nOfRequestsPerClass;


    public MatchingOptimalServiceLevel(int maxVehicleCapacityRTV, int badServicePenalty, double mipTimeLimit, double timeoutVehicleRTV, double mipGap, int maxEdgesRV, int rejectionPenalty) {
        super(maxVehicleCapacityRTV, mipTimeLimit, timeoutVehicleRTV, mipGap, maxEdgesRV, rejectionPenalty);
        this.badServicePenalty = badServicePenalty;
    }

    @Override
    public ResultAssignment match(int currentTime, List<User> unassignedRequests, List<Vehicle> currentVehicleList, Set<Vehicle> hired, Matching configMatching) {
        this.currentTime = currentTime;
        this.result = new ResultAssignment(currentTime);

        List<Vehicle> allAvailableVehicles = new ArrayList<>(currentVehicleList);
        allAvailableVehicles.addAll(hired);

        buildGraphRTV(unassignedRequests, allAvailableVehicles, this.maxVehicleCapacityRTV, timeoutVehicleRTV);

        if (this.requests.isEmpty())
            return result;

        try {
            createGurobiModelAndEnvironment();
            initVarsServiceLevel();
            addConstraintsServiceLevels();
            setupObjectiveHierarchicalPenaltyThenTotalWaiting();

            saveModel(currentTime);
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
            closeGurobiModelAndEnvironment();
        }

        result.printRoundResult();

        return result;
    }

    protected void addConstraintsServiceLevels() throws GRBException {
        addConstraintsStandardAssignment();
        activateVarRequestMetSL();
        guaranteeClassMinimumSL();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // OBJECTIVE ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void setupObjectiveHierarchicalPenaltyThenTotalWaiting() throws GRBException {

        Map<String, GRBLinExpr> penObjectives = new LinkedHashMap<>();

        // Sort QoS order = A, B, C
        objHierarchicalServiceLevel(penObjectives);

        objTotalWaitingTime(penObjectives);

        addHierarchicalObjectives(penObjectives);
    }


    protected void objHierarchicalServiceLevel(Map<String, GRBLinExpr> penObjectives) {
        // Sort QoS order = A, B, C
        List<Qos> sortedQos = Config.getInstance().getSortedQosList();

        // Violation penalty
        for (Qos qos : sortedQos) {
            penObjectives.put(qos.id, new GRBLinExpr());
        }

        for (User request : requests) {
            penObjectives.get(request.qos.id).addTerm(rejectionPenalty, varRequestRejected(request));
            penObjectives.get(request.qos.id).addTerm(-badServicePenalty, varRequestServiceLevelAchieved(request));
            penObjectives.get(request.qos.id).addConstant(badServicePenalty);
        }
    }

    protected void objHierarchicalWaiting(Map<String, GRBLinExpr> penObjectives) {
        // Sort QoS order = A, B, C
        List<Qos> sortedQos = Config.getInstance().getSortedQosList();

        // Violation penalty
        for (Qos qos : sortedQos) {
            penObjectives.put(qos.id, new GRBLinExpr());
        }

        for (User request : requests) {
            penObjectives.get(request.qos.id).addTerm(rejectionPenalty, varRequestRejected(request));
            List<Visit> requestVisits = graphRTV.getListOfVisitsFromUser(request);
            for (Visit visit : requestVisits) {
                //if (isFirstTier(request, visit)) {
                double delay = getDelayOfRequestInVisit(request, visit);
                penObjectives.get(request.qos.id).addTerm(delay, varVisitSelected(visit));
            }
            penObjectives.get(request.qos.id).addTerm(-badServicePenalty, varRequestServiceLevelAchieved(request));
            penObjectives.get(request.qos.id).addConstant(badServicePenalty);
        }
    }

    private double getDelayOfRequestInVisit(User request, Visit visit) {
        return graphRTV.getWeightFromRequestVisitEdge(request, visit);
    }

    protected void objTotalWaitingTime(Map<String, GRBLinExpr> penObjectives) {
        penObjectives.put("WAITING", new GRBLinExpr());
        for (Visit visit : visits) {
            penObjectives.get("WAITING").addTerm(visit.getDelay(), varVisitSelected(visit));
        }
    }

    protected void addHierarchicalObjectives(Map<String, GRBLinExpr> penObjectives) throws GRBException {
        int i = penObjectives.size();

        for (Map.Entry<String, GRBLinExpr> e : penObjectives.entrySet()) {
            i--;
            model.setObjectiveN(e.getValue(), i, i, 1.0, 0.0, 0.0, "OBJ_" + e.getKey());
        }

        // The objective is to minimize the total pay costs
        model.set(GRB.IntAttr.ModelSense, GRB.MINIMIZE);
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

        for (Qos qos : Config.getInstance().qosDic.values()) {
            addClassServiceLevelSlack(qos, nOfRequestsPerClass[qos.code]);
        }

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // CONSTRAINTS /////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void guaranteeClassMinimumSL() {

        GRBLinExpr[] constrGaranteeClassServiceLevel = new GRBLinExpr[Config.getInstance().getQosCount()];

        for (int i = 0; i < Config.getInstance().getQosCount(); i++) {
            constrGaranteeClassServiceLevel[i] = new GRBLinExpr();
        }

        // Sum of all user that got first tier service levels of each class
        for (User request : requests) {

            // GOOD SERVICE = desired service level
            GRBVar slAchieved = varRequestServiceLevelAchieved(request);
            if (slAchieved != null) {
                constrGaranteeClassServiceLevel[request.getQoSCode()].addTerm(1, slAchieved);
            }
        }

        Config.getInstance().qosDic.forEach((s, qos) -> {

            // Add slack to each service level class (i.e., number of user who received second tier or were rejected)
            constrGaranteeClassServiceLevel[qos.code].addTerm(-1, varClassServiceLevelViolation[qos.code]);

            // Number of picked up users has to be higher than service rate promised
            try {
                int minRequestsSL = (int) Math.ceil(qos.serviceRate * nOfRequestsPerClass[qos.code]);
                String label = String.format("class_%s_sr_%.2f_total_%d_min_%d", qos.id, qos.serviceRate, nOfRequestsPerClass[qos.code], minRequestsSL);

                model.addConstr(constrGaranteeClassServiceLevel[qos.code], GRB.EQUAL, minRequestsSL, label);
            } catch (GRBException e) {
                e.printStackTrace();
            }
        });
    }


    private boolean isFirstTier(User request, Visit visit) {

        double pickupDelay = getDelayOfRequestInVisit(request, visit);
        return request.isDelayFirstTier(pickupDelay);
    }

    private void activateVarRequestMetSL() throws GRBException {

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


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // VARIABLES ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private GRBVar varRequestServiceLevelAchieved(User request) {
        return varFirstTierMet[requestIndex.get(request)];
    }

    private void addIsTargetServiceLevelMetVar(User request) throws GRBException {
        String label = "x_sl_" + request.toString().trim();
        varFirstTierMet[requestIndex.get(request)] = model.addVar(0, 1, 1, GRB.BINARY, label);
    }

    private void addClassServiceLevelSlack(Qos qos, int userCount) throws GRBException {
        String label = "x_slack_bad_service" + qos.id;
        int minRequestsSL = (int) Math.ceil(qos.serviceRate * nOfRequestsPerClass[qos.code]);
        varClassServiceLevelViolation[qos.code] = model.addVar(-minRequestsSL, userCount, 1, GRB.INTEGER, label);
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
