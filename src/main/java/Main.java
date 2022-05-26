import config.Config;
import config.CustomerBaseConfig;
import config.InstanceConfig;
import dao.Dao;
import helper.HelperIO;
import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;
import model.node.NodeMiddle;
import simulation.Simulation;
import simulation.Solution;
import simulation.matching.*;
import simulation.rebalancing.Rebalance;
import simulation.rebalancing.RebalanceStrategy;

import java.io.IOException;
import java.util.Date;
import java.util.Random;

/**
 * Execute all instances from test cases in json file
 */
public class Main {

    public static void main(String[] args) {
        String configFilePath = args[0];
        InstanceConfig instanceSettings;
        try {

            instanceSettings = Config.createInstanceFrom(configFilePath);


            // Vary test case parameters
            for (Date earliestTime : instanceSettings.getEarliestTimeArray()) {
                for (String spMethod : instanceSettings.getShortestPathAlgorithm()) {
                    for (InstanceConfig.TimeConfig timeHorizon : instanceSettings.getTimeHorizonArray()) {
                        for (int maxRequestsIteration : instanceSettings.getMaxRequestsIterationArray()) {
                            for (double percentageRequestsIteration : instanceSettings.getPercentageRequestsIterationArray()) {
                                for (int timeWindow : instanceSettings.getTimeWindowArray()) {
                                    for (int vehicleMaxCapacity : instanceSettings.getVehicleMaxCapacityArray()) {
                                        for (int initialFleet : instanceSettings.getInitialFleetArray()) {
                                            for (boolean isAllowedToHire : instanceSettings.getAllowVehicleHiringArray()) {
                                                for (boolean isAllowedToDisplaceRequests : instanceSettings.getAllowRequestDisplacementArray()) {
                                                    for (int contractDuration : instanceSettings.getContractDurationArray()) {
                                                        for (CustomerBaseConfig customerBaseSettings : instanceSettings.getCustomerBaseSettingsArray()) {


                                                            for (RebalanceStrategy rebalanceStrategy : instanceSettings.getRebalancingMethods()) {
                                                                for (RideMatchingStrategy matchingMethod : instanceSettings.getMatchingMethods()) {
                                                                    for (InstanceConfig.LearningConfig learningConfig : instanceSettings.getLearningConfigs()) {


                                                                        //String fileName = String.format("v=%04d_tw=%04d_h=%06d_req=%04d", initialFleet, timeWindow, timeHorizon, maxRequestsIteration);


                                                                        // Update global class configuration to run current test case
                                                                        Config.getInstance().updateQosDic(customerBaseSettings.qosDic);
                                                                        Config.getInstance().setShortestPathAlgorithm(spMethod);

                                                                        Rebalance rebalancingSettings = new Rebalance(rebalanceStrategy);

                                                                        Matching matchingSettings = new Matching(
                                                                                customerBaseSettings,
                                                                                contractDuration,
                                                                                rebalancingSettings,
                                                                                isAllowedToHire && matchingMethod instanceof MatchingOptimalServiceLevelAndHire,
                                                                                isAllowedToDisplaceRequests,
                                                                                matchingMethod);

                                                                        // Save data from matching methods in the same file for all possible earliest times
                                                                        String fileName = Solution.getTestCaseName(
                                                                                instanceSettings.getInstanceName(),
                                                                                null,
                                                                                instanceSettings.getMaxTimeToReachRegionCenter(),
                                                                                initialFleet,
                                                                                maxRequestsIteration,
                                                                                percentageRequestsIteration,
                                                                                vehicleMaxCapacity,
                                                                                timeWindow,
                                                                                timeHorizon,
                                                                                contractDuration,
                                                                                matchingSettings.isAllowUserDisplacement(),
                                                                                isAllowedToHire,
                                                                                customerBaseSettings.serviceRateLabel,
                                                                                customerBaseSettings.customerSegmentationLabel,
                                                                                rebalanceStrategy,
                                                                                null);

                                                                        learningConfig.setModelInstanceLabel(fileName);


                                                                        int N_OF_EPISODES = 1;
                                                                        boolean learningIsActive = true;
                                                                        if (matchingMethod instanceof MatchingSimple) {
                                                                            N_OF_EPISODES = learningConfig.requestSamples;
                                                                            learningIsActive = true;
                                                                            ((MatchingSimple) matchingMethod).configureLearning(learningConfig);
                                                                            String msg1 = Dao.getInstance().getServer().loadModelAt(learningConfig.getModelFilePath());
                                                                            System.out.println(msg1);
                                                                            String msg2 = Dao.getInstance().getServer().createExperiencesFolderAt(learningConfig.getExperiencesFolder());
                                                                            System.out.println(msg2);
                                                                        }


                                                                        for (int iEpisode = 0; iEpisode < N_OF_EPISODES; iEpisode++) {
                                                                            //Dao.getInstance().setRandomSeed(new Random(iEpisode));
                                                                            // Loop throughout the training data to diversify experiences
                                                                            for (Date episodeStartDatetime : learningConfig.episodeStartDatetimeList){


                                                                                // Users are sampled randomly from batch
                                                                                // Vehicles start at random positions
                                                                                Random randomSeed = new Random(iEpisode);

                                                                            //TODO instance objects generating solution objects
                                                                            // Create FCFS simulation
                                                                            Simulation simulation = new SimulationFCFS(
                                                                                    instanceSettings.getInstanceName(),
                                                                                    episodeStartDatetime,
                                                                                    instanceSettings.getMaxTimeToReachRegionCenter(),
                                                                                    initialFleet,
                                                                                    vehicleMaxCapacity,
                                                                                    maxRequestsIteration,
                                                                                    percentageRequestsIteration,
                                                                                    timeWindow,
                                                                                    timeHorizon,
                                                                                    contractDuration,
                                                                                    isAllowedToHire,
                                                                                    customerBaseSettings.serviceRateLabel,
                                                                                    customerBaseSettings.customerSegmentationLabel,
                                                                                    rebalancingSettings,
                                                                                    matchingSettings,
                                                                                    randomSeed);

                                                                            // Run simulation

                                                                            if (simulation.instanceAlreadyProcessed() && !learningIsActive) {
                                                                                System.out.println(simulation.getSol().getOutputFile() + " already exists.");
                                                                            } else {
                                                                                System.out.println(String.format("# Processing instance \"%s\"...", simulation.getSol().getOutputFile()));


                                                                                simulation.run();


                                                                                if (learningIsActive) {
                                                                                    // Saving episode
                                                                                    String headers = "method;episode;earliest,n_vehicles;n_requests;n_finished;service_rate;max_rounds;round_count;total_delay;distance_empty;distance_loaded,runtime_sec";
                                                                                    String episodeInfo = simulation.getSummary(iEpisode);
                                                                                    System.out.println(episodeInfo);
                                                                                    HelperIO.saveDataWithHeaders(fileName + ".csv", episodeInfo, headers, true);
                                                                                    if (matchingMethod instanceof MatchingSimple) {
                                                                                        String msg = Dao.getInstance().getServer().saveModelAt(learningConfig.getModelFilePath());
                                                                                        System.out.println("Model saved at " + msg);
                                                                                    }
                                                                                }
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
                    }

                }
            }
        } catch (IOException e) {
            System.out.println("Error! Cannot read " + configFilePath);
            e.printStackTrace();
        }
    }
}
