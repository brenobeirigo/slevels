import config.Config;
import config.CustomerBaseConfig;
import config.InstanceConfig;
import config.Rebalance;
import dao.Dao;
import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;
import model.node.NodeMiddle;
import simulation.Simulation;
import simulation.SimulationFCFS;
import simulation.Solution;


public class MainJson {


    public static void main(String[] args) {


        InstanceConfig instanceSettings = InstanceConfig.getInstance();
        int levelInfo = Simulation.ALL_INFO;

        // Vary test case parameters
        for (int timeHorizon : instanceSettings.getTimeHorizonArray()) {
            for (int maxRequestsIteration : instanceSettings.getMaxRequestsIterationArray()) {
                for (int timeWindow : instanceSettings.getTimeWindowArray()) {
                    for (int vehicleMaxCapacity : instanceSettings.getVehicleMaxCapacityArray()) {
                        for (int initialFleet : instanceSettings.getInitialFleetArray()) {
                            for (boolean isAllowedToHire : instanceSettings.getAllowVehicleHiringArray()) {
                                for (boolean isAllowedToLowerServiceLevel : instanceSettings.getAllowServiceDeteriorationArray()) {

                                    // If can hire than service level have to be lowered
                                    if (isAllowedToHire != isAllowedToLowerServiceLevel) continue;

                                    for (int contractDuration : instanceSettings.getContractDurationArray()) {

                                        for (CustomerBaseConfig customerBaseSettings : instanceSettings.getCustomerBaseSettingsArray()) {
                                            // Update global class configuration to run current test case
                                            Config.getInstance().qosDic = customerBaseSettings.qosDic;

                                            for (boolean allowRebalancing : instanceSettings.getAllowRebalancingArray()) {

                                                if (allowRebalancing) {
                                                    for (Rebalance rebalanceSettings : instanceSettings.getListRebalanceSettings()) {

                                                        // Create FCFS simulation
                                                        Simulation fcfs = new SimulationFCFS(
                                                                instanceSettings.getInstanceName(),
                                                                initialFleet,
                                                                vehicleMaxCapacity,
                                                                maxRequestsIteration,
                                                                timeWindow,
                                                                timeHorizon,
                                                                allowRebalancing,
                                                                contractDuration,
                                                                isAllowedToHire,
                                                                isAllowedToLowerServiceLevel,
                                                                customerBaseSettings.serviceRateLabel,
                                                                customerBaseSettings.customerSegmentationLabel,
                                                                rebalanceSettings);

                                                        // Run simulation
                                                        fcfs.run(levelInfo);

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
                                                } else {

                                                    Rebalance rebalanceSettings = new Rebalance(
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
                                                            instanceSettings.getInstanceName(),
                                                            initialFleet,
                                                            vehicleMaxCapacity,
                                                            maxRequestsIteration,
                                                            timeWindow,
                                                            timeHorizon,
                                                            allowRebalancing,
                                                            contractDuration,
                                                            isAllowedToHire,
                                                            isAllowedToLowerServiceLevel,
                                                            customerBaseSettings.serviceRateLabel,
                                                            customerBaseSettings.customerSegmentationLabel,
                                                            rebalanceSettings);

                                                    // Run simulation
                                                    fcfs.run(levelInfo);

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
    }
}