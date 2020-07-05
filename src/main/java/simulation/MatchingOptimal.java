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
    final int REJECTION_PENALTY = 100;
    final int BAD_SERVICE_PENALTY = 10;
    final int VEHICLE_CAPACITY = 4;
    final double MIP_TIME_LIMIT = 15;
    final double MIP_GAP = 0.01;
    final int SQ_USER_REJECTED = 0;
    GraphRTV graphRTV;

    // Turn off logging
    // env.set(GRB.IntParam.OutputFlag, 0);
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
    private GRBVar[] x;
    private GRBVar[] y;
    private GRBVar[] w;
    private GRBVar[] slack;
    private int[] nOfRequestsPerClass;
    private int currentTime;

    /*public void assertInputState(){
        //assert thereAreNoRepeatedRequests(requests) : "There are repeated elements in request list!";
        //assert allVehicleVisitsAreValid() : "Invalid visits found.";
        //assert eachUserIsAssignedToSingleVehicle() : "User is assigned to two different vehicles.";
    }*/

    @Override
    public ResultAssignment match(int currentTime, List<User> unassignedRequests, List<Vehicle> listVehicles, Matching configMatching) {
        this.currentTime = currentTime;
        // Consider all requests (assigned and unassigned)
        List<User> requests = new ArrayList<>(unassignedRequests);
        requests.addAll(Vehicle.getAssignedRequestsFrom(listVehicles));

        // BUILDING GRAPH STRUCTURE ////////////////////////////////////////////////////////////////////////////////////
        this.graphRTV = new GraphRTV(requests, listVehicles, VEHICLE_CAPACITY);

        // To assure every vehicle is assigned to a visit, create dummy stop visit.
        graphRTV.addStopVisits();

        // ASSIGNMENT //////////////////////////////////////////////////////////////////////////////////////////////////

        //this.runTimes.put(Solution.TIME_MATCHING, System.nanoTime());

        result = new ResultAssignment(currentTime);

        try {

            initVars();
            setupVehicleConservationConstraints();
            setupRequestConservationConstraints();
            setupClassTargetServiceLevelConstraints();
            setupObjective();
            model.write(String.format("round_mip_model/assignment_5%d.lp", this.currentTime));

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
//            } else if (status == GRB.Status.TIME_LIMIT) {
//                System.out.println("TIME LIMIT!!!!!!!");
//            }
            }else {
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

    private void setupObjective() throws GRBException {

        // Set primary objective
        //GRBLinExpr obj0 = new GRBLinExpr();
        GRBLinExpr obj1 = new GRBLinExpr();
        //GRBLinExpr obj2 = new GRBLinExpr();
        Map<Qos, GRBLinExpr> penObjectives = new HashMap<>();
        //int objIndex = 0;
        List<String> sortedQos = Config.getInstance().qosDic.values().stream().map(qos -> qos.id).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        //objIndex = sortedQos.size() + 1;
        //GRBLinExpr[] objs = new GRBLinExpr[objIndex];


        for (Qos qos : Config.getInstance().qosDic.values()) {
            penObjectives.put(qos, new GRBLinExpr());
            penObjectives.get(qos).addTerm(BAD_SERVICE_PENALTY, slack[qos.code]);
            //obj0.addTerm(BAD_SERVICE_PENALTY, slack[qos.code]);
        }

        for (Visit visit : visits) {
            obj1.addTerm(visit.getDelay(), varVisitSelected(visit));
            //objs[0] = new GRBLinExpr();
            //objs[0].addTerm(visit.getDelay(), varVisitSelected(visit));
        }

        for (User request : requests) {
            penObjectives.get(request.qos).addTerm(REJECTION_PENALTY, varRequestRejected(request));

            //obj2.addTerm(REJECTION_PENALTY, varRequestRejected(request));
            //objs[1] = new GRBLinExpr();
            //objs[1].addTerm(REJECTION_PENALTY, varRequestRejected(request));
        }

//        for (User request : requests) {
//            obj2.addTerm(REJECTION_PENALTY, varRequestRejected(request));
//            objs[1] = new GRBLinExpr();
//            objs[1].addTerm(REJECTION_PENALTY, varRequestRejected(request));
//        }

        for (int i = 0; i < sortedQos.size(); i++) {
            String classLabel = sortedQos.get(i);
            model.setObjectiveN(penObjectives.get(Config.getInstance().qosDic.get(classLabel)), i, i, 1.0, 0.0, 0.0, "OBJ_" + classLabel);
        }
//        sortedQos.add(0, "OBJ_REJECTION");
//        String[] labels = new String[]{"OBJ_DELAY", "OBJ"};
//        for (int i = 4; i >=0; i--) {
//            model.setObjectiveN(objs[4-i], 4-i, i, 1.0, 0.0, 0.0, "OBJ_SERVICE_LEVEL");
//        }

        //GRBLinExpr obj1 = new GRBLinExpr();
        //model.setObjectiveN(obj0, 0, 2, 1.0, 0.0, 0.0, "OBJ_SERVICE_LEVEL");
        //model.setObjectiveN(obj1, 0, 0, 1.0, 0.0, 0.0, "OBJ_TOTAL_DELAY");
        //model.setObjectiveN(obj2, 2, 3, 1.0, 0.0, 0.0, "OBJ_REJECTION");

        // The objective is to minimize the total pay costs
        model.set(GRB.IntAttr.ModelSense, GRB.MINIMIZE);
    }

    private void extractResult() throws GRBException {

        for (Qos qos : Config.getInstance().qosDic.values()) {
            double unmetService = slack[qos.code].get(GRB.DoubleAttr.X);
            result.unmetServiceLevelClass.put(qos, (int) unmetService);
            result.totalServiceLevelClass.put(qos, nOfRequestsPerClass[qos.code]);
        }
        for (User request : requests) {

            if (varRequestServiceLevelAchieved(request).get(GRB.DoubleAttr.X) > 0.99) {
                result.requestsServicedLevelAchieved.add(request);
            }

            if (varRequestRejected(request).get(GRB.DoubleAttr.X) > 0.99) {
                result.requestsUnassigned.add(request);

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
            GRBLinExpr constrRequestServiceLevel = new GRBLinExpr();
            GRBLinExpr constrRequestPreviouslyAssignedHaveToBeServiced = new GRBLinExpr();

            List<Visit> requestVisits = graphRTV.getListOfVisitsFromUser(request);
            for (Visit visit : requestVisits) {

                constrRequestConservation.addTerm(1, varVisitSelected(visit));

                if (isFirstTier(request, visit, graphRTV)) {
                    constrRequestServiceLevel.addTerm(1, varVisitSelected(visit));
                }

                if (request.isPreviouslyAssigned()) {
                    constrRequestPreviouslyAssignedHaveToBeServiced.addTerm(1, varVisitSelected(visit));
                }

            }
            constrRequestConservation.addTerm(1, varRequestRejected(request));
            constrRequestServiceLevel.addTerm(-1, varRequestServiceLevelAchieved(request));

            // Requests are associated with only one visit
            //model.addConstr(constrRequestServiceLevelConservation, GRB.EQUAL, 1, request.toString().trim());
            model.addConstr(constrRequestConservation, GRB.EQUAL, 1, "conservation_" + request.toString().trim());
            model.addConstr(constrRequestServiceLevel, GRB.EQUAL, 0, "first_tier_" + request.toString().trim());
            if (request.isPreviouslyAssigned())
                model.addConstr(constrRequestPreviouslyAssignedHaveToBeServiced, GRB.EQUAL, 1, "request_previously_assigned_have_to_serviced" + request.toString().trim());
        }
    }

    private GRBVar varRequestRejected(User request) {
        return y[requestIndex.get(request)];
    }

    private GRBVar varRequestServiceLevelAchieved(User request) {
        return w[requestIndex.get(request)];
    }

    private GRBVar varVisitSelected(Visit visit) {
        return x[visitIndex.get(visit)];
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
        x = new GRBVar[visits.size()];
        y = new GRBVar[requests.size()];
        w = new GRBVar[requests.size()];
        slack = new GRBVar[requests.size()];

        nOfRequestsPerClass = new int[Config.getInstance().getQosCount()];

        for (User request : requests) {
            addIsRejectedVar(request);
            addIsTargetServiceLevelMetVar(request);
            nOfRequestsPerClass[request.getQoSCode()]++;
        }

        for (Qos qos : Config.getInstance().qosDic.values()) {
            addClassServiceLevelSlack(qos, nOfRequestsPerClass[qos.code]);
        }

        for (Visit visit : visits) {
            addIsVisitChosenVar(visit);
        }

        // w[r][v][0] = 1, if request r is assigned to trip v with first-tier service levels
        // w[r][v][1] = 1, if request r is assigned to trip v with second-tier service levels
        // w[r][v][2] = 1, if request r is rejected
        //sl = new GRBVar[Config.getInstance().qosDic.size()][requests.size()][visits.size()][2];
    }

    private void addIsVisitChosenVar(Visit visit) throws GRBException {
        String label = String.format("x_%s", visit.getVarId());

        x[visitIndex.get(visit)] = model.addVar(0, 1, visit.getDelay(), GRB.BINARY, label);
        // obj0.addTerm(1, x[visitIndex.get(visit)]);
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
            constrFirstClass[qos.code].addTerm(1, slack[qos.code]);

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

    private boolean isFirstTier(User request, Visit visit, GraphRTV graphRTV) {
        double pickupDelay = graphRTV.getWeightFromRequestVisitEdge(request, visit);
        return request.isDelayFirstTier(pickupDelay);
    }

    private GRBVar addIsRejectedVar(User request) throws GRBException {
        y[requestIndex.get(request)] = model.addVar(0, 1, REJECTION_PENALTY, GRB.BINARY, "y_REJECTED_" + request.toString().trim());
        return y[requestIndex.get(request)];
    }

    private GRBVar addIsTargetServiceLevelMetVar(User request) throws GRBException {
        w[requestIndex.get(request)] = model.addVar(0, 1, 1, GRB.BINARY, "w_SL1_MET_" + request.toString().trim());
        return w[requestIndex.get(request)];
    }

    private GRBVar addClassServiceLevelSlack(Qos qos, int userCount) throws GRBException {
        slack[qos.code] = model.addVar(0, userCount, 1, GRB.INTEGER, "slack_SL2" + qos.id);
        return slack[qos.code];
    }

    /*private GRBVar addServiceLevelVar(User request, Visit visit, GraphRTV graphRTV) throws GRBException {
        double pickupDelay = graphRTV.getWeightFromRequestVisitEdge(request, visit);
        int tier = request.getServiceLevelTierBasedOn(pickupDelay);
        String label = String.format("sl_%s_%s_%s", request.getPerformanceClass(), request.toString().trim(), visitIndex.get(visit), tier);
        //sl[request.getQoSCode()][requestIndex.get(request)][visitIndex.get(visit)][tier] = model.addVar(0, 1, pickupDelay, GRB.BINARY, label);
        //return sl[request.getQoSCode()][requestIndex.get(request)][visitIndex.get(visit)][tier];
    }*/

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
