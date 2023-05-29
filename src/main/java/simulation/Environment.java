package simulation;

import config.*;
import dao.Dao;
import dao.Logging;
import model.FleetConfig;
import model.PathBuilder;
import model.Vehicle;
import model.demand.*;
import model.learn.StateAction;
import model.network.NetworkLoaded;
import model.network.TransportNetwork;
import model.node.Node;
import model.node.NodeDropoff;
import model.node.NodePickup;
import model.node.NodeWaypoint;
import model.visit.Visit;
import model.visit.VisitDisplaceAndStop;
import model.visit.VisitObj;
import simulation.matching.Matching;
import util.pdcombinatorics.PDPermutations;

import java.time.LocalDateTime;
import java.util.*;

import static config.Config.getInstance;

public class Environment {

    private static final int MAX_PICKUP_DELAY = 300;

    public Matching matching;
    public FleetConfig fleetConfig;
    public DemandConfig demandConfig;
    public TimeConfig timeConfig;
    public TransportNetwork network;
    public RequestUtil demand;
    private NetworkConfig networkConfig;
    private MatchingConfig matchingConfig;
    private List<Request> requests;

    public Environment(InstanceData instance) {
        this.updateFleetConfig(instance.fleetConfig);
        this.updateDemandConfig(instance.demandConfig);
        this.updateNetworkConfig(instance.networkConfig);
        this.updateTimeConfig(instance.timeConfig);
        this.updateMatchingConfig(instance.matchingConfig);
    }

    public Environment(
            FleetConfig fleetConfig,
            DemandConfig demandConfig,
            TransportNetwork network,
            TimeConfig timeConfig,
            Matching matchingConfig) {
        this.fleetConfig = fleetConfig;
        this.demandConfig = demandConfig;
        this.network = network;
        this.timeConfig = timeConfig;
        this.matching = matchingConfig;


    }

    public static Visit getBestVisitFromPDPermutationsSummarized(Vehicle vehicle, Set<User> requests, Environment env) {
        // A single request can be inserted in a vehicle in multiple ways. Only the best (i.e., the lowest delay)
        // visit is inserted in the RTV graph.

        Visit visit = null;
        Node[] lowestDelaySequence = null;
        PathBuilder bestDraftVisit = null;

        // Create request set out of one request
        PDPermutations perms = new PDPermutations(requests, vehicle);

        while (perms.hasNext()) {

            Node[] PDPermutation = perms.next();
            // getMapNetworkIdNodes(sequencePickupsAndDeliveries);

            PathBuilder draftVisit = env.getDraftVisit(vehicle, PDPermutation);

            // Update if delay is valid
            if (draftVisit != null) {
                if (draftVisit.compareTo(bestDraftVisit) < 0) {
                    bestDraftVisit = draftVisit;
                    lowestDelaySequence = PDPermutation;
                }
            }
        }

        if (lowestDelaySequence != null) {

            // Setup new visit
            visit = new Visit(lowestDelaySequence, bestDraftVisit.delay, bestDraftVisit.delayBonus, bestDraftVisit.idleness, vehicle, requests);

        }

        return visit;
    }

    public static int getTimestampFrom(int earliest, String qos) {
        return earliest + getInstance().qosDic.get(qos).pkDelay;
    }

    public static void reset() {
        Logging.logger.info("Resetting environment");
        // Reset classes for next iteration
        Dao.getInstance().resetRecords();
        User.reset();
        Vehicle.reset();
        Node.reset();
        NodeWaypoint.reset();
        Visit.reset();
        Solution.reset();
        return;
    }

    private void updateMatchingConfig(MatchingConfig matchingConfig) {
        this.matchingConfig = matchingConfig;
    }

    private void updateTimeConfig(TimeConfig timeConfig) {
        this.timeConfig = timeConfig;
    }

    private void updateNetworkConfig(NetworkConfig networkConfig) {
        this.networkConfig = networkConfig;
        this.network = new NetworkLoaded(
                this.networkConfig.distances_file(),
                this.networkConfig.adjacency_matrix_file(),
                this.networkConfig.zone_data_file(),
                this.networkConfig.network_node_info_file(),
                this.networkConfig.shortest_path_method(),
                this.networkConfig.avg_speed_km_hour());
    }

    private void updateDemandConfig(DemandConfig demandConfig) {
        this.demandConfig = demandConfig;
        demand = new RequestCSV(demandConfig.requestFile());
    }

    private void updateFleetConfig(FleetConfig fleetConfig) {
        this.fleetConfig = fleetConfig;
    }

