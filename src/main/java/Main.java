import config.Config;
import config.Config.Qos;
import config.Rebalance;
import dao.Dao;
import dao.FileUtil;
import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;
import model.node.NodeMiddle;
import simulation.Simulation;
import simulation.SimulationFCFS;
import simulation.Solution;

import java.util.HashMap;
import java.util.Map;


public class Main {


    public static void main(String[] args) {

        int[] timeWindowArray = new int[]{30}; // Time window of request collection bin
        int[] timeHorizonArray = new int[]{600}; // Time horizon of experiment (0 t0 24h)
        int[] maxRequestsIterationArray = new int[]{1000}; // Max number of requests pooled in during an iteration
        int[] initialFleetArray = new int[]{1000}; // Initial size of fleet
        int[] vehicleMaxCapacityArray = new int[]{4}; // Max capacity of vehicle
        boolean[] allowRebalancingArray = new boolean[]{true}; // Each round has TW seconds
        int[] contractDurationArray = new int[]{
                Simulation.DURATION_SINGLE_RIDE}; // In rounds of tw_batch seconds
        boolean[] allowVehicleHiringArray = new boolean[]{false};
        boolean[] allowServiceDeteriorationArray = new boolean[]{false};

        FileUtil.createDir("output");

        FileUtil.createDir("output");

        String instanceName = "testRebalancingSettings";

        boolean[] allowManyToOneTarget = new boolean[]{false, true};
        boolean[] reinsertTargets = new boolean[]{true, false};
        boolean[] clearTargetListEveryRound = new boolean[]{true, false};
        boolean[] useUrgentKey = new boolean[]{false, true};


        // Vary service rate scenario (S1, S2, S3 - Different rates for classes A, B, and C)
        HashMap<String, HashMap<String, Double>> serviceRateScenarioMap = new HashMap<String, HashMap<String, Double>>() {
            {
                {

                    put("S1", new HashMap<String, Double>() {{
                        put("A", 1.0);
                        put("B", 0.9);
                        put("C", 0.8);
                    }});
                    /*
                    put("S2", new HashMap<String, Double>() {{
                        put("A", 0.9);
                        put("B", 0.8);
                        put("C", 0.7);
                    }});

                    put("S3", new HashMap<String, Double>() {{
                        put("A", 0.8);
                        put("B", 0.7);
                        put("C", 0.6);
                    }});
                    */
                }
            }
        };

        // Customer segmentation scenario (AA, BB, CC, A, B, and C - Different shares for classes A, B, C)
        HashMap<String, HashMap<String, Double>> segmentationScenarioMap = new HashMap<String, HashMap<String, Double>>() {
            {
                {
                    /*
                    put("C", new HashMap<String, Double>() {{
                        put("A", 0.0);
                        put("B", 0.0);
                        put("C", 1.0);
                    }});

                    put("B", new HashMap<String, Double>() {{
                        put("A", 0.0);
                        put("B", 1.0);
                        put("C", 0.0);
                    }});

                    put("A", new HashMap<String, Double>() {{
                        put("A", 1.0);
                        put("B", 0.0);
                        put("C", 0.0);
                    }});

                    put("AA", new HashMap<String, Double>() {{
                        put("A", 0.68);
                        put("B", 0.16);
                        put("C", 0.16);
                    }});


                     */

                    put("BB", new HashMap<String, Double>() {{
                        put("A", 0.16);
                        put("B", 0.68);
                        put("C", 0.16);
                    }});
                    /*

                    put("CC", new HashMap<String, Double>() {{
                        put("A", 0.16);
                        put("B", 0.16);
                        put("C", 0.68);
                    }});
                   */
                }
            }
        };

        // Service levels (pickup and trip delays) for classes A, B, and C
        HashMap<String, HashMap<String, Integer>> serviceLevelMap = new HashMap<String, HashMap<String, Integer>>() {
            {
                {
                    put("A", new HashMap<String, Integer>() {{
                        put("pk_delay", 180);
                        put("trip_delay", 180);
                        put("sharing_preference", Qos.PRIVATE_VEHICLE);
                    }});
                    put("B", new HashMap<String, Integer>() {{
                        put("pk_delay", 300);
                        put("trip_delay", 600);
                        put("sharing_preference", Qos.ALLOWED_SHARING);
                    }});
                    put("C", new HashMap<String, Integer>() {{
                        put("pk_delay", 600);
                        put("trip_delay", 900);
                        put("sharing_preference", Qos.ALLOWED_SHARING);
                    }});
                }
            }
        };

        // Vary test case parameters
        for (int timeHorizon : timeHorizonArray) {
            for (int maxRequestsIteration : maxRequestsIterationArray) {
                for (int timeWindow : timeWindowArray) {
                    for (int vehicleMaxCapacity : vehicleMaxCapacityArray) {
                        for (int initialFleet : initialFleetArray) {
                            for (boolean isAllowedToHire : allowVehicleHiringArray) {
                                for (boolean isAllowedToLowerServiceLevel : allowServiceDeteriorationArray) {

                                    // If can hire than service level have to be lowered
                                    if (isAllowedToHire != isAllowedToLowerServiceLevel) continue;

                                    for (int contractDuration : contractDurationArray) {


                                        // All service rate
                                        for (Map.Entry<String, HashMap<String, Double>> serviceRateScenario :
                                                serviceRateScenarioMap.entrySet()) {

                                            // S1, S2, S3
                                            String serviceRateScenarioLabel = serviceRateScenario.getKey();
                                            for (boolean allowRebalancing : allowRebalancingArray) {
                                                // Segmentation scenario - AA, BB, CC, A, B, C
                                                for (Map.Entry<String, HashMap<String, Double>> segmentationScenario :
                                                        segmentationScenarioMap.entrySet()) {

                                                    // AA, BB, CC, A, B, C
                                                    String segmentationScenarioLabel = segmentationScenario.getKey();

                                                    // Fixed service levels - A (180, 180), B(300, 600), C(600, 900)
                                                    for (Map.Entry<String, HashMap<String, Integer>> serviceLevel :
                                                            serviceLevelMap.entrySet()) {

                                                        /* Creating QoS class
                                                         *  - service level class label (A, B, or C)
                                                         *  - pickup delay
                                                         *  - trip delay delay
                                                         *  - service rate (varies according to scenario)
                                                         *  - customer segmentation (varies according to scenario)
                                                         */

                                                        // Setup QoS class
                                                        Qos qos = new Qos(serviceLevel.getKey(),
                                                                serviceLevel.getValue().get("pk_delay"),
                                                                serviceLevel.getValue().get("trip_delay"),
                                                                serviceRateScenario.getValue().get(serviceLevel.getKey()),
                                                                segmentationScenario.getValue().get(serviceLevel.getKey()),
                                                                (serviceLevel.getValue().get("sharing_preference") == Qos.ALLOWED_SHARING));

                                                        // Update global class configuration to run current test case
                                                        Config.getInstance().qosDic.put(serviceLevel.getKey(), qos);
                                                    }

                                                    // Print Qos for round
                                                    Config.getInstance().printQosDic();
                                                    if (allowRebalancing) {
                                                        for (boolean n1 : allowManyToOneTarget) {
                                                            for (boolean reinsert : reinsertTargets) {
                                                                for (boolean clear : clearTargetListEveryRound) {
                                                                    for (boolean useUrg : useUrgentKey) {

                                                                        Rebalance rebalanceUtil = new Rebalance(
                                                                                n1,
                                                                                reinsert,
                                                                                clear,
                                                                                useUrg,
                                                                                "TEST",
                                                                                false,
                                                                                false
                                                                        );

                                                                        // Create FCFS simulation
                                                                        Simulation fcfs = new SimulationFCFS(
                                                                                instanceName,
                                                                                initialFleet,
                                                                                vehicleMaxCapacity,
                                                                                maxRequestsIteration,
                                                                                timeWindow,
                                                                                timeHorizon,
                                                                                allowRebalancing,
                                                                                contractDuration,
                                                                                isAllowedToHire,
                                                                                isAllowedToLowerServiceLevel,
                                                                                serviceRateScenarioLabel,
                                                                                segmentationScenarioLabel,
                                                                                rebalanceUtil);

                                                                        // Run simulation
                                                                        fcfs.run(Simulation.ROUND_INFO);

                                                                        // Reset classes for next iteration
                                                                        Dao.getInstance().resetRecords();
                                                                        User.reset();
                                                                        Vehicle.reset();
                                                                        Node.reset();
                                                                        NodeMiddle.reset();
                                                                        Visit.reset();
                                                                        Simulation.reset();
                                                                        Solution.reset();
                                                                    }
                                                                }
                                                            }
                                                        }

                                                    } else {

                                                        Rebalance rebalanceUtil = new Rebalance(
                                                                false,
                                                                false,
                                                                false,
                                                                false,
                                                                "Rebalancing",
                                                                false,
                                                                false
                                                        );
                                                        // Create FCFS simulation
                                                        Simulation fcfs = new SimulationFCFS(
                                                                instanceName,
                                                                initialFleet,
                                                                vehicleMaxCapacity,
                                                                maxRequestsIteration,
                                                                timeWindow,
                                                                timeHorizon,
                                                                allowRebalancing,
                                                                contractDuration,
                                                                isAllowedToHire,
                                                                isAllowedToLowerServiceLevel,
                                                                serviceRateScenarioLabel,
                                                                segmentationScenarioLabel,
                                                                rebalanceUtil
                                                        );

                                                        // Run simulation
                                                        fcfs.run(Simulation.ROUND_INFO);

                                                        // Reset classes for next iteration
                                                        Dao.getInstance().resetRecords();
                                                        User.reset();
                                                        Vehicle.reset();
                                                        Node.reset();
                                                        NodeMiddle.reset();
                                                        Visit.reset();
                                                        Simulation.reset();
                                                        Solution.reset();
                                                    }

                                                }
                                                Config.reset();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Setup QoS class
        /*
        Qos qos1 = new Qos("A", 180, 180, 1,0);
        Qos qos2 = new Qos("B", 300, 600, 0.95,1);
        Qos qos3 = new Qos("C", 600, 900, 0.8,0);
        Config.getInstance().qosDic.put("A", qos1);
        Config.getInstance().qosDic.put("B", qos2);
        Config.getInstance().qosDic.put("C", qos3);

        Simulation fcfs = new SimulationFCFS(1000, 10, 1000, 30, 24*3600, "S1", "B");
        fcfs.run();

        //System.out.println(Config.getInstance().qosDic);

        //SimulationRTV fcfs = new SimulationRTV();
        */


    }
}