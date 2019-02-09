package simulation;

import config.Config;
import config.Rebalance;
import dao.Dao;
import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SimulationFCFS extends Simulation {

    /* METHOD CONFIGURATION*/
    private int maxPermutationsFCFS;
    private boolean allPermutations;
    private boolean stopAtFirstBest;
    private boolean checkInParallel;
    private Path outputFile;

    /* Construct FCFS simulation */
    public SimulationFCFS(String methodName,
                          int initialFleet,
                          int vehicleMaxCapacity,
                          int maxRequestsIteration,
                          int timeWindow,
                          int timeHorizon,
                          boolean allowRebalancing,
                          int contractDuration,
                          boolean isAllowedToHire,
                          boolean isAllowedToLowerServiceLevel,
                          String serviceRateScenarioLabel,
                          String segmentationScenarioLabel,
                          Rebalance rebalance) {


        // Build generic Simulation object
        super(initialFleet,
                vehicleMaxCapacity,
                maxRequestsIteration,
                timeWindow,
                timeHorizon,
                allowRebalancing,
                contractDuration,
                isAllowedToHire,
                isAllowedToLowerServiceLevel,
                rebalance);

        // Service rate and segmentation scenarios
        this.serviceRateScenarioLabel = serviceRateScenarioLabel;
        this.segmentationScenarioLabel = segmentationScenarioLabel;

        /* METHOD CONFIGURATION*/
        maxPermutationsFCFS = 100; //Restrict the number of permutations
        allPermutations = true;  //True, if all permutations (considering user+vehicle stops) should be tested
        stopAtFirstBest = false;  //True, if solution is returned when first user/vehicle combination is found
        checkInParallel = false; //True, if visits should be checked in parallel

        // Initialize solution
        sol = new Solution(
                methodName,
                initialFleet,
                maxRequestsIteration,
                vehicleMaxCapacity,
                timeWindow,
                timeHorizon,
                allowRebalancing,
                contractDuration,
                isAllowedToHire,
                isAllowedToLowerServiceLevel,
                serviceRateScenarioLabel,
                segmentationScenarioLabel,
                rebalance);
    }

    /**
     * Create a vehicle of capacity "capacity" positioned at a random node.
     *
     * @param u Capacity of vehicle
     * @return Vehicle at random position
     */
    public static Vehicle createVehicleAtRandomPosition(User u, int currentTime, int contractDuration) {

        // Nodes that can reach user (within max. duration)
        short userNetworkId = (short) u.getNodePk().getNetworkId();
        String userClass = u.getPerformanceClass();
        List<Short> canReach = Dao.getInstance().getCanReachList().get(userNetworkId).get(userClass);

        // Network id
        short randomVehicleOrigin = canReach.get(Dao.getInstance().rand.nextInt(canReach.size()));

        // When vehicle have to be released
        int contractDeadline = currentTime;
        int distOriginPkUser = Dao.getInstance().getDistSec(
                randomVehicleOrigin,
                u.getNodePk().getNetworkId());

        int distPkDp = u.getDistFromTo();
        //System.out.println("Distance origin pickup user: " + distOriginPkUser);
        //System.out.println("   Distance pickup delivery: " + distPkDp);
        //System.out.println("               Current time: " + contractDeadline);
        //System.out.println("              User deadline: (pk:" + u.getNodePk().getLatest() + " / dp:"+ u.getNodeDp().getLatest()+ ")");
        //System.out.println("   Distance pickup delivery: " + distPkDp);

        // Deadline is the delivery time of user who caused hiring
        if (contractDuration == Simulation.DURATION_SINGLE_RIDE) {
            contractDeadline += distOriginPkUser + distPkDp;
            //System.out.println("  (single) Contract deadline: " + contractDeadline);

        } else {
            contractDeadline += contractDuration;
            //System.out.println("          Contract deadline: " + contractDeadline);
        }


        return new Vehicle(u.getNumPassengers(), randomVehicleOrigin, currentTime, true, contractDeadline);
    }

    /**
     * Get a new vehicle to user:
     * - According to a probability
     * > For 100% service rate class, every missed pickup entails the creation of a new vehicle.
     * > For 0% service rate class, vehicle is never created
     * - If user has been delayed "maxDelayExtensionsBeforeHiring" times
     *
     * @param u User that will potentially have a new vehicle hired
     * @return True, if vehicle should be hired
     */
    boolean hireNewVehicleToUser(User u) {

        if (!this.isAllowedToHire) {
            return false;
        }

        // Draw to decide if customer will be serviced
        double draw = Dao.getInstance().rand.nextDouble();

        // Get service rate of user u
        double serviceRate = Config.getInstance().qosDic.get(u.getPerformanceClass()).serviceRate;

        //System.out.print(String.format("\n2) %f <= %f : User: %s", draw, serviceRate, u));

        // E.g., B - 0.8 <= 1? Yes! Add vehicle and try again to service customer
        // E.g., A - 1.0 <= 1? Yes! Add vehicle and try again to service customer
        return (draw <= serviceRate);
    }

    /**
     * Test whether it is beneficial send different vehicles to service users in the same locations.
     *
     * @param u
     */
    public void computeAttractivenessLocationUser(User u) {

        Node pk = u.getNodePk();

        // This node should have more vehicles around it
        pk.increaseHotness();


        if (!rebalanceUtil.allowManyToOneTarget) {
            // Vehicles are sent to the same places in the network
            Vehicle.setOfHotPoints.add(pk);

            // Do not send vehicles to the same places
        } else if (!Node.tabu.contains(pk.getNetworkId())) {
            // Only add points not yet being addressed by other vehicles
            Vehicle.setOfHotPoints.add(pk);
        }
    }

    /**
     * Try to insert a user in every vehicle, and return the set of users inserted.
     * <p>
     * If user cannot be inserted:
     * - Hire a new vehicle
     * - Lower user service level (increase maximum delays)
     *
     * @return setServicedUsers Set of all users that could be inserted into vehicles
     */
    //@Override
    public Set<User> getServicedUsersDynamicSizedFleet(int currentTime) {

        // Set of users serviced
        Set<User> setServicedUsers = new HashSet<>();

        // Loop users
        for (User u : setWaitingUsers) {

            // User waits one or more rounds
            u.increaseRoundsWaiting();

            //################### REBALANCE ##### REBALANCE ##### REBALANCE ############################################
            // All points are relocation targets BUT failed pickups have priority
            computeAttractivenessLocationUser(u);

            // Aux. best visit for comparison
            Visit bestVisit = u.getBestVisitByInsertion(
                    listVehicles,
                    currentTime,
                    stopAtFirstBest);

            //##########################################################################################################
            //## VISIT WAS NOT FOUND ###################################################################################
            //##########################################################################################################

            // Depending on user class, decide if new vehicle will be created.
            if (bestVisit == null) {

                //################### REBALANCE ##### REBALANCE ##### REBALANCE ########################################
                // Some vehicle should be sent there urgently to fix supply-demand imbalance
                if (this.rebalanceUtil.useUrgentKey)
                    u.getNodePk().increaseUrgency();

                // Try to hire new vehicle to user according to user SQ class
                if (hireNewVehicleToUser(u)) {

                    // System.out.println("Hiring a vehicle to pickup user " + u);

                    // The draw was successful, vehicle will be hired immediately
                    Vehicle v = null;

                    Visit candidateVisit = null;

                    // Find freelance vehicle around user area
                    while (candidateVisit == null) {

                        v = createVehicleAtRandomPosition(
                                u,
                                currentTime,
                                contractDuration); // List of vehicles

                        //System.out.println("Finding freelance vehicle...");
                        //v.printHiringInfo();

                        //##########################################################################################
                        // Try to get a valid visit by inserting user "u" in newly created vehicle "v"
                        candidateVisit = v.getBestInsertion(u, currentTime);


                        //##########################################################################################
                    }

                    // Update best visit
                    bestVisit = candidateVisit;

                    // New vehicle is added in list
                    listVehicles.add(v);
                    listHiredVehicles.add(v);
                    setHired.add(v);

                } else if (isAllowedToLowerServiceLevel) {
                    //System.out.println("Lowering service level of user " + u);

                    /*
                    Customer is serviced using the current fleet resources.
                    *** Service level is relaxed (unbounded) and an available vehicle is chosen. */

                    // Add enough delay to user service level to guarantee he will be picked up
                    //u.lowerServiceLevel(24 * 3600);
                    // Double max pickup delay of class
                    u.lowerServiceLevel(Config.getInstance().qosDic.get(u.getPerformanceClass()).pkDelay);

                    // Compute need for urgent relocation
                    if (this.rebalanceUtil.useUrgentKey)
                        u.getNodePk().increaseUrgency();

                    // Find a visit using the TRUE fleet availability
                    bestVisit = u.getBestVisitByInsertion(
                            listVehicles,
                            currentTime,
                            stopAtFirstBest);

                    // There is no vehicle with capacity == number of passengers
                    if (bestVisit == null) {

                        // Creating vehicle nearby user
                        Vehicle v = createVehicleAtRandomPosition(
                                u,
                                currentTime,
                                contractDuration); // List of vehicles

                        // Inserting user in newly created vehicle
                        bestVisit = v.getBestInsertion(u, currentTime);

                        // Adding created vehicle to fleet
                        listVehicles.add(v);
                        listHiredVehicles.add(v);
                        setHired.add(v);
                    }
                } else {
                    if (rebalanceUtil.showInfo)
                        System.out.println("CAN'T SERVICE - User:" +
                                u + " - Node PK: " +
                                u.getNodePk() +
                                " - Node PK network id:" +
                                u.getNodePk().getNetworkId() +
                                " - Increasing");
                    // User is rejected if no vehicle could be hired and service level could not be met
                }
            }

            // If best visit is found, update vehicle with visit data
            if (bestVisit != null) {

                // Add user to vehicle and setup visit
                setup(bestVisit);

                // Model.User u was serviced
                setServicedUsers.add(u);
            }
        }

        // Return all serviced users
        return setServicedUsers;

    }


}