    public Set<Vehicle> createListVehicles(int currentTime, int seed) {
        Random randomSeed = new Random(seed);
        Logging.logger.info("Creating vehicles...");

        Set<Vehicle> listVehicle = new HashSet<>();

        for (int idxVehicle = 0; idxVehicle < this.fleetConfig.vehicles().get(0).nVehicles(); idxVehicle++) {

            int randomOrigin = (short) (randomSeed.nextDouble() * this.getNetwork().getZoneIds().size());
            int departureZoneId = this.getNetwork().getZoneIds().get(randomOrigin);
            Vehicle v = new Vehicle(this.fleetConfig.vehicles().get(0).capacity(), departureZoneId, currentTime);

            listVehicle.add(v);
        }
        return listVehicle;
    }


    public VisitDisplaceAndStop getVisitRelocationToMiddle(Vehicle vehicle) {
        Node middle = vehicle.getMiddleNode();
        return new VisitDisplaceAndStop(middle, vehicle, this);
    }

    public void updatePerformanceClass(User user, String performanceClass) {

        user.performanceClass = performanceClass;

        int originId = Integer.parseInt(user.record.get(Request.PICKUP_NODE_ID));
        int destinationId = Integer.parseInt(user.record.get(Request.DROPOFF_NODE_ID));
        double originLat = 0; //Double.parseDouble(record.get("pickup_latitude"));
        double originLon = 0; //Double.parseDouble(record.get("pickup_longitude"));
        double destinationLat = 0; //Double.parseDouble(record.get("dropoff_latitude"));
        double destinationLon = 0; //Double.parseDouble(record.get("dropoff_longitude"));
        int pk_latest = getTimestampFrom(user.getReqTime(), performanceClass);
        int dp_earliest = getEarliestDp(user.getReqTime(), originId, destinationId);
        int dp_latest = getLatestDp(user.getReqTime(), originId, destinationId, performanceClass);
        user.sharingAllowed = Config.getInstance().qosDic.get(user.performanceClass).allowedSharing;
        user.qos = getInstance().qosDic.get(user.getPerformanceClass());

        //Logging.logger.info(this.reqTime + "-" + pk_latest + ": " +  dp_earliest + ": " + " = " + dp_latest);

        // Start nodes
        user.nodePickup = new NodePickup(
                originId,
                originLat,
                originLon,
                user.getId(),
                user.getReqTime(),
                pk_latest,
                user.getNumPassengers());

        user.nodeDp = new NodeDropoff(
                destinationId,
                destinationLat,
                destinationLon,
                user.getId(),
                dp_earliest,
                dp_latest,
                -user.getNumPassengers());

        // Save all users
        User.mapOfUsers.put(user.getId(), user);
    }

    public int getEarliestDp(int earliest, int from, int to) {
        return earliest + this.getNetwork().getDistSec(from, to);
    }

    public int getLatestDp(int earliest, int from, int to, String qos) {
        return earliest + this.getNetwork().getDistSec(from, to) + getInstance().qosDic.get(qos).dpDelay;
    }

    /**
     * When visits are set up, define for each node the expected arrival time.
     * This can be used to invalidate routes: visit is valid only when pickup times decrease.
     */
    public void updateArrivalSoFarAtVisitNodes(VisitObj visit) {
        if (!visit.getVehicle().isParked()) {
            Node source = visit.getVehicle().getLastVisitedNode();
            int arrival = visit.getVehicle().getEarliestDeparture();
            for (Node target : visit.getSequenceVisits()) {
                arrival += getDistanceSeconds(source, target);
                // TODO add service time
                target.setArrivalSoFar(arrival);
                source = target;
            }
        }
    }

    public int getDistanceSeconds(Node source, Node target) {
        return this.getNetwork().getDistSec(source.getNetworkId(), target.getNetworkId());
    }

    /**
     * Get arrival time at first node in sequence of visits
     *
     * @param visit
     * @return Arrival time
     */
    public int getArrivalTimeAtNext(VisitObj visit) {
        return visit.getVehicle().getDepartureCurrent() +
                this.getNetwork().getDistSec(visit.getVehicle().getLastVisitedNode().getNetworkId(),
                        visit.getSequenceVisits().getFirst().getNetworkId());
    }

    public List<Integer> getArrivalTimesFromVisit(Vehicle vehicle, Visit visit) {
        int previousArrival = vehicle.getLastVisitedNode().getDeparture();
        Node previousNode = vehicle.getLastVisitedNode();
        List<Integer> arrivals = new ArrayList<>();
        arrivals.add(previousArrival);

        for (Node currentNode : visit.getSequenceVisits()) {
            previousArrival += this.getNetwork().getDistSec(
                    previousNode.getNetworkId(),
                    currentNode.getNetworkId());
            //TODO add service time
            arrivals.add(previousArrival);
            previousNode = currentNode;
        }
        return arrivals;
    }


