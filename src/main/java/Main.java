import com.google.gson.Gson;
import config.Config;
import config.CustomerBaseConfig;
import config.InstanceConfig;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Execute all instances from test cases in json file
 */
public class Main {


    public static void main(String[] args) {

        String jsonConfigFilePath;
        int infoLevel;

        try {

            System.out.println("Reading configuration...");
            // Reading input settings
            Map jsonConfig = FileUtil.getMapFrom(args[0]);

            // Instances
            jsonConfigFilePath = jsonConfig.get("instance_file_path").toString();
            System.out.println("Executing configuration at \""+jsonConfigFilePath+"\"...");

            // Round information level (no information, round summary, all information)
            String infoLevelLabel = jsonConfig.get("info_level").toString();
            System.out.println("Round information level: " + infoLevelLabel);
            infoLevel = Simulation.getInfoLevel(infoLevelLabel);

        }
        catch (Exception e){
            System.out.println("Cannot load configuration: "+ e);
            System.out.println("Loading default instance (show round summary)...");
            // Json with instance
            jsonConfigFilePath = "C:\\Users\\LocalAdmin\\IdeaProjects\\slevels\\src\\main\\resources\\default_instance.json";
            infoLevel = Simulation.PRINT_NO_ROUND_INFO;
        }

        InstanceConfig instanceSettings = InstanceConfig.getInstance(jsonConfigFilePath);

        // Vary test case parameters
        for (boolean sortWaitingUsersByClass : instanceSettings.getSortWaitingUsersByClassArray()) {
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

                                                    Instant before = Instant.now();
                                                    List<Rebalance> allRebalancingSettings = new ArrayList<>();

                                                    if (!allowRebalancing) {
                                                        Rebalance rebalanceSettings = new Rebalance(
                                                                false,
                                                                false,
                                                                false,
                                                                false,
                                                                Rebalance.METHOD_HEURISTIC,
                                                                false,
                                                                false
                                                        );

                                                        allRebalancingSettings.add(rebalanceSettings);
                                                    } else {
                                                        allRebalancingSettings = instanceSettings.getListRebalanceSettings();
                                                    }
                                                    for (Rebalance rebalanceSettings : allRebalancingSettings) {

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
                                                                sortWaitingUsersByClass,
                                                                customerBaseSettings.serviceRateLabel,
                                                                customerBaseSettings.customerSegmentationLabel,
                                                                rebalanceSettings);

                                                        // Run simulation
                                                        fcfs.run(infoLevel);

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

                                                    Instant after = Instant.now();
                                                    Duration duration = Duration.between(before, after);
                                                    System.out.println("Duration:" + duration.toMillis());

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
    }
}