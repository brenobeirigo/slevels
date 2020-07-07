package simulation;

import config.Config;
import config.InstanceConfig;
import config.Qos;
import config.RebalanceStrategy;
import dao.Dao;
import dao.FileUtil;
import helper.HelperIO;
import model.User;
import model.Vehicle;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import visualization.GeoJsonUtil;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.util.Comparator.comparingInt;

public class Solution {

    // Execution time headers
    public static final String TIME_UPDATE_FLEET_STATUS = "time_update_fleet_status_ms";
    public static final String TIME_REBALANCING_FLEET = "time_vehicle_rebalancing_ms";
    public static final String TIME_UPDATE_DEMAND = "time_ride_matching_ms";
    public static final String TIME_CREATE_RV = "time_create_rv_graph";
    public static final String TIME_CREATE_RTV = "time_create_rtv_graph";
    public static final String TIME_MATCHING = "time_matching";
    public static final String[] TIME_HEADERS = new String[]{TIME_UPDATE_DEMAND, TIME_UPDATE_FLEET_STATUS, TIME_REBALANCING_FLEET};

    private BufferedWriter writer;
    /* Instance */
    private int nOfVehicles;
    private int maxNumberOfTrips;
    private int vehicleCapacity;
    private int timeHorizon;
    private int totalHorizon;
    private String methodName;
    private String serviceRate;
    private String customerSegmentation;
    private String testCaseName;
    private boolean allowVehicleCreation;
    private boolean allowDelayExtension;
    private Map<String, Map<String, Integer>> sLevelsClass;
    /* Rebalancing */
    private boolean allowRebalancing;

    /* Deactivating */
    private int deactivationFactor;
    private int maxDelayExtensionsBeforeHiring;


    /* CSV output
    departureVehicleCurrent;seatCount;activeVehicles;enrouteCount;deniedRequests.size();finishedRequests.size();
    vehicleOccupancy;fleetMakeup;pkDelay;totalDelay;runTimes; */
    private ArrayList<List<String>> listRoundEntries;
    private List<String> listRoundHeaders;

    /* Output */
    private Path outputFile;
    private Path outputFileUsers;
    private Path outputGeoJson;

    public Solution(String methodName,
                    int nOfVehicles,
                    int maxNumberOfTrips,
                    int vehicleCapacity,
                    int timeHorizon,
                    int totalHorizon,
                    int deactivationFactor,
                    boolean allowVehicleCreation,
                    boolean allowDelayExtension) {

        this.methodName = methodName;
        this.nOfVehicles = nOfVehicles;
        this.maxNumberOfTrips = maxNumberOfTrips;
        this.vehicleCapacity = vehicleCapacity;
        this.timeHorizon = timeHorizon;
        this.totalHorizon = totalHorizon;
        this.deactivationFactor = deactivationFactor;
        this.allowVehicleCreation = allowVehicleCreation;
        this.allowDelayExtension = allowDelayExtension;
        this.listRoundEntries = new ArrayList<>();
        this.sLevelsClass = new HashMap<>();
        createHeader();
    }