    public TransportNetwork getNetwork() {
        return network;
    }


    /**
     * Pull new requests (within TW) from database. Stop pulling if number of simulation rounds has reached set limit.
     */
    public Set<User> pullCurrentRequestBatch(int currentTime, int seed) {

        // Clean list of pooled users


        // After the number of rounds stop pooling requests but finish waiting requests
        if (currentTime <= this.timeConfig.totalSimulationHorizonSec()) {

//            // Dictionary of pooled requests inside time slot
//            newUsers = Dao.getInstance()
//                    .getListTripsClassedShuffled(
//                            this.timeConfig.startDateTime(),
//                            this.timeConfig.timeWindowSec(),
//                            this.fleetConfig.vehicles().get(0).nVehicles(),
//                            this.demandConfig.percentageRequests(),
//                            new Random(seed));
        }
//        return newUsers;
        return null;
    }


    public boolean instanceAlreadyProcessed() {
//        return Files.exists(this.sol.getOutputFile());
        return false;
    }


    //****************************************************************************************************************//
    //***** ASSERTIONS ***********************************************************************************************//
    //****************************************************************************************************************//
//    public boolean allVehicleVisitsAreValid() {
//        for (Vehicle vehicle : listVehicles) {
//            if (vehicle.getVisit() != null && !vehicle.getVisit().isValid()) {
//                Logging.logger.info("{}", String.format("Sequence of vehicle %s is invalid! Visit: %s", vehicle, vehicle.getVisit()));
//                return false;
//            }
//        }
//        return true;
//    }


    /**
     * Update the intermediate position (middle node) of vehicle given current time.
     * If:
     * - Vehicle is parked -> return null (no middle)
     * - Vehicle has been assigned to a visit, but did not leave origin node -> return origin node
     * - Vehicle has been assigned to a visit, but left origin node -> return closest middle, for example:
     * Origin--> currentTime --> M1 --------------> M2 -----> Destination --------------- (return M1)
     * Origin------------------> M1 --currentTime-> M2 -----> Destination --------------- (return M2)
     * Origin------------------> M1 --------------> M2 -----> Destination ----currentTime (return Destination)
     * Origin-------------------------currentTime-----------> Destination --------------- (return Destination)
     *
     * @param currentTime
     */
    public void updateMiddle(int currentTime, Vehicle vehicle) {

        if (vehicle.isParked()) {
            vehicle.setMiddleNode(null);
            vehicle.distMiddleNode = 0;
        } else {

            int elapsedTimeSinceLeftLastNode = currentTime - vehicle.getEarliestDeparture();

            int networkIdWaypointNode = network.getNodeBetweenAndExtraDelay(
                    vehicle.getLastVisitedNode(),
                    vehicle.getVisit().getTargetNode(),
                    elapsedTimeSinceLeftLastNode);

            // Distance from current to middle node
            vehicle.distMiddleNode = Math.max(elapsedTimeSinceLeftLastNode, network.getDistSec(
                    vehicle.getLastVisitedNode().getNetworkId(),
                    networkIdWaypointNode));

            Node middle = new NodeWaypoint(
                    networkIdWaypointNode,
                    vehicle.getLastVisitedNode(),
                    vehicle.getVisit().getTargetNode(),
                    vehicle.distMiddleNode);

            vehicle.setMiddleNode(middle);
        }
    }

    public boolean vehicleCanReach(StateAction v1VisitPostState, Vehicle v2PreDecision) {
        // Next node post decision
        Node v1PostNextNode = v1VisitPostState.getNextNode();

        // Next node a vehicle will visit (pre-decision)
        // The assumption is that since the step is small, this holds
        Node v2PreNextNode = v2PreDecision.getTargetNode();
        int delayV1_V2 = this.getNetwork().getDistSec(v1PostNextNode.getNetworkId(), v2PreNextNode.getNetworkId());
        int delayV2_V1 = this.getNetwork().getDistSec(v2PreNextNode.getNetworkId(), v1PostNextNode.getNetworkId());
        return delayV1_V2 <= MAX_PICKUP_DELAY || delayV2_V1 <= MAX_PICKUP_DELAY;
    }


