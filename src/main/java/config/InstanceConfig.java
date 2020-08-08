package config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import dao.FileUtil;
import simulation.matching.*;
import simulation.rebalancing.Rebalance;
import simulation.rebalancing.RebalanceHeuristic;
import simulation.rebalancing.RebalanceOptimal;
import simulation.rebalancing.RebalanceStrategy;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.*;

public class InstanceConfig {
    // Files contain snapshots of each round
    public static final String ROUND_TRACK_FOLDER = "round_track";
    //Files contain how each user end up being serviced
    public static final String REQUEST_TRACK_FOLDER = "request_track";
    //Files contain how each user end up being serviced
    public static final String GEOJSON_TRACK_FOLDER = "geojson_track";
    // Singleton
    private static InstanceConfig instance;
    private ArrayList<Date> earliestTimeArray;
    private Date firstDate;
    private String instanceFilePath; // File path of the instance
    private List<CustomerBaseConfig> customerBaseSettingsArray;
    private List<RideMatchingStrategy> matchingMethods;
    private boolean[] sortWaitingUsersByClassArray; // If true, sort users according class when matching
    private int[] timeWindowArray; // Time window of request collection bin
    private int[] timeHorizonArray; // Time horizon of experiment (0 t0 24h)
    private int[] maxRequestsIterationArray; // Max number of requests pooled in during an iteration
    private int[] initialFleetArray; // Initial size of fleet
    private int[] vehicleMaxCapacityArray; // Max capacity of vehicle
    private boolean[] allowRebalancingArray; // Each round has TW seconds
    private int[] contractDurationArray; // In rounds of tw_batch seconds
    private boolean[] allowVehicleHiringArray;
    private boolean[] allowRequestDisplacementArray;

    // QoS
    private HashMap<String, Map<String, Double>> serviceRateScenarioMap;
    private HashMap<String, Map<String, Double>> segmentationScenarioMap;
    private HashMap<String, Map<String, Integer>> serviceLevelMap;
    private Map<String, Integer> maxTimeHiringList = new HashMap<>();
    // Rebalancing configuration
    private List<RebalanceStrategy> rebalancingMethods;
    private boolean[] allowManyToOneTarget;
    private boolean[] reinsertTargets;
    private boolean[] clearTargetListEveryRound;
    private boolean[] useUrgentKey;
    // Info
    private Path instancesPath;
    private Path resultPath;
    private Path distancesPath;
    private Path durationsPath;
    private Path adjacencyMatrixPath;
    private Path networkNodeInfoPath;
    private Path requestsPath;
    private String instanceDescription;
    private String instanceName;
    private String roundTrackFolder;
    private String requestTrackFolder;
    private String geojsonTrackFolder;