    // Initialize solution
    public Solution(String methodName,
                    int nOfVehicles,
                    int maxNumberOfTrips,
                    int vehicleCapacity,
                    int timeHorizon,
                    int totalHorizon,
                    int contractDuration,
                    boolean allowVehicleHiring,
                    boolean allowServiceDeterioration,
                    String serviceRate,
                    String customerSegmentation,
                    RebalanceStrategy rebalanceStrategy,
                    RideMatchingStrategy matchingStrategy) {

        // Initialize solution
        this(methodName,
                nOfVehicles,
                maxNumberOfTrips,
                vehicleCapacity,
                timeHorizon,
                totalHorizon,
                contractDuration,
                allowVehicleHiring,
                allowServiceDeterioration);

        this.serviceRate = serviceRate;
        this.customerSegmentation = customerSegmentation;


        testCaseName = String.format(
                "IN-%s_BA-%d_ST-%d_MR-%d_IF-%d_MC-%d_CS-%s",
                methodName,
                timeHorizon,
                totalHorizon,
                maxNumberOfTrips,
                nOfVehicles,
                vehicleCapacity,
                customerSegmentation);
        testCaseName += (allowVehicleHiring ? "_CD-" + (contractDuration == Config.DURATION_SINGLE_RIDE ? 0 : contractDuration) : "");
        testCaseName += (allowServiceDeterioration ? "_SR-" + serviceRate : "");
        testCaseName += (allowVehicleHiring ? "_VH" : "");
        testCaseName += (allowServiceDeterioration ? "_SD" : "");
        testCaseName += rebalanceStrategy != null ? rebalanceStrategy : "_RE-NO";
        testCaseName += matchingStrategy;

        // File path
        this.outputFile = Paths.get(
                String.format("%s/%s.csv", InstanceConfig.getInstance().getRoundTrackFolder(),
                        testCaseName
                ));

        // File path
        this.outputGeoJson = Paths.get(
                String.format("%s/%s.geojson",
                        InstanceConfig.getInstance().getGeojsonTrackFolder(),
                        testCaseName
                ));

        // File path
        this.outputFileUsers = Paths.get(
                String.format("%s/%s.csv",
                        InstanceConfig.getInstance().getRequestTrackFolder(),
                        testCaseName
                ));
    }

    public static void reset() {
        return;
    }

    public String allPoints() {
        StringBuilder b = new StringBuilder();
        b.append("{\n" +
                "  \"type\": \"FeatureCollection\",\n" +
                "  \"features\": [");
        for (User u : User.mapOfUsers.values()) {
            b.append(u.getNodePk().toGeoJson());
            b.append(",");
            b.append(u.getNodeDp().toGeoJson());
            b.append(",");

        }
        b.append("]}");
        return b.toString();
    }

    public void createHeader() {

        listRoundHeaders = new ArrayList<>();
        listRoundHeaders.add("timestamp");
        listRoundHeaders.add("waiting");
        listRoundHeaders.add("finished");
        listRoundHeaders.add("denied");
        listRoundHeaders.add("n_requests");
        listRoundHeaders.add("seat_count");
        listRoundHeaders.add("picking_up_seats");
        listRoundHeaders.add("rebalancing_seats");
        listRoundHeaders.add("empty_seats");
        listRoundHeaders.add("total_capacity");
        listRoundHeaders.add("active_vehicles");
        listRoundHeaders.add("hired_vehicles");
        listRoundHeaders.add("deactivated_vehicles");
        listRoundHeaders.add("enroute_count");
        listRoundHeaders.add("pk_delay");
        listRoundHeaders.add("total_delay");
        listRoundHeaders.add("parked_vehicles");
        listRoundHeaders.add("origin_vehicles");
        listRoundHeaders.add("rebalancing");
        listRoundHeaders.add("stopped_rebalancing");
        listRoundHeaders.add("idle");
        listRoundHeaders.add("picking_up");

        for (int i = 1; i <= vehicleCapacity; i++) {
            listRoundHeaders.add("O" + i);
        }

        for (int i = 1; i <= vehicleCapacity; i++) {
            listRoundHeaders.add("V" + i);
        }

        listRoundHeaders.add("distance_traveled_cruising");
        listRoundHeaders.add("distance_traveled_loaded");
        listRoundHeaders.add("distance_traveled_rebalancing");
        for (String h : Solution.TIME_HEADERS) {
            listRoundHeaders.add(h);
        }

        for (String q :
                Config.getInstance().qosDic.keySet()) {
            listRoundHeaders.add(String.format("%s_pk", q));
            listRoundHeaders.add(String.format("%s_dp", q));
            listRoundHeaders.add(String.format("%s_count", q));
            listRoundHeaders.add(String.format("%s_unmet_slevels", q));
        }
    }

    public Path getOutputFile() {
        return outputFile;
    }

