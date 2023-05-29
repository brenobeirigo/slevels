import config.Config;
import config.InstanceConfig;
import dao.Dao;
import dao.Logging;
import experiment.InstanceOld;
import experiment.InstanceUtil;
import model.learn.LearningConfig;
import model.learn.LearningSettings;
import model.network.NetworkLoaded;
import simulation.Environment;
import simulation.Simulation;
import simulation.matching.MatchingSimple;

import java.util.Date;
import java.util.List;

/**
 * Execute all instances from test cases in json file
 */
public class MainOld {

    public static void main(String[] args) {

        String configFilePath = args[0];
        String trainingConfig = args[1];

        InstanceConfig instanceSettings = Config.createInstanceFrom(configFilePath);
        List<InstanceOld> instances = InstanceUtil.getInstanceListFrom(instanceSettings);

        if (instances != null) {

            Logging.logger.info("## DNN - VF");

            NetworkLoaded transportNetwork = new NetworkLoaded(
                    InstanceConfig.getInstance().getDistancesPath().toString(),
                    InstanceConfig.getInstance().getAdjacencyMatrixPath().toString(),
                    InstanceConfig.getInstance().getZoneDataPath().toString(),
                    InstanceConfig.getInstance().getNetworkNodeInfoPath().toString(), Config.getInstance().getSpAlgorithm(),
                    InstanceConfig.getInstance().getSpeedKmHour());

            for (InstanceOld instance : instances) {
                Config.getInstance().updateQosDic(instance.customerBaseSettings().qosDic);
                Config.getInstance().setShortestPathAlgorithm(instance.spMethod());

                Environment environment = new Environment(
                        instance.fleetConfig(),
                        instance.demandConfig(),
                        transportNetwork,
                        instance.timeConfig(),
                        instance.routingConfig());

                // Save data from matching methods in the same file
                String fileName = instance.getLabel();
                instance.learningConfig().setModelInstanceLabel(fileName);


                if (instance.matchingMethod() instanceof MatchingSimple) {
                    train(environment, instance, fileName);
                    // Disable learning
                    ((MatchingSimple) instance.matchingMethod()).configureLearning(null);
                }

                test(environment, instance.learningConfig(), fileName);

                // Reset QOS class
                Config.reset();
            }
        }
    }


    private static void train(Environment environment, InstanceOld instance, String fileName) {


        while (instance.learningConfig().hasNext()) {

            LearningSettings learningSettings = instance.learningConfig().next();
            ((MatchingSimple) environment.matching.getRideMatchingStrategy()).configureLearning(learningSettings);

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
                for (Date episodeStartDatetime : instance.learningConfig().episodeStartDatetimeList) {

                    Logging.logger.info("{}", String.format("###### Episode %5d\n", iEpisode + 1));
                    Dao.getInstance().setRecords(instance.learningConfig().arrayFilepathTrainingData[i]);
                    i++;
                    // Users are sampled randomly from batch
                    // Vehicles start at random  positions

//
                    if (environment.instanceAlreadyProcessed()) {
//                        Logging.logger.info(environment.getSol().getOutputFile() + " " + "already exists.");
//                    } else {
//                        Logging.logger.info("{}", String.format("# Processing instance \"%s\"...", environment.getSol().getOutputFile()));

                        Simulation simulation = new Simulation(environment, iEpisode);
                        simulation.run();

//                        String headerStates = String.join(";", Vehicle.STATES);
//                        String states = String.join(";", Arrays.stream(Vehicle.STATES).map(s -> environment.getSol().statusVehicles.get(s).toString()).toList());

//                        // Saving episode
//                        String headers = "type;config;method;episode;" + "earliest;n_vehicles;n_requests;n_finished;service_rate;max_rounds;round_count;total_delay;distance_empty;distance_loaded;runtime_sec;" + headerStates;
//                        String episodeInfo = simulation.getSummaryEpisodeInfo(iSample);
//                        Logging.logger.info(episodeInfo);
//                        HelperIO.saveDataWithHeaders(fileName + ".csv", "train;" + learningSettings.getLabel() + ";" + episodeInfo + ";" + states, headers, true);
//                        if (iEpisode % 100 == 0) {
//                            Logging.logger.info("Saving model...");
//                            String msg = Dao.getInstance().getServer().saveModelAt(modelFilePath);
//                            Logging.logger.info("Model saved at " + msg);
//                        }
                    }

                    Environment.reset();
                    iEpisode++;
                    Logging.logger.info("Finished resetting.");
                }

            }
        }
    }

    private static void test(Environment environment, LearningConfig learningConfig, String fileName) {


        for (int iEpisode = 0; iEpisode < 10; iEpisode++) {


            //Dao.getInstance().setRandomSeed(new
            // Random(iEpisode));
            // Loop throughout the training data
            // to diversify experiences
            int i = 0;
            for (Date episodeStartDatetime : learningConfig.episodeStartDatetimeListTest) {
                Dao.getInstance().setRecords(learningConfig.arrayFilepathTestingData[i]);
                i++;

                // Run environment

//                if (environment.instanceAlreadyProcessed()) {
//                    Logging.logger.info(environment.getSol().getOutputFile() + " " + "already exists.");
//                } else {
//                    Logging.logger.info("# Processing instance '{}'...", environment.getSol().getOutputFile());
//
//                    // Users are sampled randomly from batch and vehicles start at randomly at positions
//                    Simulation simulation = new Simulation(environment, iEpisode);
//                    simulation.run();
//
//
//                    // Saving episode
//                    String headerStates = String.join(";", Vehicle.STATES);
//                    String states = String.join(";", Arrays.stream(Vehicle.STATES).map(s -> environment.getSol().statusVehicles.get(s).toString()).toList());
//
//                    String headers = "type;config;method;episode;" + "earliest;n_vehicles;n_requests;n_finished;service_rate;max_rounds;round_count;total_delay;distance_empty;distance_loaded;runtime_sec;" + headerStates;
//                    String episodeInfo = simulation.getSummaryEpisodeInfo(iEpisode);
//                    Logging.logger.info(episodeInfo);
//                    HelperIO.saveDataWithHeaders(fileName + ".csv", "test;-;" + episodeInfo + ";" + states, headers, true);
//
//                }
                // Reset classes for next iteration
                Environment.reset();
            }
        }
    }
}
