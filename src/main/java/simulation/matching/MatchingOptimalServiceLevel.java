package simulation.matching;

import config.Config;
import config.Qos;
import gurobi.GRB;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBVar;
import model.User;
import model.Vehicle;
import model.Visit;
import model.VisitStop;

import java.util.*;


public class MatchingOptimalServiceLevel extends MatchingOptimal {

    private final int serviceLevelViolationPenalty;
    private final int badServicePenalty;
    private GRBVar[] varVehicleIsHired;
    private Set<Vehicle> hired;
    private Map<Vehicle, Integer> hiredIndex;

    private GRBVar[] varFirstTierMet;
    private GRBVar[] varSecondTierMet;
    private GRBVar[] varClassServiceLevelViolation;
    private int[] nOfRequestsPerClass;


    public MatchingOptimalServiceLevel(int maxVehicleCapacityRTV, int violationPenalty, int badServicePenalty, double mipTimeLimit, double timeoutVehicleRTV, double mipGap, int maxEdgesRV, int rejectionPenalty) {
        super(maxVehicleCapacityRTV, mipTimeLimit, timeoutVehicleRTV, mipGap, maxEdgesRV, rejectionPenalty);
        this.serviceLevelViolationPenalty = violationPenalty;
        this.badServicePenalty = badServicePenalty;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // OBJECTIVE ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void setupObjectiveHierarchicalPenaltyThenTotalWaiting() throws GRBException {

        Map<String, GRBLinExpr> penObjectives = new LinkedHashMap<>();

        // Sort QoS order = A, B, C
        objHierarchicalServiceLevel(penObjectives);

        objTotalWaitingTime(penObjectives);

        setHierarchicalObjectives(penObjectives);
    }

    private void setupObjectiveHiredHierarchicalPenaltyThenTotalWaiting() throws GRBException {


        Map<String, GRBLinExpr> penObjectives = new LinkedHashMap<>();

        if (!hired.isEmpty())
            objNumberHired(penObjectives);

        objHierarchicalServiceLevel(penObjectives);

        objTotalWaitingTime(penObjectives);

        setHierarchicalObjectives(penObjectives);
    }

    private void objNumberHired(Map<String, GRBLinExpr> penObjectives) {

        penObjectives.put("N_HIRED", new GRBLinExpr());
        for (Vehicle vehicle : hired) {
            penObjectives.get("N_HIRED").addTerm(1, varVehicleIsHired(vehicle));
        }
    }

    private void objHierarchicalServiceLevel(Map<String, GRBLinExpr> penObjectives) {
        // Sort QoS order = A, B, C
        List<Qos> sortedQos = Config.getInstance().getSortedQosList();

        // Violation penalty
        for (Qos qos : sortedQos) {
            penObjectives.put(qos.id, new GRBLinExpr());
        }

        for (User request : requests) {
            penObjectives.get(request.qos.id).addTerm(rejectionPenalty, varRequestRejected(request));
            penObjectives.get(request.qos.id).addTerm(badServicePenalty, varRequestServiceLevelNotAchieved(request));
        }
    }

    private void objTotalWaitingTime(Map<String, GRBLinExpr> penObjectives) {
        penObjectives.put("WAITING", new GRBLinExpr());
        for (Visit visit : visits) {
            penObjectives.get("WAITING").addTerm(visit.getDelay(), varVisitSelected(visit));
        }
    }

    private void setupObjective() throws GRBException {

        Map<String, GRBLinExpr> penObjectives = new LinkedHashMap<>();

        // Sort QoS order = A, B, C
        objHierarchicalServiceLevel(penObjectives);
        objTotalWaitingTime(penObjectives);


        /*for (User request : requests) {
            GRBLinExpr constrRequestServiceLevel = new GRBLinExpr();
            List<Visit> requestVisits = graphRTV.getListOfVisitsFromUser(request);
            for (Visit visit : requestVisits) {
                double delay = graphRTV.getWeightFromRequestVisitEdge(request, visit);
                if (isFirstTier(request, visit)) {
                    constrRequestServiceLevel.addTerm(delay, varVisitSelected(visit));
                }
            }
        }*/

        setHierarchicalObjectives(penObjectives);
    }

    private void setupObjectiveHierarchicalPenaltyAndWaiting() throws GRBException {

        Map<String, GRBLinExpr> penObjectives = new LinkedHashMap<>();

        // Sort QoS order = A, B, C
        List<Qos> sortedQos = Config.getInstance().getSortedQosList();

        // Violation penalty
        for (Qos qos : sortedQos) {
            penObjectives.put(qos.id, new GRBLinExpr());
            penObjectives.put("DELAY_" + qos.id, new GRBLinExpr());
        }

        for (User request : requests) {
            graphRTV.getListOfVisitsFromUser(request).forEach(
                    visit -> {
                        double delay = graphRTV.getWeightFromRequestVisitEdge(request, visit);
                        penObjectives.get("DELAY_" + request.qos.id).addTerm(delay, varVisitSelected(visit));
                    }
            );

            penObjectives.get(request.qos.id).addTerm(rejectionPenalty, varRequestRejected(request));
            penObjectives.get(request.qos.id).addTerm(badServicePenalty, varRequestServiceLevelNotAchieved(request));
        }

        setHierarchicalObjectives(penObjectives);
    }