    private InstanceConfig(String jsonFilePath) {

        this.instanceFilePath = jsonFilePath;

        Gson gson = new Gson();

        Path filePath = Paths.get(jsonFilePath);

        try {
            // Reading input settings
            String inputSettings = new String(Files.readAllBytes(filePath));

            Map jsonConfig = gson.fromJson(inputSettings, Map.class);
            System.out.printf("# Reading instance data from \"%s\"...%n", jsonFilePath);
            System.out.printf("# JSON data: %s...%n", jsonConfig);

            //Description
            this.instancesPath = Paths.get(jsonConfig.get("instances_folder").toString());
            this.resultPath = Paths.get(jsonConfig.get("result_folder").toString());
            this.distancesPath = Paths.get(jsonConfig.get("distances_file").toString());
            this.durationsPath = Paths.get(jsonConfig.get("durations_file").toString());
            this.adjacencyMatrixPath = Paths.get(jsonConfig.get("adjacency_matrix_file").toString());
            this.networkNodeInfoPath = Paths.get(jsonConfig.get("network_node_info_file").toString());
            this.requestsPath = Paths.get(jsonConfig.get("requests_file").toString());
            this.instanceDescription = jsonConfig.get("instance_description").toString();
            this.instanceName = jsonConfig.get("instance_name").toString();

            JsonObject scenarioConfig = gson.toJsonTree(jsonConfig
                    .get("scenario_config"))
                    .getAsJsonObject();

            // Time window of request collection bin
            this.timeWindowArray = gson.fromJson(scenarioConfig
                    .get("batch_duration")
                    .getAsJsonArray(), int[].class);

            // Time window of request collection bin
            this.timeHorizonArray = gson.fromJson(scenarioConfig
                    .get("time_horizon")
                    .getAsJsonArray(), int[].class);

            String[] earliestTimes = gson.fromJson(scenarioConfig.get("earliest_time").getAsJsonArray(), String[].class);
            this.earliestTimeArray = new ArrayList<>();
            for (String earliestTime : earliestTimes) {
                try {
                    this.earliestTimeArray.add(Config.formatter_date_time.parse(earliestTime));
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }

            this.maxRequestsIterationArray = gson.fromJson(scenarioConfig.get("max_requests").getAsJsonArray(), int[].class);// Max number of requests pooled in during an iteration
            this.initialFleetArray = gson.fromJson(scenarioConfig.get("initial_fleet").getAsJsonArray(), int[].class);// Initial size of fleet
            this.vehicleMaxCapacityArray = gson.fromJson(scenarioConfig.get("max_capacity").getAsJsonArray(), int[].class);// Max capacity of vehicle
            this.allowRebalancingArray = gson.fromJson(scenarioConfig.get("rebalance").getAsJsonArray(), boolean[].class);// Each round has TW seconds
            this.contractDurationArray = gson.fromJson(scenarioConfig.get("contract_duration").getAsJsonArray(), int[].class); // In rounds of tw_batch seconds
            this.allowVehicleHiringArray = gson.fromJson(scenarioConfig.get("allow_vehicle_hiring").getAsJsonArray(), boolean[].class);
            this.allowRequestDisplacementArray = gson.fromJson(scenarioConfig.get("allow_request_displacement").getAsJsonArray(), boolean[].class);

            // Customer base settings
            Type segmentationScenarioType = new TypeToken<HashMap<String, HashMap<String, Double>>>() {
            }.getType();
            this.segmentationScenarioMap = gson.fromJson(scenarioConfig.get("customer_segmentation"), segmentationScenarioType);

            Type serviceLevelMapType = new TypeToken<HashMap<String, HashMap<String, Integer>>>() {
            }.getType();
            this.serviceLevelMap = gson.fromJson(scenarioConfig.get("service_level"), serviceLevelMapType);

            Type serviceRateMapType = new TypeToken<HashMap<String, HashMap<String, Double>>>() {
            }.getType();
            this.serviceRateScenarioMap = gson.fromJson(scenarioConfig.get("service_rate"), serviceRateMapType);

            this.maxTimeHiringList = new HashMap<>();
            for (Map.Entry<String, Map<String, Integer>> e : serviceLevelMap.entrySet()) {
                for (int tw : this.timeWindowArray) {
                    this.maxTimeHiringList.put(e.getKey(), (e.getValue().get("pk_delay_target") - tw));
                }
            }

            System.out.printf("# Max. time to reach classes (sec): %s%n", this.maxTimeHiringList);

            this.customerBaseSettingsArray = new ArrayList<>();
            // All service rate
            for (Map.Entry<String, Map<String, Double>> serviceRateScenario : serviceRateScenarioMap.entrySet()) {

                // S1, S2, S3
                String serviceRateScenarioLabel = serviceRateScenario.getKey();

                for (Map.Entry<String, Map<String, Double>> segmentationScenario : segmentationScenarioMap.entrySet()) {

                    // AA, BB, CC, A, B, C
                    String segmentationScenarioLabel = segmentationScenario.getKey();

                    Map<String, Qos> qosDic = new HashMap<>();
                    int qosCode = 0;
                    // Fixed service levels - A (180, 180), B(300, 600), C(600, 900)
                    for (Map.Entry<String, Map<String, Integer>> serviceLevel : serviceLevelMap.entrySet()) {

                        /* Creating QoS class
                         *  - service level class label (A, B, or C)
                         *  - pickup delay
                         *  - trip delay delay
                         *  - service rate (varies according to scenario)
                         *  - customer segmentation (varies according to scenario)
                         */

                        // Setup QoS class
                        Qos qos = new Qos(serviceLevel.getKey(),
                                qosCode++,
                                serviceRateScenario.getKey(),
                                segmentationScenarioLabel,
                                serviceLevel.getValue().get("priority"),
                                serviceLevel.getValue().get("pk_delay"),
                                serviceLevel.getValue().get("pk_delay_target"),
                                serviceLevel.getValue().get("trip_delay"),
                                serviceRateScenario.getValue().get(serviceLevel.getKey()),
                                segmentationScenario.getValue().get(serviceLevel.getKey()),
                                (serviceLevel.getValue().get("sharing_preference") == Qos.ALLOWED_SHARING));

                        // Update global class configuration to run current test case
                        qosDic.put(serviceLevel.getKey(), qos);
                    }
                    this.customerBaseSettingsArray.add(new CustomerBaseConfig(serviceRateScenarioLabel, segmentationScenarioLabel, qosDic));
                }
            }

            //Matching configuration
            this.matchingMethods = new ArrayList<>();
            JsonArray matchingConfig = gson.toJsonTree(jsonConfig.get("matching_config")).getAsJsonArray();
            //this.sortWaitingUsersByClassArray = gson.fromJson(matchingConfig.get("sort_waiting_users_by_class").getAsJsonArray(), boolean[].class);
            //JsonArray matchingMethods = gson.toJsonTree(jsonConfig.get("method")).getAsJsonArray();
            for (JsonElement matchingMethod : matchingConfig) {

                JsonObject element = gson.fromJson(matchingMethod, JsonObject.class);
                String name = gson.fromJson(element.get("name"), String.class);
                System.out.println("Processing " + name);

                if (Matching.METHOD_OPTIMAL_ENFORCE_SL_HIRE.equals(name)) {

                    MatchingOptimalServiceLevelAndHire method = readMatchingOptimalServiceLevelAndHireParams(gson, element);
                    this.matchingMethods.add(method);

                } else if (Matching.METHOD_OPTIMAL_ENFORCE_SL.equals(name)) {

                    MatchingOptimalServiceLevel method = readMatchingOptimalServiceLevelParams(gson, element);
                    this.matchingMethods.add(method);

                } else if (Matching.METHOD_OPTIMAL.equals(name)) {

                    MatchingOptimal method = readMatchingOptimalParams(gson, element);
                    this.matchingMethods.add(method);

                } else if (Matching.METHOD_FCFS.equals(name)) {

                    MatchingFCFS method = readMatchingFCFSParams(gson, element);
                    this.matchingMethods.add(method);

                } else if (Matching.METHOD_GREEDY.equals(name)) {
                    MatchingGreedy method = readMatchingGreedyParams(gson, element);
                    this.matchingMethods.add(method);
                } else {
                    System.out.println("NO METHOD");
                }
            }

            this.rebalancingMethods = new ArrayList<>();
            JsonArray rebalancingConfigurations = gson.toJsonTree(jsonConfig.get("rebalancing_config")).getAsJsonArray();
            for (JsonElement rebalanceStrategy : rebalancingConfigurations) {

                JsonObject element = gson.fromJson(rebalanceStrategy, JsonObject.class);
                String name = gson.fromJson(element.get("name"), String.class);
                System.out.println("Rebalancing " + name);
                if (Rebalance.METHOD_OPTIMAL.equals(name)) {

                    RebalanceOptimal method = new RebalanceOptimal();
                    this.rebalancingMethods.add(method);

                } else if (Rebalance.METHOD_HEURISTIC.equals(name)) {

                    // Rebalancing configuration
                    JsonObject rebalancingConfig = gson.fromJson(rebalanceStrategy, JsonObject.class);
                    boolean[] allowManyToOneTarget = gson.fromJson(rebalancingConfig.get("allow_many_to_one").getAsJsonArray(), boolean[].class);// Each round has TW seconds
                    boolean[] reinsertTargets = gson.fromJson(rebalancingConfig.get("reinsert_targets").getAsJsonArray(), boolean[].class);// Each round has TW seconds
                    boolean[] clearTargetListEveryRound = gson.fromJson(rebalancingConfig.get("clear_target_list_every_round").getAsJsonArray(), boolean[].class);// Each round has TW seconds
                    boolean[] useUrgentKey = gson.fromJson(rebalancingConfig.get("allow_urgent_relocation").getAsJsonArray(), boolean[].class);// Each round has TW seconds

                    for (boolean n1 : allowManyToOneTarget) {
                        for (boolean reinsert : reinsertTargets) {
                            for (boolean clear : clearTargetListEveryRound) {
                                for (boolean useUrg : useUrgentKey) {
                                    RebalanceStrategy method = new RebalanceHeuristic(n1, reinsert, clear, useUrg);
                                    rebalancingMethods.add(method);
                                }
                            }
                        }
                    }
                } else {
                    // No rebalancing
                    rebalancingMethods.add(null);
                }
            }
            //Creating directories for results
            this.roundTrackFolder = String.format("%s/%s", this.instancesPath, ROUND_TRACK_FOLDER);
            this.requestTrackFolder = String.format("%s/%s", this.instancesPath, REQUEST_TRACK_FOLDER);
            this.geojsonTrackFolder = String.format("%s/%s", this.instancesPath, GEOJSON_TRACK_FOLDER);
            FileUtil.createDir(roundTrackFolder);
            FileUtil.createDir(requestTrackFolder);
            FileUtil.createDir(geojsonTrackFolder);

        } catch (IOException e) {
            e.printStackTrace();
        }

        //Map jsonObject = (Map) gson.fromJson(data, Object.class);
    }

    public static void main(String[] a) {
        String s = "C:\\Users\\LocalAdmin\\IdeaProjects\\slevels\\src\\main\\resources\\instance_settings_test_rebalancing.json";
        InstanceConfig instanceConfig = new InstanceConfig(s);
        System.out.println(instanceConfig);
        System.out.println(instanceConfig.customerBaseSettingsArray);
        System.out.println(instanceConfig.rebalancingMethods);
    }

    public static InstanceConfig getInstance() {
        return instance;
    }

    public static InstanceConfig getInstance(String source) {

        if (instance == null) {
            instance = new InstanceConfig(source);
        }

        return instance;

    }

    private MatchingOptimal readMatchingOptimalParams(Gson gson, JsonObject element) {
        int maxEdgesRV = gson.fromJson(element.get("max_edges_rv"), int.class);
        int maxEdgesRR = gson.fromJson(element.get("max_edges_rr"), int.class);
        int maxVehicleCapacityRTV = gson.fromJson(element.get("rtv_max_vehicle_capacity"), int.class);
        double timeoutVehicle = gson.fromJson(element.get("rtv_vehicle_timeout"), double.class);
        double timeLimit = gson.fromJson(element.get("mip_time_limit"), double.class);
        double mipGap = gson.fromJson(element.get("mip_gap"), double.class);
        int rejectionPenalty = gson.fromJson(element.get("rejection_penalty"), int.class);
        String[] objectives = gson.fromJson(element.get("objectives"), String[].class);
        return new MatchingOptimal(maxVehicleCapacityRTV, timeLimit, timeoutVehicle, mipGap, maxEdgesRV, maxEdgesRR, rejectionPenalty, objectives);
    }

    private MatchingGreedy readMatchingGreedyParams(Gson gson, JsonObject element) {
        int maxEdgesRV = gson.fromJson(element.get("max_edges_rv"), int.class);
        int maxEdgesRR = gson.fromJson(element.get("max_edges_rr"), int.class);
        int maxVehicleCapacityRTV = gson.fromJson(element.get("rtv_max_vehicle_capacity"), int.class);
        double timeoutVehicle = gson.fromJson(element.get("rtv_vehicle_timeout"), double.class);
        double timeLimit = gson.fromJson(element.get("mip_time_limit"), double.class);
        double mipGap = gson.fromJson(element.get("mip_gap"), double.class);
        return new MatchingGreedy(maxVehicleCapacityRTV, timeLimit, timeoutVehicle, mipGap, maxEdgesRV, maxEdgesRR);
    }

    private MatchingFCFS readMatchingFCFSParams(Gson gson, JsonObject element) {
        int maxPermutationsFCFS = gson.fromJson(element.get("max_permutations"), int.class);
        boolean allPermutations = gson.fromJson(element.get("all_permutations"), boolean.class);
        boolean stopAtFirstBest = gson.fromJson(element.get("stop_at_first_best"), boolean.class);
        boolean checkInParallel = gson.fromJson(element.get("check_in_parallel"), boolean.class);
        return new MatchingFCFS(maxPermutationsFCFS, allPermutations, stopAtFirstBest, checkInParallel);
    }

    private MatchingOptimalServiceLevel readMatchingOptimalServiceLevelParams(Gson gson, JsonObject element) {
        int maxVehicleCapacityRTV = gson.fromJson(element.get("rtv_max_vehicle_capacity"), int.class);
        double timeoutVehicleRTV = gson.fromJson(element.get("rtv_vehicle_timeout"), double.class);
        int maxEdgesRV = gson.fromJson(element.get("max_edges_rv"), int.class);
        int maxEdgesRR = gson.fromJson(element.get("max_edges_rr"), int.class);
        double timeLimit = gson.fromJson(element.get("mip_time_limit"), double.class);
        double mipGap = gson.fromJson(element.get("mip_gap"), double.class);

        // SERVICE LEVEL PENALTIES
        int rejectionPenalty = gson.fromJson(element.get("rejection_penalty"), int.class);
        int badServicePenalty = gson.fromJson(element.get("bad_service_penalty"), int.class);
        String[] objectives = gson.fromJson(element.get("objectives"), String[].class);
        return new MatchingOptimalServiceLevel(maxVehicleCapacityRTV, badServicePenalty, timeLimit, timeoutVehicleRTV, mipGap, maxEdgesRV, maxEdgesRR, rejectionPenalty, objectives);
    }

    private MatchingOptimalServiceLevelAndHire readMatchingOptimalServiceLevelAndHireParams(Gson gson, JsonObject element) {
        int maxVehicleCapacityRTV = gson.fromJson(element.get("rtv_max_vehicle_capacity"), int.class);
        double timeoutVehicleRTV = gson.fromJson(element.get("rtv_vehicle_timeout"), double.class);
        int maxEdgesRV = gson.fromJson(element.get("max_edges_rv"), int.class);
        int maxEdgesRR = gson.fromJson(element.get("max_edges_rr"), int.class);
        double timeLimit = gson.fromJson(element.get("mip_time_limit"), double.class);
        double mipGap = gson.fromJson(element.get("mip_gap"), double.class);

        // SERVICE LEVEL PENALTIES
        int rejectionPenalty = gson.fromJson(element.get("rejection_penalty"), int.class);
        int badServicePenalty = gson.fromJson(element.get("bad_service_penalty"), int.class);
        int hiringPenalty = gson.fromJson(element.get("hiring_penalty"), int.class);
        boolean allowHiring = gson.fromJson(element.get("allow_hiring"), boolean.class);
        String[] objectives = gson.fromJson(element.get("objectives"), String[].class);
        return new MatchingOptimalServiceLevelAndHire(maxVehicleCapacityRTV, badServicePenalty, hiringPenalty, timeLimit, timeoutVehicleRTV, mipGap, maxEdgesRV, maxEdgesRR, rejectionPenalty, allowHiring, objectives);
    }

    public boolean[] getSortWaitingUsersByClassArray() {
        return sortWaitingUsersByClassArray;
    }

    public void setSortWaitingUsersByClassArray(boolean[] sortWaitingUsersByClassArray) {
        this.sortWaitingUsersByClassArray = sortWaitingUsersByClassArray;
    }

    public Path getDistancesPath() {
        return distancesPath;
    }

    public Path getAdjacencyMatrixPath() {
        return adjacencyMatrixPath;
    }

    public Path getNetworkNodeInfoPath() {
        return networkNodeInfoPath;
    }

    public Path getRequestsPath() {
        return requestsPath;
    }

    @Override
    public String toString() {
        return "################## Instance Config ###################################" +
                "\ntimeWindowArray=" + Arrays.toString(timeWindowArray) +
                "\ntimeHorizonArray=" + Arrays.toString(timeHorizonArray) +
                "\nmaxRequestsIterationArray=" + Arrays.toString(maxRequestsIterationArray) +
                "\ninitialFleetArray=" + Arrays.toString(initialFleetArray) +
                "\ninitialFleetArray=" + Arrays.toString(initialFleetArray) +
                "\nvehicleMaxCapacityArray=" + Arrays.toString(vehicleMaxCapacityArray) +
                "\nallowRebalancingArray=" + Arrays.toString(allowRebalancingArray) +
                "\ncontractDurationArray=" + Arrays.toString(contractDurationArray) +
                "\nallowVehicleHiringArray=" + Arrays.toString(allowVehicleHiringArray) +
                "\nallowManyToOneTarget=" + Arrays.toString(allowManyToOneTarget) +
                "\nreinsertTargets=" + Arrays.toString(reinsertTargets) +
                "\nserviceRateScenarioMap=" + serviceRateScenarioMap +
                "\nsegmentationScenarioMap=" + segmentationScenarioMap +
                "\nclearTargetListEveryRound=" + Arrays.toString(clearTargetListEveryRound) +
                "\nuseUrgentKey=" + Arrays.toString(useUrgentKey) +
                "\ninstancesPath=" + instancesPath +
                "\nresultPath=" + resultPath +
                "\nrequestsPath=" + requestsPath +
                "\ndistancesPath=" + distancesPath +
                "\ndurationsPath=" + durationsPath +
                "\nmaxTimeHiringList=" + maxTimeHiringList +
                "\ninstanceDescription='" + instanceDescription + '\'' +
                '}';
    }

    /**
     * @return Map of classes and max times
     */
    public Map<String, Integer> getMaxTimeHiringList() {
        return maxTimeHiringList;
    }

    public int[] getTimeWindowArray() {
        return timeWindowArray;
    }

    public int[] getTimeHorizonArray() {
        return timeHorizonArray;
    }

    public int[] getMaxRequestsIterationArray() {
        return maxRequestsIterationArray;
    }

    public int[] getInitialFleetArray() {
        return initialFleetArray;
    }

    public int[] getVehicleMaxCapacityArray() {
        return vehicleMaxCapacityArray;
    }

    public boolean[] getAllowRebalancingArray() {
        return allowRebalancingArray;
    }

    public int[] getContractDurationArray() {
        return contractDurationArray;
    }

    public boolean[] getAllowVehicleHiringArray() {
        return allowVehicleHiringArray;
    }

    public HashMap<String, Map<String, Double>> getServiceRateScenarioMap() {
        return serviceRateScenarioMap;
    }

    public HashMap<String, Map<String, Double>> getSegmentationScenarioMap() {
        return segmentationScenarioMap;
    }

    public HashMap<String, Map<String, Integer>> getServiceLevelMap() {
        return serviceLevelMap;
    }

    public List<CustomerBaseConfig> getCustomerBaseSettingsArray() {
        return customerBaseSettingsArray;
    }

    public boolean[] getAllowManyToOneTarget() {
        return allowManyToOneTarget;
    }

    public boolean[] getReinsertTargets() {
        return reinsertTargets;
    }

    public boolean[] getClearTargetListEveryRound() {
        return clearTargetListEveryRound;
    }

    public boolean[] getUseUrgentKey() {
        return useUrgentKey;
    }

    public Path getInstancesPath() {
        return instancesPath;
    }

    public Path getResultPath() {
        return resultPath;
    }

    public String getInstanceDescription() {
        return instanceDescription;
    }

    public String getRoundTrackFolder() {
        return roundTrackFolder;
    }

    public String getRequestTrackFolder() {
        return requestTrackFolder;
    }

    public String getGeojsonTrackFolder() {
        return geojsonTrackFolder;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public List<RebalanceStrategy> getRebalancingMethods() {
        return rebalancingMethods;
    }

    public Path getDurationsPath() {
        return this.durationsPath;
    }

    public List<RideMatchingStrategy> getMatchingMethods() {
        return matchingMethods;
    }

    public List<Date> getEarliestTimeArray() {
        return this.earliestTimeArray;
    }

    public boolean[] getAllowRequestDisplacementArray() {
        return this.allowRequestDisplacementArray;
    }
}


