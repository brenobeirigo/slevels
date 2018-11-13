package simulation;

import config.Config;
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



    /* CSV output
    departureVehicleCurrent;seatCount;activeVehicles;enrouteCount;deniedRequests.size();finishedRequests.size();
    vehicleOccupancy;fleetMakeup;pkDelay;totalDelay;runTime; */
    private ArrayList<List<String>> entries;
    private List<String> header;
    /* Output */
    private Path outputFile;
    private long runTime;

    public Solution(String methodName, int nOfVehicles, int maxNumberOfTrips, int vehicleCapacity, int timeHorizon, int totalHorizon) {
        this.methodName = methodName;
        this.nOfVehicles = nOfVehicles;
        this.maxNumberOfTrips = maxNumberOfTrips;
        this.vehicleCapacity = vehicleCapacity;
        this.timeHorizon = timeHorizon;
        this.totalHorizon = totalHorizon;
        this.entries = new ArrayList<>();
        createHeader();
    }

    // Initialize solution
    public Solution(String methodName,
                    int nOfVehicles,
                    int maxNumberOfTrips,
                    int vehicleCapacity,
                    int timeHorizon,
                    int totalHorizon,
                    String serviceRate,
                    String customerSegmentation) {

        // Initialize solution
        this(methodName, nOfVehicles, maxNumberOfTrips, vehicleCapacity, timeHorizon, totalHorizon);
        this.serviceRate = serviceRate;
        this.customerSegmentation = customerSegmentation;
    }

    public void createHeader() {

        header = new ArrayList<>();
        header.add("timestamp");
        header.add("waiting");
        header.add("finished");
        header.add("denied");
        header.add("n_requests");
        header.add("seat_count");
        header.add("active_vehicles");
        header.add("enroute_count");
        header.add("pk_delay");
        header.add("total_delay");
        header.add("idle");
        header.add("picking_up");

        for (int i = 1; i <= vehicleCapacity; i++) {
            header.add("O" + String.valueOf(i));
        }

        for (int i = 1; i <= vehicleCapacity; i++) {
            header.add("V" + String.valueOf(i));
        }

        header.add("run_time");
    }

    public void save() {
        // File path
        this.outputFile = Paths.get(
                String.format("V%d-%d_R%d_TW%d_TH%d_%s_%s_%s.csv",
                        nOfVehicles,
                        vehicleCapacity,
                        maxNumberOfTrips,
                        timeHorizon,
                        totalHorizon,
                        methodName,
                        serviceRate,
                        customerSegmentation
                ));

        System.out.println("!!!!!! " + this.outputFile);

        try {
            writer = Files.newBufferedWriter(outputFile);
            String[] a = header.toArray(new String[0]);
            CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(a));

            for (List<String> e : this.entries) {
                csvPrinter.printRecord(e);
                csvPrinter.flush();
            }

            csvPrinter.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getRoundStatistics(int currentTime,
                                     int vehicleSize,
                                     List<Vehicle> listVehicles,
                                     Set<User> waitingRequests,
                                     Set<User> finishedRequests,
                                     Set<User> deniedRequests,
                                     List<User> setOfRequests,
                                     Map<Integer, User> allRequests,
                                     long runTime) {
        double pkDelay = 0;
        double totalDelay = 0;

        // Sum delays of finished users
        for (User u : finishedRequests) {
            pkDelay += u.getNodePk().getDelay();
            totalDelay += u.getNodeDp().getDelay();
        }

        // Average delays
        pkDelay = pkDelay / finishedRequests.size();
        totalDelay = totalDelay / finishedRequests.size();


        int enrouteCount = 0;
        int activeVehicles = 0;
        int seatCount = 0;
        int totalSeats = 0;

        // Number of vehicles per occupation
        int[] vehicleOccupancy = new int[vehicleSize + 1];
        int[] fleetMakeup = new int[vehicleSize];

        /***************************************************************************************************************
         ///////Model.Vehicle stats ////////////////////////////////////////////////////////////////////////////////////
         */
        int pickingUp = 0;

        for (Vehicle v : listVehicles) {

            // Increment occupancy
            vehicleOccupancy[v.getLoad()]++;
            fleetMakeup[v.getCapacity() - 1]++;

            int nUsers = v.getUsers().size();

            // If there are users inside vehicle
            if (nUsers > 0) {

                if (v.getLoad() == 0) {
                    pickingUp++;
                }

                // Sum current load of vehicle
                seatCount += v.getLoad();

                // Sum capacity of vehicle
                totalSeats += v.getCapacity();

                // Number of users en-route
                enrouteCount += nUsers;

                // If there users inside, vehicle is active
                activeVehicles++;

            }

        }

        // Stats
        double finishedRequestsPercentage = (double) finishedRequests.size() / allRequests.size();
        double deniedRequestsPercentage = (double) deniedRequests.size() / allRequests.size();
        double waitingRequestsPercentage = (double) waitingRequests.size() / allRequests.size();

        this.addEntryCSV(currentTime,
                setOfRequests.size(),
                seatCount,
                activeVehicles,
                pickingUp,
                enrouteCount,
                waitingRequests.size(),
                deniedRequests.size(),
                finishedRequests.size(),
                vehicleOccupancy,
                fleetMakeup,
                pkDelay,
                totalDelay,
                runTime);


        return String.format(

                "\n## REQUESTS MAKEUP:" +
                        "\n    En-route: %6s (%.2f%%)" +
                        "\n     Waiting: %6s (%.2f%%)" +
                        "\n      Denied: %6s (%.2f%%)" +
                        "\n    Finished: %6s (%.2f%%)" +
                        "\nPickup delay: %d" +
                        "\n Total delay: %d" +
                        "## VEHICLE:" +
                        "\n  Seat count: %6s (%.2f%%)" +
                        "\n Fleet usage: %6s (%.2f%%)" +
                        "\n  Picking up: %6s (%.2f%%)" +
                        "\n   Occupancy: %s" +
                        "\n      Makeup: %s" +
                        "\n    Run time: %.2f s ",
                String.valueOf(enrouteCount), Math.abs((double) enrouteCount / allRequests.size()) * 100,
                String.valueOf(waitingRequests.size()), waitingRequestsPercentage * 100,
                String.valueOf(deniedRequests.size()), deniedRequestsPercentage * 100,
                String.valueOf(finishedRequests.size()), finishedRequestsPercentage * 100,
                (int) pkDelay,
                (int) totalDelay,
                String.valueOf(seatCount), (double) seatCount / totalSeats * 100,
                String.valueOf(activeVehicles), (double) activeVehicles * 100 / listVehicles.size(),
                String.valueOf(pickingUp), (double) pickingUp * 100 / listVehicles.size(),
                String.valueOf(Arrays.toString(vehicleOccupancy)),
                String.valueOf(Arrays.toString(fleetMakeup)),
                (double) runTime / 1000);
    }

    public void addEntryCSV(int currentTime,
                            int numberOfRequests,
                            int seatCount,
                            int activeVehicles,
                            int pickingUp,
                            int enrouteCount,
                            int waitingRequests,
                            int deniedRequests,
                            int finishedRequests,
                            int[] vehicleOccupancy,
                            int[] fleetMakeup,
                            double pkDelay,
                            double totalDelay,
                            long runTime) {

        List<String> entry = new ArrayList<>();
        entry.add(Config.sec2Datetime(currentTime));
        entry.add(String.valueOf(waitingRequests));
        entry.add(String.valueOf(finishedRequests));
        entry.add(String.valueOf(deniedRequests));
        entry.add(String.valueOf(numberOfRequests));
        entry.add(String.valueOf(seatCount));
        entry.add(String.valueOf(activeVehicles));
        entry.add(String.valueOf(enrouteCount));
        entry.add(String.valueOf(pkDelay));
        entry.add(String.valueOf(totalDelay));

        entry.add(String.valueOf(vehicleOccupancy[0] - pickingUp));

        entry.add(String.valueOf(pickingUp));

        for (int i = 1; i < vehicleOccupancy.length; i++) {
            entry.add(String.valueOf(vehicleOccupancy[i]));
        }

        for (int s : fleetMakeup) {
            entry.add(String.valueOf(s));
        }

        entry.add(String.valueOf(runTime));

        this.entries.add(entry);
    }
}