    private void setHierarchicalObjectives(Map<String, GRBLinExpr> penObjectives) throws GRBException {
        int i = penObjectives.size();

        for (Map.Entry<String, GRBLinExpr> e : penObjectives.entrySet()) {
            i--;
            model.setObjectiveN(e.getValue(), i, i, 1.0, 0.0, 0.0, "OBJ_" + e.getKey());
        }

        // The objective is to minimize the total pay costs
        model.set(GRB.IntAttr.ModelSense, GRB.MINIMIZE);
    }

    @Override
    public ResultAssignment match(int currentTime, List<User> unassignedRequests, List<Vehicle> listVehicles, Set<Vehicle> hired, Matching configMatching) {

        List<Vehicle> total = new ArrayList<>(listVehicles);
        total.addAll(hired);
        this.hired = hired;

        buildGraphRTV(unassignedRequests, total, this.maxVehicleCapacityRTV, timeoutVehicleRTV);

        result = new ResultAssignment(currentTime);

        if (this.requests.isEmpty())
            return result;

        try {

            createModel();
            initVarsStandard();
            initVarsSL();
            setupVehicleConservationConstraints();
            setupRequestConservationConstraints();
            setupRequestServiceLevelConstraints();
            setupClassTargetServiceLevelConstraints();
            setupRequestStatusConstraints();

            if (configMatching.isAllowedToHire) {
                initVarsHiring();
                setupIsHiredConstraints();
                setupObjectiveHiredHierarchicalPenaltyThenTotalWaiting();
            } else {
                setupObjectiveHierarchicalPenaltyAndWaiting();
            }

            model.write(String.format("round_mip_model/assignment%s_%d.lp", this, currentTime));
            this.model.optimize();

            int status = this.model.get(GRB.IntAttr.Status);
            if (status == GRB.Status.OPTIMAL || status == GRB.Status.TIME_LIMIT) {

                if (status == GRB.Status.TIME_LIMIT) {
                    System.out.println(String.format("## TIME LIMIT REACHED = %.2f seconds // Solution count: %s", mipTimeLimit, model.get(GRB.IntAttr.SolCount)));
                }

                extractResult();
                extractResultSL();

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

        result.printRoundResult();

        return result;
    }

    protected void keepPreviousAssignement() {

        result.getRequestsUnassigned().addAll(User.getUnassigned(requests));

        // Keep the previously picked up requests
        List<User> serviced = User.getAssigned(requests);
        serviced.forEach(user -> result.addVisit(user.getCurrentVisit()));

        // Classify serviced in first- and second-tier
        result.requestsServicedLevelAchieved.addAll(User.filterFirstTier(serviced));
        result.requestsServicedLevelNotAchieved.addAll(User.filterSecondTier(serviced));

        for (Qos qos : Config.getInstance().qosDic.values()) {
            result.unmetServiceLevelClass.put(qos, User.filterUsersOfQos(User.filterSecondTier(requests), qos).size());
            result.totalServiceLevelClass.put(qos, User.filterUsersOfQos(requests, qos).size());
        }
    }

    private void initVarsHiring() throws GRBException {
        varVehicleIsHired = new GRBVar[hired.size()];

        hiredIndex = new HashMap<>();
        int i = 0;
        for (Vehicle vehicle : hired) {
            hiredIndex.put(vehicle, i);
            i++;
        }

        for (Vehicle vehicle : hired) {
            addIsHiredVehicleVar(vehicle);
        }
    }

    private void initVarsSL() throws GRBException {

        varFirstTierMet = new GRBVar[requests.size()];
        varSecondTierMet = new GRBVar[requests.size()];
        varClassServiceLevelViolation = new GRBVar[Config.getInstance().getQosCount()];
        nOfRequestsPerClass = new int[Config.getInstance().getQosCount()];

        for (User request : requests) {
            addIsTargetServiceLevelMetVar(request);
            addIsTargetServiceLevelNotMetVar(request);
            nOfRequestsPerClass[request.getQoSCode()]++;
        }

        for (Qos qos : Config.getInstance().qosDic.values()) {
            addClassServiceLevelSlack(qos, nOfRequestsPerClass[qos.code]);
        }

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // CONSTRAINTS /////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void setupRequestStatusConstraints() throws GRBException {
        for (User request : requests) {

            GRBLinExpr constrRequestServiceLevelNotAchieved = new GRBLinExpr();
            constrRequestServiceLevelNotAchieved.addTerm(1, varRequestServiceLevelNotAchieved(request));
            constrRequestServiceLevelNotAchieved.addTerm(1, varRequestServiceLevelAchieved(request));
            constrRequestServiceLevelNotAchieved.addTerm(1, varRequestRejected(request));

            model.addConstr(constrRequestServiceLevelNotAchieved, GRB.EQUAL, 1, "second_tier_" + request.toString().trim());
        }
    }

    private void setupIsHiredConstraints() throws GRBException {

        Map<Vehicle, GRBLinExpr> hiredVehicleVisits = new HashMap<>();

        for (Vehicle vehicle : hired) {
            hiredVehicleVisits.put(vehicle, new GRBLinExpr());
        }

        for (Visit visit : visits) {

            // Exclude visits from previously hired vehicles
            if (visit.getVehicle().isHired() && hired.contains(visit.getVehicle()) && !(visit instanceof VisitStop)) {
                hiredVehicleVisits.get(visit.getVehicle()).addTerm(1, varVisitSelected(visit));
            }
        }

        for (Vehicle vehicle : hired) {
            hiredVehicleVisits.get(vehicle).addTerm(-1, varVehicleIsHired(vehicle));
            model.addConstr(hiredVehicleVisits.get(vehicle), GRB.EQUAL, 0, "hiring_" + vehicle.toString().trim());
        }
    }

    private void setupClassTargetServiceLevelConstraints() {

        GRBLinExpr[] constrFirstClass = new GRBLinExpr[Config.getInstance().getQosCount()];
        GRBLinExpr[] constrBadService = new GRBLinExpr[Config.getInstance().getQosCount()];
        for (int i = 0; i < Config.getInstance().getQosCount(); i++) {
            constrFirstClass[i] = new GRBLinExpr();
            constrBadService[i] = new GRBLinExpr();
        }

        // Sum of all user that got first tier service levels of each class
        for (User request : requests) {

            // GOOD SERVICE = desired service level
            GRBVar slAchieved = varRequestServiceLevelAchieved(request);
            if (slAchieved != null) {
                constrFirstClass[request.getQoSCode()].addTerm(1, slAchieved);
            }


            // BAD SERVICE = service level NOT achieved OR rejections
            GRBVar slNotAchieved = varRequestServiceLevelNotAchieved(request);
            if (slNotAchieved != null) {
                constrBadService[request.getQoSCode()].addTerm(1, slNotAchieved);
            }

            constrBadService[request.getQoSCode()].addTerm(1, varRequestRejected(request));
        }

        Config.getInstance().qosDic.forEach((s, qos) -> {

            // Add slack to each service level class (i.e., number of user who received second tier or were rejected)
            constrFirstClass[qos.code].addTerm(1, varClassServiceLevelViolation[qos.code]);
            constrBadService[qos.code].addTerm(-1, varClassServiceLevelViolation[qos.code]);

            // Number of picked up users has to be higher than service rate promised
            try {
                model.addConstr(
                        constrFirstClass[qos.code],
                        GRB.GREATER_EQUAL,
                        (int) Math.ceil(qos.serviceRate * nOfRequestsPerClass[qos.code]),
                        String.format("class_%s", qos.id));

                model.addConstr(
                        constrBadService[qos.code],
                        GRB.GREATER_EQUAL,
                        0,
                        String.format("class_reject_no_sl_%s", qos.id));
            } catch (GRBException e) {
                e.printStackTrace();
            }
        });
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


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // VARIABLES ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private GRBVar varRequestServiceLevelAchieved(User request) {
        return varFirstTierMet[requestIndex.get(request)];
    }

    private GRBVar varRequestServiceLevelNotAchieved(User request) {
        return varSecondTierMet[requestIndex.get(request)];
    }

    private GRBVar varVehicleIsHired(Vehicle vehicle) {
        return varVehicleIsHired[hiredIndex.get(vehicle)];
    }

    protected void addIsHiredVehicleVar(Vehicle vehicle) throws GRBException {
        String label = String.format("h_%s", vehicle.toString().trim());
        varVehicleIsHired[hiredIndex.get(vehicle)] = model.addVar(0, 1, 1, GRB.BINARY, label);
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

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // RESULT PROCESSING ///////////////////////////////////////////////////////////////////////////////////////////////
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

        for (Vehicle vehicle : hired) {
            if (varVehicleIsHired(vehicle).get(GRB.DoubleAttr.X) > 0.99) {
                result.addHiredVehicle(vehicle);
            }
        }
    }

    @Override
    public String toString() {
        return "_OPT-JAVIERSL";
    }

}
