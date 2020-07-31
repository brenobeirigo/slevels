package simulation.matching;

import gurobi.GRB;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBVar;
import model.User;
import model.Vehicle;
import model.Visit;

import java.util.*;


public class MatchingOptimalServiceLevelAndHire extends MatchingOptimalServiceLevel {

    private GRBVar[] varVehicleIsHired;
    private Set<Vehicle> hiredCurrentPeriod;
    private Map<Vehicle, Integer> hiredIndex;


    public MatchingOptimalServiceLevelAndHire(int maxVehicleCapacityRTV, int badServicePenalty, double mipTimeLimit, double timeoutVehicleRTV, double mipGap, int maxEdgesRV, int rejectionPenalty) {
        super(maxVehicleCapacityRTV, badServicePenalty, mipTimeLimit, timeoutVehicleRTV, mipGap, maxEdgesRV, rejectionPenalty);
    }

    @Override
    public ResultAssignment match(int currentTime, List<User> unassignedRequests, List<Vehicle> currentVehicleList, Set<Vehicle> hired, Matching configMatching) {
        this.currentTime = currentTime;
        this.hiredCurrentPeriod = hired;
        this.result = new ResultAssignment(currentTime);

        List<Vehicle> allAvailableVehicles = new ArrayList<>(currentVehicleList);
        allAvailableVehicles.addAll(hired);

        buildGraphRTV(unassignedRequests, allAvailableVehicles, this.maxVehicleCapacityRTV, timeoutVehicleRTV);

        if (this.requests.isEmpty())
            return result;

        try {
            createGurobiModelAndEnvironment();
            initVarsHiring();
            addConstraintsHiring();
            setupObjectiveHiredHierarchicalPenaltyThenTotalWaiting();

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


    protected void addConstraintsHiring() throws GRBException {
        super.addConstraintsServiceLevels();
        addConstrsVehicleIsHired();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // OBJECTIVE ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    private void setupObjectiveHiredHierarchicalPenaltyThenTotalWaiting() throws GRBException {


        Map<String, GRBLinExpr> penObjectives = new LinkedHashMap<>();

        if (!hiredCurrentPeriod.isEmpty())
            objNumberHired(penObjectives);

        objHierarchicalServiceLevel(penObjectives);

        objTotalWaitingTime(penObjectives);

        addHierarchicalObjectives(penObjectives);
    }

    private void objNumberHired(Map<String, GRBLinExpr> penObjectives) {

        penObjectives.put("N_HIRED", new GRBLinExpr());
        for (Vehicle vehicle : hiredCurrentPeriod) {
            penObjectives.get("N_HIRED").addTerm(1, varVehicleIsHired(vehicle));
        }
    }


    private void initVarsHiring() throws GRBException {

        initVarsServiceLevel();

        varVehicleIsHired = new GRBVar[hiredCurrentPeriod.size()];

        hiredIndex = new HashMap<>();
        int i = 0;
        for (Vehicle vehicle : hiredCurrentPeriod) {
            hiredIndex.put(vehicle, i);
            i++;
        }

        for (Vehicle vehicle : hiredCurrentPeriod) {
            addIsHiredVehicleVar(vehicle);
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // CONSTRAINTS /////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void addConstrsVehicleIsHired() throws GRBException {

        Map<Vehicle, GRBLinExpr> hiredVehicleVisits = new HashMap<>();

        for (Vehicle vehicle : hiredCurrentPeriod) {
            hiredVehicleVisits.put(vehicle, new GRBLinExpr());
        }

        for (Visit visit : visits) {

            // Exclude visits from previously hired vehicles
            if (hiredCurrentPeriod.contains(visit.getVehicle()) && visit.getVehicle().isHired()) {
                hiredVehicleVisits.get(visit.getVehicle()).addTerm(1, varVisitSelected(visit));
            }
        }

        for (Vehicle vehicle : hiredCurrentPeriod) {
            hiredVehicleVisits.get(vehicle).addTerm(-1, varVehicleIsHired(vehicle));
            model.addConstr(hiredVehicleVisits.get(vehicle), GRB.EQUAL, 0, "hiring_" + vehicle.toString().trim());
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // VARIABLES ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    private GRBVar varVehicleIsHired(Vehicle vehicle) {
        return varVehicleIsHired[hiredIndex.get(vehicle)];
    }

    protected void addIsHiredVehicleVar(Vehicle vehicle) throws GRBException {
        String label = String.format("x_hired_%s", vehicle.toString().trim());
        varVehicleIsHired[hiredIndex.get(vehicle)] = model.addVar(0, 1, 1, GRB.BINARY, label);
    }
    private boolean isVehicleHired(Vehicle vehicle) throws GRBException {
        return getValue(varVehicleIsHired(vehicle)) > 0.99;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // RESULT PROCESSING ///////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void extractResultServiceLevel() throws GRBException {

        super.extractResultServiceLevel();

        for (Vehicle vehicle : hiredCurrentPeriod) {
            if (isVehicleHired(vehicle)) {
                result.addHiredVehicle(vehicle);
            }
        }
    }


    @Override
    public String toString() {
        return "_OPT-ERTV_HIRE";
    }
}
