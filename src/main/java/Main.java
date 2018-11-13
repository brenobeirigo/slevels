import config.Config;
import config.Config.Qos;
import dao.Dao;
import model.User;
import model.Vehicle;
import model.node.Node;
import simulation.Simulation;
import simulation.SimulationFCFS;

import java.util.HashMap;
import java.util.Map;


public class Main {


    public static void main(String[] args) {

        int[] timeWindowArray = new int[]{30}; // Time window of request collection bin
        int[] timeHorizonArray = new int[]{3600, 86400}; // Time horizon of experiment (0 t0 24h)
        int[] maxRequestsIterationArray = new int[]{1000}; // Max number of requests pooled in during an iteration
        int[] initialFleetArray = new int[]{1000, 2000}; // Initial size of fleet
        int[] vehicleMaxCapacityArray = new int[]{4, 10}; // Max capacity of vehicle


        // Vary service rate scenario (S1, S2, S3 - Different rates for classes A, B, and C)
        HashMap<String, HashMap<String, Double>> serviceRateScenarioMap = new HashMap<String, HashMap<String, Double>>() {
            {
                {
                    put("S1", new HashMap<String, Double>() {{
                        put("A", 1.0);
                        put("B", 0.95);
                        put("C", 0.9);
                    }});
                    put("S2", new HashMap<String, Double>() {{
                        put("A", 0.95);
                        put("B", 0.9);
                        put("C", 0.85);
                    }});
                    put("S3", new HashMap<String, Double>() {{
                        put("A", 0.9);
                        put("B", 0.85);
                        put("C", 0.8);
                    }});
                }
            }
        };

        // Customer segmentation scenario (AA, BB, CC, A, B, and C - Different shares for classes A, B, C)
        HashMap<String, HashMap<String, Double>> segmentationScenarioMap = new HashMap<String, HashMap<String, Double>>() {
            {
                {
                    put("AA", new HashMap<String, Double>() {{
                        put("A", 0.68);
                        put("B", 0.16);
                        put("C", 0.16);
                    }});
                    put("BB", new HashMap<String, Double>() {{
                        put("A", 0.16);
                        put("B", 0.68);
                        put("C", 0.16);
                    }});
                    put("CC", new HashMap<String, Double>() {{
                        put("A", 0.16);
                        put("B", 0.16);
                        put("C", 0.68);
                    }});

                    put("A", new HashMap<String, Double>() {{
                        put("A", 1.0);
                        put("B", 0.0);
                        put("C", 0.0);
                    }});

                    put("B", new HashMap<String, Double>() {{
                        put("A", 0.0);
                        put("B", 1.0);
                        put("C", 0.0);
                    }});
                    put("C", new HashMap<String, Double>() {{
                        put("A", 0.0);
                        put("B", 0.0);
                        put("C", 1.0);
                    }});
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
                    }});
                    put("B", new HashMap<String, Integer>() {{
                        put("pk_delay", 300);
                        put("trip_delay", 600);
                    }});
                    put("C", new HashMap<String, Integer>() {{
                        put("pk_delay", 600);
                        put("trip_delay", 900);
                    }});
                }
            }
        };

        // Vary test case parameters
        for (int initialFleet : initialFleetArray) {
            for (int vehicleMaxCapacity : vehicleMaxCapacityArray) {
                for (int maxRequestsIteration : maxRequestsIterationArray) {
                    for (int timeWindow : timeWindowArray) {
                        for (int timeHorizon : timeHorizonArray) {

                            // All service rate
                            for (Map.Entry<String, HashMap<String, Double>> serviceRateScenario :
                                    serviceRateScenarioMap.entrySet()) {

                                // S1, S2, S3
                                String serviceRateScenarioLabel = serviceRateScenario.getKey();

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
                                                segmentationScenario.getValue().get(serviceLevel.getKey()));

                                        // Update global class configuration to run current test case
                                        Config.getInstance().qosDic.put(serviceLevel.getKey(), qos);
                                    }

                                    // Print Qos for round
                                    Config.getInstance().printQosDic();

                                    // Create FCFS simulation
                                    Simulation fcfs = new SimulationFCFS(
                                            initialFleet,
                                            vehicleMaxCapacity,
                                            maxRequestsIteration,
                                            timeWindow,
                                            timeHorizon,
                                            serviceRateScenarioLabel,
                                            segmentationScenarioLabel);

                                    // Run simulation
                                    fcfs.run();

                                    // Reset classes for next iteration
                                    Dao.getInstance().resetRecords();
                                    User.reset();
                                    Vehicle.reset();
                                    Node.reset();
                                    Config.reset();
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

        //Simulation rtv = new SimulationRTV();
        //rtv.run();

    }
}