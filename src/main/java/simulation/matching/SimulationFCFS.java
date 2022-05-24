package simulation.matching;

import config.Config;
import simulation.Simulation;
import simulation.Solution;
import simulation.rebalancing.Rebalance;
import dao.Dao;
import model.User;
import model.Vehicle;
import model.Visit;

import java.nio.file.Path;
import java.util.Date;
import java.util.Random;

public class SimulationFCFS extends Simulation {

    /* METHOD CONFIGURATION*/
    private int maxPermutationsFCFS;
    private boolean allPermutations;
    private boolean stopAtFirstBest;
    private boolean checkInParallel;
    private Path outputFile;

    /* Construct FCFS simulation */
    public SimulationFCFS(String methodName,
                          Date earliestTime,
                          int maxTimeToReachRegionCenter,
                          int initialFleet,
                          int vehicleMaxCapacity,
                          int maxRequestsIteration,
                          double percentageRequestsIteration,
                          int timeWindow,
                          int timeHorizon,
                          int contractDuration,
                          boolean isAllowedToHire,
                          String serviceRateScenarioLabel,
                          String segmentationScenarioLabel,
                          Rebalance rebalance,
                          Matching matchingSettings,
                          Random randomSeed) {


        // Build generic Simulation object
        super(initialFleet,
                vehicleMaxCapacity,
                maxRequestsIteration,
                percentageRequestsIteration,
                earliestTime,
                timeWindow,
                timeHorizon,
                contractDuration,
                isAllowedToHire,
                rebalance,
                matchingSettings,
                randomSeed);

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
                earliestTime,
                maxTimeToReachRegionCenter,
                initialFleet,
                maxRequestsIteration,
                percentageRequestsIteration,
                vehicleMaxCapacity,
                timeWindow,
                timeHorizon,
                contractDuration,
                matchingSettings.isAllowUserDisplacement(),
                isAllowedToHire,
                serviceRateScenarioLabel,
                segmentationScenarioLabel,
                rebalance.strategy,
                matchingSettings.rideMatchingStrategy);
    }

    /**
     * Create a vehicle of capacity "capacity" positioned at a random node.
     *
     * @param u Capacity of vehicle
     * @return Vehicle at random position
     */
    public static Vehicle createVehicleAtRandomPosition(User u, int currentTime, int contractDuration) {

        int closestRegionCenterId = Dao.getInstance().getClosestRegion(u.getNodePk().getNetworkId(), u.getPerformanceClass());

        // When vehicle have to be released
        int contractDeadline = currentTime;
        int distOriginPkUser = Dao.getInstance().getDistSec(closestRegionCenterId, u.getNodePk().getNetworkId());

        int distPkDp = u.getDistFromTo();
        //System.out.println("Distance origin pickup user: " + distOriginPkUser);
        //System.out.println("   Distance pickup delivery: " + distPkDp);
        //System.out.println("               Current time: " + contractDeadline);
        //System.out.println("              User deadline: (pk:" + u.getNodePk().getLatest() + " / dp:"+ u.getNodeDp().getLatest()+ ")");
        //System.out.println("   Distance pickup delivery: " + distPkDp);

        // Deadline is the delivery time of user who caused hiring
        if (contractDuration == Config.DURATION_SINGLE_RIDE) {
            contractDeadline += distOriginPkUser + distPkDp;
            //System.out.println("  (single) Contract deadline: " + contractDeadline);

        } else {
            contractDeadline += contractDuration;
            //System.out.println("          Contract deadline: " + contractDeadline);
        }

        return new Vehicle(u.getNumPassengers(), closestRegionCenterId, currentTime, true, contractDeadline);
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


    private Visit getVisitHiredVehicleUser(User u, int currentTime) {

        // The draw was successful, vehicle will be hired immediately
        Vehicle v = null;

        Visit candidateVisitHiredVehicle = null;

        // Find freelance vehicle around user area
        while (candidateVisitHiredVehicle == null) {

            // For the sake of fairness, position is left to chance
            v = createVehicleAtRandomPosition(
                    u,
                    currentTime,
                    contractDuration); // List of vehicles


            //##########################################################################################
            // Try to get a valid visit by inserting user "u" in newly created vehicle "v"
            candidateVisitHiredVehicle = v.getVisitWithInsertedUser(u, currentTime);
            //##########################################################################################
        }

        // New vehicle is added in list
        listVehicles.add(v);
        listHiredVehicles.add(v);
        setHired.add(v);
        return candidateVisitHiredVehicle;
    }
}
