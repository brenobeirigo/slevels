import config.Config;
import config.CustomerBaseConfig;
import config.InstanceConfig;
import dao.Dao;
import dao.Logging;
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
import java.util.Arrays;
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
            Logging.logger.info("## DNN - VF");


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


                                                                        //String fileName = String.format
                                                                        // ("v=%04d_tw=%04d_h=%06d_req=%04d",
                                                                        // initialFleet, timeWindow, timeHorizon,
                                                                        // maxRequestsIteration);


                                                                        // Update global class configuration to run
                                                                        // current test case
                                                                        Config.getInstance().updateQosDic(customerBaseSettings.qosDic);
                                                                        Config.getInstance().setShortestPathAlgorithm(spMethod);

                                                                        Rebalance rebalancingSettings = new Rebalance(rebalanceStrategy);
                                                                        Matching matchingSettings = new Matching(customerBaseSettings, contractDuration, rebalancingSettings, isAllowedToHire && matchingMethod instanceof MatchingOptimalServiceLevelAndHire, isAllowedToDisplaceRequests, matchingMethod);

                                                                        // Save data from matching methods in the same file
                                                                        String fileName = Solution.getTestCaseName(instanceSettings.getInstanceName(), null, instanceSettings.getMaxTimeToReachRegionCenter(), initialFleet, maxRequestsIteration, percentageRequestsIteration, vehicleMaxCapacity, timeWindow, timeHorizon, contractDuration, matchingSettings.isAllowUserDisplacement(), isAllowedToHire, customerBaseSettings.serviceRateLabel, customerBaseSettings.customerSegmentationLabel, rebalanceStrategy, null);
                                                                        learningConfig.setModelInstanceLabel(fileName);

