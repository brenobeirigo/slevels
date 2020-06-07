package config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import dao.FileUtil;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private String instanceFilePath; // File path of the instance
    private boolean[] sortWaitingUsersByClassArray; // If true, sort users according class when matching
    private int[] timeWindowArray; // Time window of request collection bin
    private int[] timeHorizonArray; // Time horizon of experiment (0 t0 24h)
    private int[] maxRequestsIterationArray; // Max number of requests pooled in during an iteration
    private int[] initialFleetArray; // Initial size of fleet
    private int[] vehicleMaxCapacityArray; // Max capacity of vehicle
    private boolean[] allowRebalancingArray; // Each round has TW seconds
    private int[] contractDurationArray; // In rounds of tw_batch seconds
    private boolean[] allowVehicleHiringArray;
    private boolean[] allowServiceDeteriorationArray;

    // QoS
    private HashMap<String, Map<String, Double>> serviceRateScenarioMap;
    private HashMap<String, Map<String, Double>> segmentationScenarioMap;
    private HashMap<String, Map<String, Integer>> serviceLevelMap;
    private List<CustomerBaseConfig> customerBaseSettingsArray = new ArrayList<>();
    private Map<String, Integer> maxTimeHiringList = new HashMap<>();

    // Rebalancing configuration
    private boolean[] allowManyToOneTarget = new boolean[]{false, true};
    private boolean[] reinsertTargets = new boolean[]{true, false};
    private boolean[] clearTargetListEveryRound = new boolean[]{true, false};
    private boolean[] useUrgentKey = new boolean[]{false, true};
    private List<Rebalance> listRebalanceSettings = new ArrayList<>();

    // Info
    private Path instancesPath;
    private Path resultPath;
    private Path distancesPath;
    private Path requestsPath;
    private String instanceDescription;
    private String instanceName;
    private String roundTrackFolder;
    private String requestTrackFolder;
    private String geojsonTrackFolder;

    public boolean[] getSortWaitingUsersByClassArray() {
        return sortWaitingUsersByClassArray;
    }

    public void setSortWaitingUsersByClassArray(boolean[] sortWaitingUsersByClassArray) {
        this.sortWaitingUsersByClassArray = sortWaitingUsersByClassArray;
    }

    private InstanceConfig(String jsonFilePath) {

        this.instanceFilePath = jsonFilePath;

        Gson gson = new Gson();

        Path filePath = Paths.get(jsonFilePath);

        try {
            // Reading input settings
            String inputSettings = new String(Files.readAllBytes(filePath));

            Map jsonConfig = gson.fromJson(inputSettings, Map.class);
            System.out.println("INSTANCE DATA (from json)");
            System.out.println(jsonConfig);

            //Description
            this.instancesPath = Paths.get(jsonConfig.get("instances_folder").toString());
            this.resultPath = Paths.get(jsonConfig.get("result_folder").toString());
            this.distancesPath = Paths.get(jsonConfig.get("distances_file").toString());
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


            this.maxRequestsIterationArray = gson.fromJson(scenarioConfig.get("max_requests").getAsJsonArray(), int[].class);// Max number of requests pooled in during an iteration
            this.initialFleetArray = gson.fromJson(scenarioConfig.get("initial_fleet").getAsJsonArray(), int[].class);// Initial size of fleet
            this.vehicleMaxCapacityArray = gson.fromJson(scenarioConfig.get("max_capacity").getAsJsonArray(), int[].class);// Max capacity of vehicle
            this.allowRebalancingArray = gson.fromJson(scenarioConfig.get("rebalance").getAsJsonArray(), boolean[].class);// Each round has TW seconds
            this.contractDurationArray = gson.fromJson(scenarioConfig.get("contract_duration").getAsJsonArray(), int[].class); // In rounds of tw_batch seconds
            this.allowServiceDeteriorationArray = gson.fromJson(scenarioConfig.get("allow_service_deterioration").getAsJsonArray(), boolean[].class);
            this.allowVehicleHiringArray = gson.fromJson(scenarioConfig.get("allow_vehicle_hiring").getAsJsonArray(), boolean[].class);


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
            this.maxTimeHiringList = new HashMap();
            for (Map.Entry<String, Map<String, Integer>> e :
                    serviceLevelMap.entrySet()) {
                for (int tw :
                        this.timeWindowArray) {
                    this.maxTimeHiringList.put(e.getKey(), (e.getValue().get("pk_delay") - tw));
                }
            }

            System.out.println("MAXTIME HIRING LIST");
            System.out.println(this.maxTimeHiringList);


            // All service rate
            for (Map.Entry<String, Map<String, Double>> serviceRateScenario : serviceRateScenarioMap.entrySet()) {

                // S1, S2, S3
                String serviceRateScenarioLabel = serviceRateScenario.getKey();

                for (Map.Entry<String, Map<String, Double>> segmentationScenario : segmentationScenarioMap.entrySet()) {

                    // AA, BB, CC, A, B, C
                    String segmentationScenarioLabel = segmentationScenario.getKey();

                    Map<String, Config.Qos> qosDic = new HashMap<>();

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
                        Config.Qos qos = new Config.Qos(serviceLevel.getKey(),
                                serviceRateScenario.getKey(),
                                segmentationScenarioLabel,
                                serviceLevel.getValue().get("pk_delay"),
                                serviceLevel.getValue().get("trip_delay"),
                                serviceRateScenario.getValue().get(serviceLevel.getKey()),
                                segmentationScenario.getValue().get(serviceLevel.getKey()),
                                (serviceLevel.getValue().get("sharing_preference") == Config.Qos.ALLOWED_SHARING));

                        // Update global class configuration to run current test case
                        qosDic.put(serviceLevel.getKey(), qos);
                    }
                    this.customerBaseSettingsArray.add(new CustomerBaseConfig(serviceRateScenarioLabel, segmentationScenarioLabel, qosDic));
                }
            }

            //Matching configuration
            JsonObject matchingConfig = gson.toJsonTree(jsonConfig.get("matching_config")).getAsJsonObject();
            this.sortWaitingUsersByClassArray = gson.fromJson(matchingConfig.get("sort_waiting_users_by_class").getAsJsonArray(), boolean[].class);

            // Rebalancing configuration
            JsonObject rebalancingConfig = gson.toJsonTree(jsonConfig.get("rebalancing_config")).getAsJsonObject();
            this.allowManyToOneTarget = gson.fromJson(rebalancingConfig.get("allow_many_to_one").getAsJsonArray(), boolean[].class);// Each round has TW seconds
            this.reinsertTargets = gson.fromJson(rebalancingConfig.get("reinsert_targets").getAsJsonArray(), boolean[].class);// Each round has TW seconds
            this.clearTargetListEveryRound = gson.fromJson(rebalancingConfig.get("clear_target_list_every_round").getAsJsonArray(), boolean[].class);// Each round has TW seconds
            this.useUrgentKey = gson.fromJson(rebalancingConfig.get("allow_urgent_relocation").getAsJsonArray(), boolean[].class);// Each round has TW seconds

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

                            listRebalanceSettings.add(rebalanceUtil);
                        }
                    }
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
        System.out.println(instanceConfig.listRebalanceSettings);
    }

    public Path getDistancesPath() {
        return distancesPath;
    }

    public static InstanceConfig getInstance() {
        return instance;
    }

    public static InstanceConfig getInstance(String source) {
        if (instance == null){
             instance = new InstanceConfig(source);
        }
        return instance;
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
                "\nallowServiceDeteriorationArray=" + Arrays.toString(allowServiceDeteriorationArray) +
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

    public boolean[] getAllowServiceDeteriorationArray() {
        return allowServiceDeteriorationArray;
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

    public List<Rebalance> getListRebalanceSettings() {
        return listRebalanceSettings;
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
}