    public void saveRoundInfo() {

        System.out.println(">>>>>>> Round log: " + this.outputFile);

        try {
            writer = Files.newBufferedWriter(outputFile);

            String[] a = listRoundHeaders.toArray(new String[0]);

            CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(a).withCommentMarker('#'));

            // Printing comment in the beginning of case study with instance configuration
            for (Map.Entry<String, Qos> e : Config.getInstance().qosDic.entrySet()) {
                csvPrinter.printComment(e.getValue().toString());
            }

            for (List<String> e : this.listRoundEntries) {

                csvPrinter.printRecord(e);
                csvPrinter.flush();
            }

            csvPrinter.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Save a geojson route for every vehicle in vehicle list
     *
     * @param vehicleList
     */
    public void saveGeoJsonPerVehicle(List<Vehicle> vehicleList) {
        System.out.println("Saving geojson vehicle traces...");


        // Create a folder for test case
        String caseStudyPath = String.format("%s/%s",
                InstanceConfig.getInstance().getGeojsonTrackFolder(),
                this.testCaseName);
        FileUtil.createDir(caseStudyPath);


        for (Vehicle v : vehicleList) {

            // Create vehicle geojson name
            Path outputGeoJsonVehicle = Paths.get(
                    String.format("%s/%s.geojson",
                            caseStudyPath,
                            v.toString().trim()
                    ));
            try {
                writer = Files.newBufferedWriter(outputGeoJsonVehicle);
                String geojson = String.format("{\n" +
                        "    \"type\": \"FeatureCollection\",\n" +
                        "    \"features\": %s\n}", String.join(",\n", GeoJsonUtil.getJourneyComplete(v)));

                writer.write(geojson);
                writer.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public void saveUserInfo(Map<Integer, User> listOfServicedUsers) {

        System.out.println(">>>>>>> User service log: " + this.outputFileUsers);

        List<User> sortedUsersPk = new ArrayList<>(listOfServicedUsers.values());
        sortedUsersPk.sort(comparingInt(o -> o.getNodePk().getEarliest()));

        try {
            writer = Files.newBufferedWriter(outputFileUsers);
            String[] a = new String[]{"earliest", "id", "class", "delay_pk", "delay_in_vehicle", "delay_ride", "pk_time", "dp_time", "id_from", "id_to", "min_dist_sec", "service", "service_level"};

            CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(a).withCommentMarker('#'));

            for (Map.Entry<String, Qos> e : Config.getInstance().qosDic.entrySet()) {
                csvPrinter.printComment(e.getValue().toString());
            }
            for (User u : sortedUsersPk) {
                int minDistSec = Dao.getInstance().getDistSec(u.getNodePk(), u.getNodeDp());
                int inVehicleDelay = u.getNodeDp().getDelay() - u.getNodePk().getDelay();
                List<String> entry = new ArrayList<>();
                entry.add(Config.sec2Datetime(u.getNodePk().getEarliest()));
                entry.add(String.valueOf(u.getId()));
                entry.add(String.valueOf(u.getPerformanceClass()));
                entry.add(String.valueOf(u.getNodePk().getDelay()));
                entry.add(String.valueOf(inVehicleDelay));
                entry.add(String.valueOf(u.getNodeDp().getDelay()));
                entry.add(Config.sec2Datetime(u.getNodePk().getArrival()));
                entry.add(Config.sec2Datetime(u.getNodeDp().getArrival()));
                entry.add(String.valueOf(u.getNodePk().getNetworkId()));
                entry.add(String.valueOf(u.getNodeDp().getNetworkId()));
                entry.add(String.valueOf(minDistSec));
                entry.add(u.isRejected() ? "DENIED" : u.isServicedByDedicated() ? "FLEET" : "FREELANCE");
                entry.add(u.isFirstTier() ? "FIRST" : "SECOND");

                csvPrinter.printRecord(entry);
                csvPrinter.flush();
            }

            csvPrinter.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String calculateRoundStats(int currentTime,
                                      int vehicleSize,
                                      List<Vehicle> listVehicles,
                                      Set<Vehicle> setHiredVehicles,
                                      Set<Vehicle> setDeactivated,
                                      List<Vehicle> listHiredVehicles,
                                      List<User> waitingRequests,
                                      Set<User> finishedRequests,
                                      List<User> roundRejectedUsers,
                                      Set<User> deniedRequests,
                                      List<User> setOfRequests,
                                      Map<Integer, User> allRequests,
                                      Map<String, Long> runTimes,
                                      boolean saveRoundInfoCSV,
                                      boolean showRoundInfo) {

        if (saveRoundInfoCSV == false && showRoundInfo == false) {
            return null;
        }

        double pkDelay = 0;
        double totalDelay = 0;

        //The procedure below sweeps all customers so far...
        sLevelsClass.clear();
        for (String sqClass : Config.getInstance().qosDic.keySet()) {
            sLevelsClass.put(sqClass, new HashMap<>());
            sLevelsClass.get(sqClass).put("pk", 0);
            sLevelsClass.get(sqClass).put("dp", 0);
            sLevelsClass.get(sqClass).put("max", 0);
            sLevelsClass.get(sqClass).put("first", 0);
            sLevelsClass.get(sqClass).put("count", 0);
            sLevelsClass.get(sqClass).put("rejected", 0);
            sLevelsClass.get(sqClass).put("dedicated", 0);
            sLevelsClass.get(sqClass).put("private", 0);
            sLevelsClass.get(sqClass).put("unmet_slevels", 0);
        }
        // Sum delays of finished users
        for (User u : finishedRequests) {

            int delayPk = u.getNodePk().getDelay();
            int delayDp = u.getNodeDp().getDelay();
            String sqClass = u.getPerformanceClass();

            pkDelay += delayPk;
            totalDelay += delayDp;
            int firstTier = u.isFirstTier() ? 1 : 0;


            sLevelsClass.put(sqClass, sLevelsClass.getOrDefault(sqClass, new HashMap<>()));
            sLevelsClass.get(sqClass).put("pk", sLevelsClass.get(sqClass).getOrDefault("pk", 0) + delayPk);
            sLevelsClass.get(sqClass).put("dp", sLevelsClass.get(sqClass).getOrDefault("dp", 0) + delayDp);
            sLevelsClass.get(sqClass).put("count", sLevelsClass.get(sqClass).getOrDefault("count", 0) + 1);
            sLevelsClass.get(sqClass).put("first", sLevelsClass.get(sqClass).getOrDefault("first", 0) + firstTier);
            sLevelsClass.get(sqClass).put("max", (int) Math.max(sLevelsClass.get(sqClass).getOrDefault("max", 0), delayPk));
            sLevelsClass.get(sqClass).put("dedicated", sLevelsClass.get(sqClass).getOrDefault("dedicated", 0) + (u.isServicedByDedicated() ? 1 : 0));
            sLevelsClass.get(sqClass).put("private", sLevelsClass.get(sqClass).getOrDefault("private", 0) + (u.isServicedByHired() ? 1 : 0));
            sLevelsClass.get(sqClass).put("unmet_slevels", sLevelsClass.get(sqClass).getOrDefault("unmet_slevels", 0) + (u.isServiceLevelLowered() ? 1 : 0));

        }

        // Sum delays of finished users
        for (User u : deniedRequests) {
            String sqClass = u.getPerformanceClass();
            sLevelsClass.get(sqClass).put("rejected", sLevelsClass.get(sqClass).getOrDefault("rejected", 0) + (u.isRejected() ? 1 : 0));
            sLevelsClass.get(sqClass).put("unmet_slevels", sLevelsClass.get(sqClass).getOrDefault("unmet_slevels", 0) + (u.isRejected() ? 1 : 0));
        }

        // Average delays
        pkDelay = pkDelay / finishedRequests.size();
        totalDelay = totalDelay / finishedRequests.size();


        int nOfAssignedUsers = 0;
        int nOfVehiclesParked = 0;
        int seatCount = 0;
        int totalCurrentCapacity = 0;
        int emptySeats = 0;


        /***************************************************************************************************************
         ///////Model.Vehicle stats ////////////////////////////////////////////////////////////////////////////////////
         */

        int nOfSeatsCruisingVehicles = 0;
        int nOfSeatsRebalancing = 0;


        // Number of vehicles per occupation
        int[] vehicleOccupancy = new int[vehicleSize + 1];
        int[] fleetMakeup = new int[vehicleSize];
        int nOfVehiclesCruisingToPickup = 0;
        int nOfVehiclesRebalancing = 0;
        int nOfVehiclesDwellingInOrigin = 0;
        int nOfVehiclesServicingUsers = 0;
        int nOfVehiclesStoppedRebalancing = 0;
        double distTraveledRebal = 0;
        double distTraveledEmpty = 0;
        double distTraveledLoaded = 0;

        //How many seats were hired in this round?
        int hiredCapacity = 0;

        for (Vehicle v : setHiredVehicles) {
            hiredCapacity += v.getCapacity();
        }

        // Account for vehicles deactivated when running distance statistics
        for (Vehicle v : setDeactivated) {
            distTraveledEmpty += v.getDistanceTraveledEmpty();
            distTraveledLoaded += v.getDistanceTraveledLoaded();
            distTraveledRebal += v.getDistanceTraveledRebalancing();
        }

        for (Vehicle v : listVehicles) {

            // Update distance statistics
            distTraveledEmpty += v.getDistanceTraveledEmpty();
            distTraveledLoaded += v.getDistanceTraveledLoaded();
            distTraveledRebal += v.getDistanceTraveledRebalancing();

            // Update rebalancing statistics
            nOfVehiclesStoppedRebalancing += (v.isStoppedRebalanceToPickup() ? 1 : 0);

            // Clear interrupted rebalancing counter
            v.setStoppedRebalanceToPickup(false);

            // Increment total capacity
            totalCurrentCapacity += v.getCapacity();

            fleetMakeup[v.getCapacity() - 1]++;

            if (v.isParked()) {

                if (v.getLastVisitedNode() == v.getOrigin()) {

                    nOfVehiclesDwellingInOrigin++;

                } else {
                    // Vehicle is parked in the vicinity (Node Stop)
                    nOfVehiclesParked++;
                }

                emptySeats += v.getCapacity();

            } else if (v.isRebalancing()) {

                nOfVehiclesRebalancing++;
                nOfSeatsRebalancing += v.getCapacity();

            } else if (v.isCruising()) {

                nOfVehiclesCruisingToPickup++;
                nOfSeatsCruisingVehicles += v.getCapacity();


            } else {

                // Increment occupancy
                vehicleOccupancy[v.getCurrentLoad()]++;

                // Sum current load of vehicle
                seatCount += v.getCurrentLoad();

                nOfVehiclesServicingUsers++;

                emptySeats += v.getCapacity() - v.getCurrentLoad();

            }

        }

        // Percentage requests finished, denied, waiting, assigned
        double finishedRequestsPercentage = (double) finishedRequests.size() / allRequests.size();
        double deniedRequestsPercentage = (double) deniedRequests.size() / allRequests.size();
        double waitingRequestsPercentage = (double) waitingRequests.size() / allRequests.size();
        nOfAssignedUsers = allRequests.size() - deniedRequests.size() - finishedRequests.size() - waitingRequests.size();

        ArrayList<String> sLevelsClass = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> e :
                this.sLevelsClass.entrySet()) {

            int count = e.getValue().get("count");
            int pk = e.getValue().get("pk");
            int dp = e.getValue().get("dp");
            int max = e.getValue().get("max");
            int first = e.getValue().get("first");
            int nUsersInDedicatedVehicles = e.getValue().get("dedicated");
            int nUsersInPrivateVehicles = e.getValue().get("private");
            int nUsersRejected = e.getValue().get("rejected");
            int nUsersUnmetServiceLevels = e.getValue().get("unmet_slevels");

            sLevelsClass.add(String.format("%s (%4.2f, %4.2f) (max=%d, first=%4.2f) x %6d [Dedicated: %3d -- Private: %3d -- Rejected: %3d] [SL met: %3d -- SL unmet: %3d]",
                    e.getKey(),
                    ((double) pk) / count,
                    ((double) dp) / count,
                    max,
                    (double) first / count,
                    count + nUsersRejected,
                    nUsersInDedicatedVehicles,
                    nUsersInPrivateVehicles,
                    nUsersRejected,
                    count,
                    nUsersUnmetServiceLevels));
        }

        if (saveRoundInfoCSV == true) {
            this.addEntryCSV(currentTime,
                    setOfRequests.size(),
                    seatCount,
                    nOfSeatsCruisingVehicles,
                    nOfSeatsRebalancing,
                    emptySeats,
                    totalCurrentCapacity,
                    nOfVehiclesServicingUsers,
                    setHiredVehicles.size(),
                    setDeactivated.size(),
                    nOfVehiclesCruisingToPickup,
                    nOfVehiclesParked,
                    nOfVehiclesDwellingInOrigin,
                    nOfVehiclesRebalancing,
                    nOfVehiclesStoppedRebalancing,
                    nOfAssignedUsers,
                    waitingRequests.size(),
                    deniedRequests.size(),
                    finishedRequests.size(),
                    vehicleOccupancy,
                    fleetMakeup,
                    pkDelay,
                    totalDelay,
                    distTraveledEmpty,
                    distTraveledLoaded,
                    distTraveledRebal,
                    runTimes);
        }


        return String.format(
                "\n## REQUESTS MAKEUP ################" +
                        "\n       En-route: %6s (%.2f%%)" +
                        "\n        Waiting: %6s (%.2f%%)" +
                        "\n         Denied: %6s (%.2f%%)" +
                        "\n       Finished: %6s (%.2f%%)" +
                        "\n   Pickup delay: %d" +
                        "\n    Total delay: %d" +
                        "\n    Class delay: %s" +
                        "\n## SEAT STATUS ####################" +
                        "\n       Occupied: %6s (%.2f%%)" +
                        "\n     Picking up: %6s (%.2f%%)" +
                        "\n          Empty: %6s (%.2f%%)" +
                        "\n    Rebalancing: %6s (%.2f%%)" +
                        "\n## FLET STATUS ####################" +
                        "\n      Servicing: %6s (%.2f%%)" +
                        "\n         Parked: %6s (%.2f%%)" +
                        "\n    Rebalancing: %6s (%.2f%%)" +
                        "\n       Cruising: %6s (%.2f%%)" +
                        "\n       Inactive: %6s (%.2f%%)" +
                        "\n Stopped rebal.: %6d" +
                        "\n      Occupancy: %s" +
                        "\n         Makeup: %s (TOTAL CAPACITY: %d - HIRED: %d - #DEACTIVATED: %d)" +
                        "\n   Update fleet: %8.4f ms " +
                        "\n    Rebalancing: %8.4f ms " +
                        "\n  Update demand: %8.4f ms ",
                String.valueOf(nOfAssignedUsers), Math.abs((double) nOfAssignedUsers / allRequests.size()) * 100,
                String.valueOf(waitingRequests.size()), waitingRequestsPercentage * 100,
                String.valueOf(deniedRequests.size()), deniedRequestsPercentage * 100,
                String.valueOf(finishedRequests.size()), finishedRequestsPercentage * 100,
                (int) pkDelay,
                (int) totalDelay,
                String.join(" / ", sLevelsClass),
                String.valueOf(seatCount), (double) seatCount / totalCurrentCapacity * 100,
                String.valueOf(nOfSeatsCruisingVehicles), (double) nOfSeatsCruisingVehicles / totalCurrentCapacity * 100,
                String.valueOf(emptySeats), (double) emptySeats / totalCurrentCapacity * 100,
                String.valueOf(nOfSeatsRebalancing), (double) nOfSeatsRebalancing / totalCurrentCapacity * 100,
                String.valueOf(nOfVehiclesServicingUsers), (double) nOfVehiclesServicingUsers * 100 / listVehicles.size(),
                String.valueOf(nOfVehiclesParked), (double) nOfVehiclesParked * 100 / listVehicles.size(),
                String.valueOf(nOfVehiclesRebalancing), (double) nOfVehiclesRebalancing * 100 / listVehicles.size(),
                String.valueOf(nOfVehiclesCruisingToPickup), (double) nOfVehiclesCruisingToPickup * 100 / listVehicles.size(),
                String.valueOf(nOfVehiclesDwellingInOrigin), (double) nOfVehiclesDwellingInOrigin * 100 / listVehicles.size(),
                nOfVehiclesStoppedRebalancing,
                Arrays.toString(vehicleOccupancy),
                Arrays.toString(fleetMakeup),
                totalCurrentCapacity,
                setHiredVehicles.size(),
                setDeactivated.size(),
                runTimes.get(Solution.TIME_UPDATE_FLEET_STATUS) / 1000000000.0,
                runTimes.get(Solution.TIME_REBALANCING_FLEET) / 1000000000.0,
                runTimes.get(Solution.TIME_UPDATE_DEMAND) / 1000000000.0
        );
    }

    public void addEntryCSV(int currentTime,
                            int numberOfRequests,
                            int seatCount,
                            int pickingUpSeats,
                            int rebalancingSeats,
                            int emptySeats,
                            int totalCapacity,
                            int activeVehicles,
                            int hiredVehicles,
                            int deactivatedVehicles,
                            int pickingUp,
                            int parkedVehicles,
                            int originVehicles,
                            int rebalancingVehicles,
                            int stoppedRebalancing,
                            int enrouteCount,
                            int waitingRequests,
                            int deniedRequests,
                            int finishedRequests,
                            int[] vehicleOccupancy,
                            int[] fleetMakeup,
                            double pkDelay,
                            double totalDelay,
                            double distTraveledEmpty,
                            double distTraveledLoaded,
                            double distTraveledRebal,
                            Map<String, Long> runTimes) {

        List<String> roundEntry = new ArrayList<>();
        roundEntry.add(Config.sec2Datetime(currentTime));
        roundEntry.add(String.valueOf(waitingRequests));
        roundEntry.add(String.valueOf(finishedRequests));
        roundEntry.add(String.valueOf(deniedRequests));
        roundEntry.add(String.valueOf(numberOfRequests));
        roundEntry.add(String.valueOf(seatCount));
        roundEntry.add(String.valueOf(pickingUpSeats));
        roundEntry.add(String.valueOf(rebalancingSeats));
        roundEntry.add(String.valueOf(emptySeats));
        roundEntry.add(String.valueOf(totalCapacity));
        roundEntry.add(String.valueOf(activeVehicles));
        roundEntry.add(String.valueOf(hiredVehicles));
        roundEntry.add(String.valueOf(deactivatedVehicles));
        roundEntry.add(String.valueOf(enrouteCount));
        roundEntry.add(String.format("%.4f", pkDelay));
        roundEntry.add(String.format("%.4f", totalDelay));
        roundEntry.add(String.valueOf(parkedVehicles));
        roundEntry.add(String.valueOf(originVehicles));
        roundEntry.add(String.valueOf(rebalancingVehicles));
        roundEntry.add(String.valueOf(stoppedRebalancing));
        roundEntry.add(String.valueOf(parkedVehicles + originVehicles));
        roundEntry.add(String.valueOf(pickingUp));


        for (int i = 1; i < vehicleOccupancy.length; i++) {
            roundEntry.add(String.valueOf(vehicleOccupancy[i]));
        }

        for (int s : fleetMakeup) {
            roundEntry.add(String.valueOf(s));
        }

        roundEntry.add(String.format("%.4f", distTraveledEmpty));
        roundEntry.add(String.format("%.4f", distTraveledLoaded));
        roundEntry.add(String.format("%.4f", distTraveledRebal));
        for (String h : Solution.TIME_HEADERS) {
            roundEntry.add(String.valueOf(runTimes.get(h) / 1000000000.0));
        }


        for (Map.Entry<String, Map<String, Integer>> e :
                this.sLevelsClass.entrySet()) {

            int count = e.getValue().get("count");
            int pk = e.getValue().get("pk");
            int dp = e.getValue().get("dp");
            int unmet = e.getValue().get("unmet_slevels");

            roundEntry.add(String.format("%.2f", (double) pk / count));
            roundEntry.add(String.format("%.2f", (double) dp / count));
            roundEntry.add(String.format("%d", count));
            roundEntry.add(String.format("%d", unmet));

        }

        this.listRoundEntries.add(roundEntry);
    }

    public void printAllJourneys(List<Vehicle> listVehicles) {

        System.out.println(HelperIO.printJourneys(listVehicles));
        System.out.println("GEOJSON DATA");
        // Dao dao = Dao.getInstance();
        for (Vehicle v : listVehicles) {
            System.out.println(v.getOrigin().getNetworkId());
            //System.out.println(v.getInfo());

            //System.out.println(v.getJourneyInfo());
            //ServerUtil.printGeoJsonJourney(v, Simulation.);
        }
    }
}
