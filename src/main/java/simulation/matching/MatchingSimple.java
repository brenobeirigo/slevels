package simulation.matching;

import com.google.common.collect.Iterables;
import config.InstanceConfig;
import dao.Dao;
import dao.Logging;
import gurobi.GRBEnv;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;
import helper.HelperIO;
import model.*;
import model.graph.GraphRTV;
import model.graph.ParallelGraphRTV;
import model.learn.*;
import model.node.NodeTargetRebalancing;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import simulation.Simulation;

import java.util.*;

public class MatchingSimple implements RideMatchingStrategy {


    private static int nOfExperiences = 0;

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
    public List<Experience> experiences;
    private CircularFifoQueue<FleetStateActionSpace> experienceReplayMemory;
    public InstanceConfig.LearningConfig.LearningSettings learningSettings;

    public MatchingSimple(int maxVehicleCapacityRTV, double mipTimeLimit, double timeoutVehicleRTV, double mipGap, int maxEdgesRV, int maxEdgesRR, int rejectionPenalty, String[] orderedListOfObjectiveLabels, String PDVisitGenerator) {
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
        this.experiences = new ArrayList<>();
    }

    public void configureLearning(InstanceConfig.LearningConfig.LearningSettings learningConfig) {
        this.learningSettings = learningConfig;
        if (learningConfig != null)
            this.experienceReplayMemory = new CircularFifoQueue(this.learningSettings.sizeExperienceReplayBuffer);

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

    protected void buildGraphRTV(Set<User> unassignedRequests, Set<Vehicle> listVehicles, int maxVehicleCapacity, double timeoutVehicle, int maxVehReqEdges, int maxReqReqEdges) {

        // BUILDING GRAPH STRUCTURE ////////////////////////////////////////////////////////////////////////////////////
        this.graphRTV = new ParallelGraphRTV(unassignedRequests, listVehicles, maxVehicleCapacity, timeoutVehicle, maxVehReqEdges, maxReqReqEdges);
    }

    public void experienceReplay() {

        List<FleetStateActionSpace> sampledExperiences = new ArrayList<>(this.experienceReplayMemory);
        Collections.shuffle(sampledExperiences);
        Logging.logger.info(
                "### EXPERIENCE REPLAY (batch={}, buffer={})",
                this.learningSettings.sizeExperienceReplayBatch,
                sampledExperiences.size());

        sampledExperiences.stream()
                .limit(this.learningSettings.sizeExperienceReplayBatch)
                .parallel().map(this::getReplayedExperienceObject).toList().forEach(xp -> {
                    // Sequential because model cannot be updated in sequence
                    // TODO Submit batch
                    String msg = xp.remember(this.learningSettings);
                    Logging.logger.info(msg);
                });
    }

    private ExperienceObject getReplayedExperienceObject(FleetStateActionSpace fleetStateActionSpace) {

        // Query the vfs for postDecision spaces
        Map<Integer, List<Double>> predictions = getVFsFromFleetStateActionSpaceObject(
                fleetStateActionSpace.experienceObj.post_decision_state_action);

        Map<Vehicle, Set<StateAction>> vehiclePreDecisionsMap = fleetStateActionSpace.getVehicleStateActionMap();
        Map<Vehicle, Set<VisitObj>> vehicleStateActionObjMap = getVisitObjMap(vehiclePreDecisionsMap, predictions);

        // Find best assignment
        AssignmentILP assignVehiclesVisitsWithVFs = new AssignmentILP(
                fleetStateActionSpace.timeStep,
                vehicleStateActionObjMap,
                fleetStateActionSpace.requests,
                false);

        assignVehiclesVisitsWithVFs.run(
                new String[]{
                        Objective.TOTAL_REQUESTS_PLUS_VFS,
                        Objective.TOTAL_WAITING});

        fleetStateActionSpace.experienceObj.updateStateActionReward(
                fleetStateActionSpace.vehicles,
                assignVehiclesVisitsWithVFs.getResult());


        Logging.logger.info("{}",
                String.format(
                        "************************* Experience id = %d - VFs = %.4f",
                        fleetStateActionSpace.experienceObj.id,
                        assignVehiclesVisitsWithVFs.getResult().getTotalVFs()));
//            assignVehiclesVisitsWithVFs.getResult().printRoundResultSummary("Experience Replay");
        return fleetStateActionSpace.experienceObj;
    }

    private Map<Integer, List<Double>> getVFsFromFleetStateActionSpaceObject(FleetStateActionSpaceObject postDecisionStateActionSpaceObj) {
        return Dao.getInstance().getServer().getPredictionsFromDecisionSpace(postDecisionStateActionSpaceObj);
    }

    @Override
    public ResultAssignment match(int timeStep, Set<User> requests, Set<Vehicle> vehicles, Set<Vehicle> hired) {
        buildGraphRTV(requests, vehicles, this.maxVehicleCapacityRTV, timeoutVehicleRTV, maxEdgesRV, maxEdgesRR);

        this.visits = new HashSet<>(this.graphRTV.getAllVisits());
        this.requests = new HashSet<>(requests);
        this.vehicles = new HashSet<>(vehicles);
        this.vehicleVisitsMap = this.graphRTV.getVehicleVisitsMap();
        this.userVisitsMap = this.graphRTV.getUserVisitsMap();
        this.result = new ResultAssignment(timeStep);

        addRebalancingVisits();

//        DecisionSpaceObject preDecisionSpaceObj = preDecisionStateSpace.getDecisionSpaceObject();

        // CURRENT STATE
//        DecisionSpaceObject current = preDecisionStateSpace.getCurrentStateObject();

        // POST DECISION


        Map<Vehicle, Set<VisitObj>> vehiclePreDecisionsObjMap;

        if (isNotTerminal(timeStep)) {
            // PRE-DECISION
            FleetStateActionSpace preDecisionFleetStateActionSpace = new FleetStateActionSpace(
                    vehicles,
                    requests,
                    vehicleVisitsMap,
                    timeStep,
                    Simulation.timeHorizon);

            Map<Vehicle, Set<StateAction>> vehiclePreDecisionsMap =
                    preDecisionFleetStateActionSpace.getVehicleStateActionMap();

            FleetStateActionSpace postDecisionFleetStateActionSpace =
                    new PostDecisionFleetStateActionSpace(
                            preDecisionFleetStateActionSpace,
                            Simulation.timeWindow);

            FleetStateActionSpaceObject postDecisionStateSpaceObj =
                    postDecisionFleetStateActionSpace.getDecisionSpaceObject();

            preDecisionFleetStateActionSpace.addPostDecisionStateActionObj(postDecisionStateSpaceObj);


            Map<Integer, List<Double>> vehicleIdVFVisitsMap = getVFsFromFleetStateActionSpaceObject(postDecisionStateSpaceObj);
            vehiclePreDecisionsObjMap = getVisitObjMap(vehiclePreDecisionsMap, vehicleIdVFVisitsMap);


            if (isLearning() && experienceReplayEnabled()) {

                // Only take up experiences that occur before total time window:
                //   Earliest       Requests stop        End of          Total time      Simulation ends
                //     time           arriving          exp. sampling      window    (last request delivered)
                //      * --------------- * -------------- * ---------------- * --------------- *

                nOfExperiences++;
                preDecisionFleetStateActionSpace.genExperienceObj();

                experienceReplayMemory.add(preDecisionFleetStateActionSpace);
                Logging.logger.info("t={} (time={})  - Replay memory(current_size={}, max={}, batch={}) - total_experiences={} - Training frequency={}\n",
                        timeStep / Simulation.timeWindow,
                        timeStep, experienceReplayMemory.size(),
                        experienceReplayMemory.maxSize(),
                        this.learningSettings.sizeExperienceReplayBatch,
                        nOfExperiences,
                        this.learningSettings.trainingFrequency);

                // experienceReplayMemory.isAtFullCapacity() ||
                //experienceReplayMemory.size() >= this.learningSettings.sizeExperienceReplayBatch
                Logging.logger.info("N. of experiences: {}", nOfExperiences);
                //if (experienceReplayMemory.isAtFullCapacity()
                if (experienceReplayMemory.size() >= this.learningSettings.sizeExperienceReplayBatch && nOfExperiences % this.learningSettings.trainingFrequency == 0) {
                    experienceReplay();
                    Logging.logger.info("### FINISHED TRAINING - t={}, total_experiences={}, target_freq={}",
                            timeStep,
                            nOfExperiences,
                            this.learningSettings.targetNetworkUpdateFrequency);

                    if (nOfExperiences % this.learningSettings.targetNetworkUpdateFrequency == 0) {
                        Logging.logger.info("Updating target network (frequency={})", this.learningSettings.targetNetworkUpdateFrequency);
                        String msg = Dao.getInstance().getServer().updateTargetModel();
                        Logging.logger.info(msg);
                    }
                }

            }
        } else {
            // After the time horizon (i.e., last time step requests are received) the method continues until
            // the last user is delivered
            vehiclePreDecisionsObjMap = this.vehicleVisitsMap;
        }
//        if (predictions == null) {
//            Logging.logger.info(postDecisionStateSpace);
//            HelperIO.saveJSON(postDecisionStateSpaceObj, "null_post.json");
//            Map<Integer, List<Double>> predictions2 = ServerUtil.getPredictionsFrom2(postDecisionStateSpaceObj);
//        }
//        Logging.logger.info(predictions);

        // Find best assignment
        //Map<Vehicle, Set<VisitObj>> vehiclePreDecisionsObjMap = getVisitObjMap(vehiclePreDecisionsMap);
//        for (Map.Entry<Vehicle, VisitObj> vo: vehiclePreDecisionsObjMap.entrySet()
//             ) {
//
//        }
//        AssignmentILP assignment = new AssignmentILP(
//                Simulation.rightTW,
//                vehiclePreDecisionsObjMap,
//                requests);
//        assignment.run(new String[]{Objective.TOTAL_REJECTION, Objective.TOTAL_WAITING});
//
//        AssignmentILP assignment2 = new AssignmentILP(
//                Simulation.rightTW,
//                vehiclePreDecisionsObjMap,
//                requests);
//
//        Logging.logger.info("\n--->ASSIGNMENT 2");
//        assignment2.run(new String[]{Objective.TOTAL_REQUESTS, Objective.TOTAL_WAITING});
//        assignment2.getResult().printRoundResultSummary();
//        assert assignment.getResult().equals(assignment2.getResult());
        AssignmentILP assignment3 = new AssignmentILP(
                Simulation.rightTW,
                vehiclePreDecisionsObjMap,
                requests,
                true);

//        Logging.logger.info("\n--->ASSIGNMENT 3");
        assignment3.run(new String[]{Objective.TOTAL_REQUESTS_PLUS_VFS, Objective.TOTAL_WAITING});
        assignment3.getResult().printRoundResultSummary("Online Assignment");

//        if (Sets.difference(assignment2.getResult().getRequestsOK(), assignment3.getResult().getRequestsOK()).size() > 0){
//            Logging.logger.info("VFs shifted assignment");
//        }

//        RewardObject reward = new RewardObject(vehicles, assignment3.getResult());
//        ExperienceObject xp = new ExperienceObject(current, postDecisionStateSpaceObj, reward);
//        xp.remember();


        //        AssignmentILP assignment3 = new AssignmentILP(vehiclePreDecisionsObjMap, requests);
        // assignment2.run(new String[]{Objective.TOTAL_REQUESTS_PLUS_VFS, Objective.TOTAL_WAITING});

        // Create reward object sorted according to vehicle list
        // * vehicle_ids
        // * request_count
        // * delays

//        if (Config.infoHandling.get(Config.SAVE_EXPERIENCES)) {
//
//            String expFolder = InstanceConfig.getInstance().getExperiencesFolder();
//
//            String filepathStateSpace = String.format(
//                    "%s/%04d_current.json",
//                    expFolder,
//                    Simulation.rightTW);
//
//            String filepathDecisionSpace = String.format(
//                    "%s/%04d_decisions.json",
//                    expFolder,
//                    Simulation.rightTW);
//
//            String filepathRewards = String.format(
//                    "%s/%04d_reward.json",
//                    expFolder,
//                    Simulation.rightTW);
//
//            String filepathPostDecisionSpace = String.format(
//                    "%s/%04d_post_decisions_step=%04d.json",
//                    expFolder,
//                    Simulation.rightTW,
//                    Simulation.timeWindow);

//            HelperIO.saveJSON(preDecisionSpaceObj, filepathDecisionSpace);
//            HelperIO.saveJSON(current, filepathStateSpace);
//            HelperIO.saveJSON(reward, filepathRewards);
//            HelperIO.saveJSON(postDecisionStateSpaceObj, filepathPostDecisionSpace);
        //List<Double> predictions = ServerUtil.getPredictionsFromJsonFile(filepathPostDecisionSpace);


//            int totalTimeHorizon = 1 * Simulation.timeWindow;
//            int timeStep = Simulation.timeWindow;
//            genPostDecisionUntil(preDecisionStateSpace, expFolder, timeStep, totalTimeHorizon);
//        }
        //assert vehiclesVisitsSameOrder(vehicleVisitsMap, preDecisionStateSpace.getVehicleDecisionsMap());

//        int timestepSec = Simulation.timeWindow;
//        PostDecisionStateSpace postDecisionStateSpace = new PostDecisionStateSpace(preDecisionStateSpace, timestepSec);
//        DecisionSpaceObject postDecisionSpaceObj = postDecisionStateSpace.getStateObject();


//        experiences.add(new Experience(timeStep, preDecisionStateSpace, assignment.getResult()));
//        if (experiences.size() > 5) {
//            Experience pastExp = experiences.get(0);
//            Logging.logger.info("# ASSIGNMENT PAST{}", Simulation.rightTW);
//            AssignmentILP assignmentPast = new AssignmentILP(getVisitObjMap(pastExp.decisions), requests);
//            Experience e = new Experience(timeStep, pastExp.state, pastExp.decisions, assignmentPast.getResult());
//            assert e.rewardRequest == pastExp.rewardRequest;
//            assert e.rewardDelay == pastExp.rewardDelay;
//
//        }


        return assignment3.getResult();
    }

    private void addRebalancingVisits() {
        int[] zone_ids = new int[]{105, 116, 152, 264, 305, 354, 372, 388, 592, 612, 806, 828, 845, 869, 885, 932, 986, 1005, 1008, 1044, 1085, 1189, 1219, 1237, 1242, 1269, 1422, 1564, 1587, 1641, 1789, 1941, 2056, 2246, 2249, 2335, 2343, 2405, 2424, 2462, 2500, 2608, 2731, 2740, 2944, 2957, 2992, 3018, 3153, 3176, 3218, 3221, 3248, 3251, 3387, 3511, 3746, 3788, 3838, 3848, 3953, 3978, 4059, 4097, 4357, 4362, 4419};
        for (int zone_id : zone_ids) {
            for (Vehicle vehicle : this.vehicleVisitsMap.keySet()) {

                boolean vehicleNotInTargetZone = vehicle.getLastVisitedNode().getNetworkId() != zone_id;
                if (vehicle.isParked() && vehicleNotInTargetZone) {
                    NodeTargetRebalancing targetNode = new NodeTargetRebalancing(vehicle, zone_id);
                    VisitRelocation visit = new VisitRelocation(targetNode, vehicle);
                    this.vehicleVisitsMap.get(vehicle).add(visit);
                    this.visits.add(visit);
                }
            }
        }
    }

    private boolean isLearning() {
        return this.learningSettings != null;
    }

    private boolean isNotTerminal(int currentTime) {
        return currentTime < Simulation.timeHorizon;
    }

    private boolean experienceReplayEnabled() {
        return experienceReplayMemory != null;
    }

    private boolean currentTimeWithinSamplingWindow() {
        return Simulation.rightTW < Simulation.timeHorizon;
    }

    private void genPostDecisionUntil(FleetStateActionSpace preDecisionFleetStateActionSpace, String expFolder, int step, int nTimeStepsForward) {
        for (int futureStep = step; futureStep <= nTimeStepsForward; futureStep += step) {
            FleetStateActionSpace postDecisionFleetStateActionSpace = new PostDecisionFleetStateActionSpace(preDecisionFleetStateActionSpace, futureStep);
            FleetStateActionSpaceObject postDecisionStateSpaceObj = postDecisionFleetStateActionSpace.getDecisionSpaceObject();

            String filepathPostDecisionSpace = String.format(
                    "%s/%04d_post_decisions_step=%04d.json",
                    expFolder,
                    step,
                    futureStep);

            HelperIO.saveJSON(postDecisionStateSpaceObj, filepathPostDecisionSpace);
            List<Double> predictions = Dao.getInstance().getServer().getPredictionsFromJsonFile(filepathPostDecisionSpace);

        }
    }

    /**
     * Return a vehicle-visits map where predictions have been associated with VehicleState objects.
     *
     * @param vehiclePreDecisionsMap
     * @param predictions
     * @return
     */
    private Map<Vehicle, Set<VisitObj>> getVisitObjMap(
            Map<Vehicle, Set<StateAction>> vehiclePreDecisionsMap,
            Map<Integer, List<Double>> predictions) {

        // Map is constructed in the same order of original
        Map<Vehicle, Set<VisitObj>> map = new LinkedHashMap<>();
        vehiclePreDecisionsMap.forEach((vehicle, decisions) -> {
            List<Double> vfs = predictions.get(vehicle.getId());
            Set<VisitObj> visits = new LinkedHashSet<>();
            int i = 0;
            for (VisitObj decision : decisions) {
                decision.setVF(vfs.get(i));
                i++;
                visits.add(decision);
            }
            map.put(vehicle, visits);
        });

        return map;
    }

    /**
     * Return a vehicle-visits map where predictions are turned off.
     *
     * @param vehiclePreDecisionsMap
     * @return
     */
    private Map<Vehicle, Set<VisitObj>> getVisitObjMap(Map<Vehicle, Set<StateAction>> vehiclePreDecisionsMap) {
        // Map is constructed in the same order of original
        Map<Vehicle, Set<VisitObj>> map = new LinkedHashMap<>();
        vehiclePreDecisionsMap.forEach((vehicle, decisions) -> {
            Set<VisitObj> visits = new LinkedHashSet<>();
            int i = 0;
            for (VisitObj decision : decisions) {
                decision.setVF(0);
                i++;
                visits.add(decision);
            }
            map.put(vehicle, visits);
        });

        return map;
    }

    @Override
    public String toString() {
        return "_OPT-JAVIERVF";
    }


    @Override
    public void realize(Set<VisitObj> visits) {
        visits.forEach(this::realizeVisit);
    }
}