    public String getStats(Vehicle vehicle) {

        // Initial time at origin
        int startTime = vehicle.getOrigin().getArrival();

        // Last arrival
        int endTime = vehicle.getJourney().get(vehicle.getJourney().size() - 1).getArrival();

        // Total operating time
        int operatingTW = endTime - startTime;

        // Delays
        int delayPK = 0, delayDP = 0;

        for (User u : vehicle.getServicedUsers()) {
            delayPK += u.getNodePk().getDelay();
            delayDP += u.getNodeDp().getDelay();
        }

        // Operating time
        int operatingTime = 0;
        for (int i = 0; i < vehicle.getJourney().size() - 1; i++) {
            int fromId = vehicle.getJourney().get(i).getNetworkId();
            int toId = vehicle.getJourney().get(i + 1).getNetworkId();
            int tripDuration = getNetwork().getDistSec(fromId, toId);
            operatingTime += tripDuration;
        }

        // Set up total waiting time
        int totalWaiting = operatingTW - operatingTime;

        return String.format("###### %4s [%4s,%4s] => %4s (work) + %4s (wait) = %4s (%.2f%%) #### PK:%6s  DP:%6s",
                this,
                startTime,
                endTime,
                operatingTime,
                totalWaiting,
                operatingTW,
                (double) operatingTime * 100 / operatingTW,
                delayPK,
                delayDP);
    }

    /* VISIT BUILDING */


    /**
     * Construct visit sequence node by node.
     *
     * @param vehicle         Vehicle carrying out visit
     * @param validPDSequence Valid pickup and delivery sequence
     * @return Last leg of visit.
     */
    public PathBuilder getDraftVisit(Vehicle vehicle, Node[] validPDSequence) {
        //Logging.logger.info("# Vehicle="+vehicle.getInfo());
        //Logging.logger.info(Arrays.toString(validPDSequence));

        PathBuilder currentPath = new PathBuilder(vehicle, this);

        //                 t
        // ----------528--600---628
        //           PK1        DP2
        // Vehicle is moving
        // - Going to a stop point (i.e., closest middle node) upon having its customers displaced (has left last node, but there is no middle node)
        // - Servicing a passenger (has left the last node, and there is a middle node)
        // - Rebalancing (has left the last node, and there is a middle node)


        // Should a middle node be added to the sequence?
        if (vehicle.hasLeftLastNode() && (vehicle.isServicing() || vehicle.isRebalancing())) {

            Node middle = vehicle.getMiddleNode();
            assert middle != null : String.format("vehicle=%s, visit=%s, node=%s", vehicle, vehicle.getVisit(), this.getNetwork().getInfo(vehicle.getLastVisitedNode()));
            // Only add middle if vehicle is moving to different node. Example:
            // O -------- PK1-----DP1 (current route)
            // O -------- PK1 --- PK2 --- DP1 --- DP2 (new sequence)
            // No need to add middle node since vehicle is already moving to PK1.
            if (!vehicle.isMovingToNode(validPDSequence[0])) {

                currentPath.updateNextNode(middle);
                // When middle is updated, vehicle is changing plans
                //assert currentLeg.arrivalNext >= Environment.rightTW: String.format("arrival next=%d, simulation=%d, departure=%d",currentLeg.arrivalNext, Environment.rightTW, vehicle.getDepartureCurrent());
            }
        }
        // Valid sequence = [1,1']
        // Vehicle sequence = [0,1,1']
        //Logging.logger.info(vehicle.getVisit());
        for (Node node : validPDSequence) {

            //Logging.logger.info("{}", String.format("from= %s ### Arrival=%4d ### Delay=%4d\n", currentLeg.fromNode.getInfo(), currentLeg.arrivalNext, currentLeg.delay));
            if (!currentPath.updateNextNode(node)) {
                return null;
            }
//            assert currentPath.arrivalNext >= currentTime;
        }
        return currentPath;
    }

    public LocalDateTime getStartDateTime() {
        return this.timeConfig.startDateTime();
    }

    public List<Request> getRequestsBetween(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        return this.demand.getRequestsBetween(startDateTime, endDateTime);
    }

    public String getRandomClassForRequest(Random randomSeed) {

        int share = 0;
        int randValue = randomSeed.nextInt(101);
        String classUser = null;
        Set<Map.Entry<String, Double>> entries = this.demandConfig.segmentationScenarioMap().entrySet();
        for (Map.Entry<String, Double> e : entries) {
            share += e.getValue() * 100;
            if (randValue <= share) {
                classUser = e.getKey();
            }
        }
        return classUser;
    }

    public int maxVehicleCapacity() {
         return this.fleetConfig.maxVehicleCapacity();
    }

    public int distBetweenOriginDestinationSec(Request request) {
        return this.network.getDistSec(
                request.pickupNodeNetworkId(),
                request.dropoffNodeNetworkId());
    }
}