//                                                                        while (learningConfig.hasNext()){
//                                                                            Logging.logger.info(learningConfig.next().getLabel());
//                                                                        }

                                                                        if (matchingMethod instanceof MatchingSimple) {
                                                                            train(matchingMethod, instanceSettings, timeHorizon, maxRequestsIteration, percentageRequestsIteration, timeWindow, vehicleMaxCapacity, initialFleet, isAllowedToHire, contractDuration, customerBaseSettings, learningConfig, rebalancingSettings, matchingSettings, fileName);
                                                                            // Disable learning
                                                                            ((MatchingSimple) matchingMethod).configureLearning(null);
                                                                        }

                                                                        test(instanceSettings, timeHorizon, maxRequestsIteration, percentageRequestsIteration, timeWindow, vehicleMaxCapacity, initialFleet, isAllowedToHire, contractDuration, customerBaseSettings, learningConfig, rebalancingSettings, matchingSettings, fileName);

                                                                        // Reset QOS class
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
            Logging.logger.info("Error! Cannot read " + configFilePath);
            e.printStackTrace();
        }
    }

    private static void train(RideMatchingStrategy matchingMethod, InstanceConfig instanceSettings, InstanceConfig.TimeConfig timeHorizon, int maxRequestsIteration, double percentageRequestsIteration, int timeWindow, int vehicleMaxCapacity, int initialFleet, boolean isAllowedToHire, int contractDuration, CustomerBaseConfig customerBaseSettings, InstanceConfig.LearningConfig learningConfig, Rebalance rebalancingSettings, Matching matchingSettings, String fileName) {

        while (learningConfig.hasNext()) {



            InstanceConfig.LearningConfig.LearningSettings learningSettings = learningConfig.next();
            ((MatchingSimple) matchingMethod).configureLearning(learningSettings);

            String modelFilePath = learningSettings.getModelFilePath(fileName);
            String msg1 = Dao.getInstance().getServer().loadModelAt(modelFilePath);
            Logging.logger.info(msg1);
            String experiencesFolder = learningSettings.getExperiencesFolder();
            String msg2 = Dao.getInstance().getServer().createExperiencesFolderAt(experiencesFolder);
            Logging.logger.info(msg2);

            Logging.logger.info("Learning settings: {}", learningSettings);
            int iEpisode = 0;
            for (int iSample = 0; iSample < learningSettings.requestSamples; iSample++) {

                // Loop throughout the training data to diversify experiences
                int i = 0;
                for (Date episodeStartDatetime : learningConfig.episodeStartDatetimeList) {

                    Logging.logger.info("{}", String.format("###### Episode %5d\n",iEpisode+1));
                    Dao.getInstance().setRecords(learningConfig.arrayFilepathTrainingData[i]);
                    i++;
                    // Users are sampled randomly from batch
                    // Vehicles start at random  positions
                    Random randomSeed = new Random(iSample);

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

                    if (simulation.instanceAlreadyProcessed()) {
                        Logging.logger.info(simulation.getSol().getOutputFile() + " " + "already exists.");
                    } else {
                        Logging.logger.info("{}", String.format("# Processing instance \"%s\"...", simulation.getSol().getOutputFile()));


                        simulation.run();

                        String headerStates = String.join(";", Vehicle.STATES);
                        String states = String.join(";", Arrays.stream(Vehicle.STATES).map(s -> simulation.getSol().statusVehicles.get(s).toString()).toList());

                        // Saving episode
                        String headers = "type;config;method;episode;" + "earliest;n_vehicles;n_requests;n_finished;service_rate;max_rounds;round_count;total_delay;distance_empty;distance_loaded;runtime_sec;" + headerStates;
                        String episodeInfo = simulation.getSummary(iSample);
                        Logging.logger.info(episodeInfo);
                        HelperIO.saveDataWithHeaders(fileName + ".csv", "train;" + learningSettings.getLabel() + ";" + episodeInfo + ";" + states, headers, true);
                        if (iEpisode % 100 == 0) {
                            Logging.logger.info("Saving model...");
                            String msg = Dao.getInstance().getServer().saveModelAt(modelFilePath);
                            Logging.logger.info("Model saved at " + msg);
                        }
                    }

                    // Reset classes for next iteration
                    Logging.logger.info("Resetting dao");
                    Dao.getInstance().resetRecords();

                    Logging.logger.info("Resetting user");
                    User.reset();

                    Logging.logger.info("Resetting vehicle");
                    Vehicle.reset();

                    Logging.logger.info("Resetting node");
                    Node.reset();

                    Logging.logger.info("Resetting middle node");
                    NodeMiddle.reset();

                    Logging.logger.info("Resetting visit");
                    Visit.reset();

                    Logging.logger.info("Resetting simulation");
                    Simulation.reset();
                    Solution.reset();
                    iEpisode++;
                    Logging.logger.info("Finished resetting.");
                }

            }
        }
    }

    private static void test(InstanceConfig instanceSettings, InstanceConfig.TimeConfig timeHorizon, int maxRequestsIteration, double percentageRequestsIteration, int timeWindow, int vehicleMaxCapacity, int initialFleet, boolean isAllowedToHire, int contractDuration, CustomerBaseConfig customerBaseSettings, InstanceConfig.LearningConfig learningConfig, Rebalance rebalancingSettings, Matching matchingSettings, String fileName) {


        for (int iEpisode = 0; iEpisode < 10; iEpisode++) {
            //Dao.getInstance().setRandomSeed(new
            // Random(iEpisode));
            // Loop throughout the training data
            // to diversify experiences
            int i = 0;
            for (Date episodeStartDatetime : learningConfig.episodeStartDatetimeListTest) {
                Dao.getInstance().setRecords(learningConfig.arrayFilepathTestingData[i]);
                i++;

                // Users are sampled randomly
                // from batch
                // Vehicles start at random
                // positions
                Random randomSeed = new Random(iEpisode);

                //TODO instance objects
                // generating solution objects
                // Create FCFS simulation
                Simulation simulation = new SimulationFCFS(instanceSettings.getInstanceName(), episodeStartDatetime, instanceSettings.getMaxTimeToReachRegionCenter(), initialFleet, vehicleMaxCapacity, maxRequestsIteration, percentageRequestsIteration, timeWindow, timeHorizon, contractDuration, isAllowedToHire, customerBaseSettings.serviceRateLabel, customerBaseSettings.customerSegmentationLabel, rebalancingSettings, matchingSettings, randomSeed);

                // Run simulation

                if (simulation.instanceAlreadyProcessed()) {
                    Logging.logger.info(simulation.getSol().getOutputFile() + " " + "already exists.");
                } else {
                    Logging.logger.info("{}", String.format("# Processing instance \"%s\"...", simulation.getSol().getOutputFile()));


                    simulation.run();


                    // Saving episode
                    String headerStates = String.join(";", Vehicle.STATES);
                    String states = String.join(";", Arrays.stream(Vehicle.STATES).map(s -> simulation.getSol().statusVehicles.get(s).toString()).toList());

                    String headers = "type;config;method;episode;" + "earliest;n_vehicles;n_requests;n_finished;service_rate;max_rounds;round_count;total_delay;distance_empty;distance_loaded;runtime_sec;" + headerStates;
                    String episodeInfo = simulation.getSummary(iEpisode);
                    Logging.logger.info(episodeInfo);
                    HelperIO.saveDataWithHeaders(fileName + ".csv", "test;-;" + episodeInfo + ";" + states, headers, true);

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
}
