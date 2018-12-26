package simulation;

import config.Config;
import dao.Dao;
import model.User;
import model.Vehicle;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.util.Comparator.comparingInt;

public class Solution {

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
    private final String OUTPUT_FOLDER = "output";
    private boolean allowVehicleCreation;
    private boolean allowDelayExtension;
    private Map<String, Map<String, Integer>> sLevelsClass;
    /* Rebalancing */
    private int maxRoundsIdleBeforeRebalance;

    /* Deactivating */
    private int deactivationFactor;
    private int maxDelayExtensionsBeforeHiring;


    /* CSV output
    departureVehicleCurrent;seatCount;activeVehicles;enrouteCount;deniedRequests.size();finishedRequests.size();
    vehicleOccupancy;fleetMakeup;pkDelay;totalDelay;runTime; */
    private ArrayList<List<String>> entries;
    private List<String> header;

    /* Output */
    private Path outputFile;
    private Path outputFileUsers;
    private Path outputGeoJson;

    private long runTime;

    public Solution(String methodName,
                    int nOfVehicles,
                    int maxNumberOfTrips,
                    int vehicleCapacity,
                    int timeHorizon,
                    int totalHorizon,
                    int maxRoundsIdleBeforeRebalance,
                    int deactivationFactor,
                    int maxDelayExtensionsBeforeHiring,
                    boolean allowVehicleCreation,
                    boolean allowDelayExtension) {

        this.methodName = methodName;
        this.nOfVehicles = nOfVehicles;
        this.maxNumberOfTrips = maxNumberOfTrips;
        this.vehicleCapacity = vehicleCapacity;
        this.timeHorizon = timeHorizon;
        this.totalHorizon = totalHorizon;
        this.deactivationFactor = deactivationFactor;
        this.maxRoundsIdleBeforeRebalance = maxRoundsIdleBeforeRebalance;
        this.maxDelayExtensionsBeforeHiring = maxDelayExtensionsBeforeHiring;
        this.allowVehicleCreation = allowVehicleCreation;
        this.allowDelayExtension = allowDelayExtension;
        this.entries = new ArrayList<>();
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
                    int maxRoundsIdleBeforeRebalance,
                    int deactivationFactor,
                    int maxDelayExtensionsBeforeHiring,
                    boolean allowVehicleCreation,
                    boolean allowDelayExtension,
                    String serviceRate,
                    String customerSegmentation) {

        // Initialize solution
        this(methodName,
                nOfVehicles,
                maxNumberOfTrips,
                vehicleCapacity,
                timeHorizon,
                totalHorizon,
                maxRoundsIdleBeforeRebalance,
                deactivationFactor,
                maxDelayExtensionsBeforeHiring,
                allowVehicleCreation,
                allowDelayExtension);

        this.serviceRate = serviceRate;
        this.customerSegmentation = customerSegmentation;


        // File path
        this.outputFile = Paths.get(
                String.format("%s/%s_V%03d_%s-%02d_R%03d_%s_%s_%s_TW%02d_TH%05d_%s_%s.csv",
                        OUTPUT_FOLDER,
                        methodName,
                        nOfVehicles,
                        (allowVehicleCreation ? "PLUS" : "ONLY"),
                        vehicleCapacity,
                        maxNumberOfTrips,
                        (maxRoundsIdleBeforeRebalance != Integer.MAX_VALUE ? String.format("REB%03d", maxRoundsIdleBeforeRebalance) : "REB_NO"),
                        (allowVehicleCreation ? String.format("DEA-%02d", deactivationFactor) : "DEA-NO"),
                        (allowDelayExtension ? String.format("EXT-%02d", maxDelayExtensionsBeforeHiring) : "EXT-NO"),
                        timeHorizon,
                        totalHorizon,
                        serviceRate,
                        customerSegmentation
                ));

        // File path
        this.outputGeoJson = Paths.get(
                String.format("%s/%s_V%03d_%s-%02d_R%03d_%s_%s_%s_TW%02d_TH%05d_%s_%s_GEOJSON.csv",
                        OUTPUT_FOLDER,
                        methodName,
                        nOfVehicles,
                        (allowVehicleCreation ? "PLUS" : "ONLY"),
                        vehicleCapacity,
                        maxNumberOfTrips,
                        (maxRoundsIdleBeforeRebalance != Integer.MAX_VALUE ? String.format("REB%03d", maxRoundsIdleBeforeRebalance) : "REB_NO"),
                        (allowVehicleCreation ? String.format("DEA-%02d", deactivationFactor) : "DEA-NO"),
                        (allowDelayExtension ? String.format("EXT-%02d", maxDelayExtensionsBeforeHiring) : "EXT-NO"),
                        timeHorizon,
                        totalHorizon,
                        serviceRate,
                        customerSegmentation
                ));

        // File path
        this.outputFileUsers = Paths.get(
                String.format("%s/%s_V%03d_%s-%02d_R%03d_%s_%s_%s_TW%02d_TH%05d_%s_%s_USERS.csv",
                        OUTPUT_FOLDER,
                        methodName,
                        nOfVehicles,
                        (allowVehicleCreation ? "PLUS" : "ONLY"),
                        vehicleCapacity,
                        maxNumberOfTrips,
                        (maxRoundsIdleBeforeRebalance != Integer.MAX_VALUE ? String.format("REB%03d", maxRoundsIdleBeforeRebalance) : "REB_NO"),
                        (allowVehicleCreation ? String.format("DEA-%02d", deactivationFactor) : "DEA-NO"),
                        (allowDelayExtension ? String.format("EXT-%02d", maxDelayExtensionsBeforeHiring) : "EXT-NO"),
                        timeHorizon,
                        totalHorizon,
                        serviceRate,
                        customerSegmentation
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

        header = new ArrayList<>();
        header.add("timestamp");
        header.add("waiting");
        header.add("finished");
        header.add("denied");
        header.add("n_requests");
        header.add("seat_count");
        header.add("picking_up_seats");
        header.add("rebalancing_seats");
        header.add("empty_seats");
        header.add("total_capacity");
        header.add("active_vehicles");
        header.add("enroute_count");
        header.add("pk_delay");
        header.add("total_delay");
        header.add("parked_vehicles");
        header.add("origin_vehicles");
        header.add("rebalancing");
        header.add("stopped_rebalancing");
        header.add("idle");
        header.add("picking_up");

        for (int i = 1; i <= vehicleCapacity; i++) {
            header.add("O" + String.valueOf(i));
        }

        for (int i = 1; i <= vehicleCapacity; i++) {
            header.add("V" + String.valueOf(i));
        }

        header.add("distance_traveled_cruising");
        header.add("distance_traveled_loaded");
        header.add("distance_traveled_rebalancing");
        header.add("run_time");

        for (String q :
                Config.getInstance().qosDic.keySet()) {
            header.add(String.format("%s_pk", q));
            header.add(String.format("%s_dp", q));
            header.add(String.format("%s_count", q));
        }
    }

    public Path getOutputFile() {
        return outputFile;
    }

    public void save() {

        System.out.println(">>>>>>> " + this.outputFile);

        try {
            writer = Files.newBufferedWriter(outputFile);

            String[] a = header.toArray(new String[0]);

            CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(a).withCommentMarker('#'));

            for (Map.Entry<String, Config.Qos> e : Config.getInstance().qosDic.entrySet()) {

                csvPrinter.printComment(e.getValue().toString());

            }

            for (List<String> e : this.entries) {

                csvPrinter.printRecord(e);
                csvPrinter.flush();
            }

            csvPrinter.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Save the GeoJson output (Linestring)
     */
    public void saveGeoJson() {

        try {
            writer = Files.newBufferedWriter(outputGeoJson);
            writer.write(allPoints());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveUserInfo(Set<User> listOfServicedUsers) {


        List<User> sortedUsersPk = new ArrayList<>(listOfServicedUsers);
        Collections.sort(sortedUsersPk, comparingInt(o -> o.getNodePk().getEarliest()));


        try {
            writer = Files.newBufferedWriter(outputFileUsers);
            String[] a = new String[]{"earliest", "id", "class", "pk_delay", "ride_delay", "pk_time", "dp_time", "id_from", "id_to", "dist", "service"};

            CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(a).withCommentMarker('#'));

            for (Map.Entry<String, Config.Qos> e : Config.getInstance().qosDic.entrySet()) {
                csvPrinter.printComment(e.getValue().toString());
            }
            for (User u : sortedUsersPk) {

                List<String> entry = new ArrayList<>();
                entry.add(Config.sec2Datetime(u.getNodePk().getEarliest()));
                entry.add(String.valueOf(u.getId()));
                entry.add(String.valueOf(u.getPerformanceClass()));
                entry.add(String.valueOf(u.getNodePk().getDelay()));
                entry.add(String.valueOf(u.getNodeDp().getDelay()));
                entry.add(Config.sec2Datetime(u.getNodePk().getArrival()));
                entry.add(Config.sec2Datetime(u.getNodeDp().getArrival()));
                entry.add(String.valueOf(u.getNodePk().getNetworkId()));
                entry.add(String.valueOf(u.getNodeDp().getNetworkId()));
                entry.add(String.valueOf(Dao.getInstance().getDistSec(u.getNodePk(), u.getNodeDp())));
                entry.add(String.valueOf(u.isServedByHired() ? "PRIVATE" : u.getNumberOfDelayExtensions() > 0 ? "DELAYED" : "OK"));
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
                                      List<User> waitingRequests,
                                      Set<User> finishedRequests,
                                      Set<User> deniedRequests,
                                      List<User> setOfRequests,
                                      Map<Integer, User> allRequests,
                                      long runTime,
                                      long rebalancingTime) {
        double pkDelay = 0;
        double totalDelay = 0;

        //The procedure below sweeps all customers so far...
        sLevelsClass.clear();


        // Sum delays of finished users
        for (User u : finishedRequests) {

            int delayPk = u.getNodePk().getDelay();
            int delayDp = u.getNodeDp().getDelay();
            String sqClass = u.getPerformanceClass();

            pkDelay += delayPk;
            totalDelay += delayDp;


            sLevelsClass.put(sqClass, sLevelsClass.getOrDefault(sqClass, new HashMap<>()));
            sLevelsClass.get(sqClass).put("pk", sLevelsClass.get(sqClass).getOrDefault("pk", 0) + delayPk);
            sLevelsClass.get(sqClass).put("dp", sLevelsClass.get(sqClass).getOrDefault("dp", 0) + delayDp);
            sLevelsClass.get(sqClass).put("count", sLevelsClass.get(sqClass).getOrDefault("count", 0) + 1);
            sLevelsClass.get(sqClass).put("low", sLevelsClass.get(sqClass).getOrDefault("low", 0) + (u.getNumberOfDelayExtensions() > 0 ? 1 : 0));
            sLevelsClass.get(sqClass).put("private", sLevelsClass.get(sqClass).getOrDefault("private", 0) + (u.isServedByHired() ? 1 : 0));
            //System.out.println("Slevels:" + sLevelsClass);
        }

        //System.out.println("Slevels:" + sLevelsClass);


        // Average delays
        pkDelay = pkDelay / finishedRequests.size();
        totalDelay = totalDelay / finishedRequests.size();


        int nOfAssignedUsers = 0;
        int nOfVehiclesParked = 0;
        int seatCount = 0;
        int totalCapacity = 0;
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

        for (Vehicle v : listVehicles) {

            distTraveledEmpty += v.getDistanceTraveledEmpty();
            distTraveledLoaded += v.getDistanceTraveledLoaded();
            distTraveledRebal += v.getDistanceTraveledRebalancing();
            nOfVehiclesStoppedRebalancing += (v.isStoppedRebalanceToPickup() ? 1 : 0);
            v.setStoppedRebalanceToPickup(false);
            // Increment total capacity
            totalCapacity += v.getCapacity();

            fleetMakeup[v.getCapacity() - 1]++;

            if (v.isParked()) {

                if (v.getCurrentNode() == v.getOrigin()) {

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
                vehicleOccupancy[v.getLoad()]++;

                // Sum current load of vehicle
                seatCount += v.getLoad();

                nOfVehiclesServicingUsers++;

                emptySeats += v.getCapacity() - v.getLoad();

            }

        }

        //System.out.println(String.format("Parked: %d - Seat: %d - Rebalancing: %d - Cruising: %d - Total: %d", emptySeats, seatCount, nOfSeatsRebalancing, nOfSeatsCruisingVehicles, emptySeats + seatCount + nOfSeatsRebalancing + nOfSeatsCruisingVehicles));

        // Stats
        double finishedRequestsPercentage = (double) finishedRequests.size() / allRequests.size();
        double deniedRequestsPercentage = (double) deniedRequests.size() / allRequests.size();
        double waitingRequestsPercentage = (double) waitingRequests.size() / allRequests.size();

        this.addEntryCSV(currentTime,
                setOfRequests.size(),
                seatCount,
                nOfSeatsCruisingVehicles,
                nOfSeatsRebalancing,
                emptySeats,
                totalCapacity,
                nOfVehiclesServicingUsers,
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
                runTime);

        ArrayList<String> sLevelsClass = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> e :
                this.sLevelsClass.entrySet()) {

            int count = e.getValue().get("count");
            int pk = e.getValue().get("pk");
            int dp = e.getValue().get("dp");
            int slRejectedUsers = e.getValue().get("low");
            int privateVehicle = e.getValue().get("private");

            sLevelsClass.add(String.format("%s (%4.2f, %4.2f) x %6d [Lowered: %3d -- Private: %3d]", e.getKey(), ((double) pk) / count, ((double) dp) / count, count, slRejectedUsers, privateVehicle));

        }

        /*

        if(!deniedRequests.isEmpty()){
            System.out.println("####### Denied...");
            for (User u:deniedRequests) {
                u.printDetailed();
            }
        }


        if(!waitingRequests.isEmpty()){
            System.out.println("####### Waiting...");
            for (User u:waitingRequests) {
                u.printDetailed();
            }
        }

        */
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
                        "\n         Makeup: %s (TOTAL CAPACITY: %d)" +
                        "\n       Run time: %.2f s " +
                        "\n      Reb. time: %.2f s ",
                String.valueOf(nOfAssignedUsers), Math.abs((double) nOfAssignedUsers / allRequests.size()) * 100,
                String.valueOf(waitingRequests.size()), waitingRequestsPercentage * 100,
                String.valueOf(deniedRequests.size()), deniedRequestsPercentage * 100,
                String.valueOf(finishedRequests.size()), finishedRequestsPercentage * 100,
                (int) pkDelay,
                (int) totalDelay,
                String.join(" / ", sLevelsClass),
                String.valueOf(seatCount), (double) seatCount / totalCapacity * 100,
                String.valueOf(nOfSeatsCruisingVehicles), (double) nOfSeatsCruisingVehicles / totalCapacity * 100,
                String.valueOf(emptySeats), (double) emptySeats / totalCapacity * 100,
                String.valueOf(nOfSeatsRebalancing), (double) nOfSeatsRebalancing / totalCapacity * 100,
                String.valueOf(nOfVehiclesServicingUsers), (double) nOfVehiclesServicingUsers * 100 / listVehicles.size(),
                String.valueOf(nOfVehiclesParked), (double) nOfVehiclesParked * 100 / listVehicles.size(),
                String.valueOf(nOfVehiclesRebalancing), (double) nOfVehiclesRebalancing * 100 / listVehicles.size(),
                String.valueOf(nOfVehiclesCruisingToPickup), (double) nOfVehiclesCruisingToPickup * 100 / listVehicles.size(),
                String.valueOf(nOfVehiclesDwellingInOrigin), (double) nOfVehiclesDwellingInOrigin * 100 / listVehicles.size(),
                nOfVehiclesStoppedRebalancing,
                String.valueOf(Arrays.toString(vehicleOccupancy)),
                String.valueOf(Arrays.toString(fleetMakeup)),
                totalCapacity,
                (double) runTime / 1000,
                (double) rebalancingTime / 1000);



    }

    public void addEntryCSV(int currentTime,
                            int numberOfRequests,
                            int seatCount,
                            int pickingUpSeats,
                            int rebalancingSeats,
                            int emptySeats,
                            int totalCapacity,
                            int activeVehicles,
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
                            long runTime) {

        List<String> entry = new ArrayList<>();
        entry.add(Config.sec2Datetime(currentTime));
        entry.add(String.valueOf(waitingRequests));
        entry.add(String.valueOf(finishedRequests));
        entry.add(String.valueOf(deniedRequests));
        entry.add(String.valueOf(numberOfRequests));
        entry.add(String.valueOf(seatCount));
        entry.add(String.valueOf(pickingUpSeats));
        entry.add(String.valueOf(rebalancingSeats));
        entry.add(String.valueOf(emptySeats));
        entry.add(String.valueOf(totalCapacity));
        entry.add(String.valueOf(activeVehicles));
        entry.add(String.valueOf(enrouteCount));
        entry.add(String.format("%.4f", pkDelay));
        entry.add(String.format("%.4f", totalDelay));
        entry.add(String.valueOf(parkedVehicles));
        entry.add(String.valueOf(originVehicles));
        entry.add(String.valueOf(rebalancingVehicles));
        entry.add(String.valueOf(stoppedRebalancing));
        entry.add(String.valueOf(parkedVehicles - originVehicles));
        entry.add(String.valueOf(pickingUp));


        for (int i = 1; i < vehicleOccupancy.length; i++) {
            entry.add(String.valueOf(vehicleOccupancy[i]));
        }

        for (int s : fleetMakeup) {
            entry.add(String.valueOf(s));
        }

        entry.add(String.format("%.4f", distTraveledEmpty));
        entry.add(String.format("%.4f", distTraveledLoaded));
        entry.add(String.format("%.4f", distTraveledRebal));
        entry.add(String.valueOf(runTime));


        //System.out.println("Service levels per class");
        //System.out.println(sLevelsClass);

        for (Map.Entry<String, Map<String, Integer>> e :
                this.sLevelsClass.entrySet()) {

            int count = e.getValue().get("count");
            int pk = e.getValue().get("pk");
            int dp = e.getValue().get("dp");

            entry.add(String.format("%.2f", (double) pk / count));
            entry.add(String.format("%.2f", (double) dp / count));
            entry.add(String.format("%d", count));

        }

        this.entries.add(entry);
    }
}
