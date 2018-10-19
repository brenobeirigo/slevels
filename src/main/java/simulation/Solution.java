package simulation;

import config.Config;
import model.User;
import model.Vehicle;
import model.node.NodeOrigin;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Solution {

    BufferedWriter writer;
    /* Instance */
    private int nOfVehicles;
    private int maxNumberOfTrips;
    private int vehicleCapacity;
    /* CSV output

    currentTime;seatCount;activeVehicles;enrouteCount;deniedRequests.size();finishedRequests.size();
    vehicleOccupancy;fleetMakeup;pkDelay;totalDelay;runTime; */
    private ArrayList<List<String>> entries;
    private List<String> header;
    /* Output */
    private Path outputFile;
    private long runTime;

    public Solution(int nOfVehicles, int maxNumberOfTrips, int vehicleCapacity) {
        this.nOfVehicles = nOfVehicles;
        this.maxNumberOfTrips = maxNumberOfTrips;
        this.vehicleCapacity = vehicleCapacity;
        this.entries = new ArrayList<>();
        createHeader();
    }

    public void createHeader() {

        header = new ArrayList<>();
        header.add("timestamp");
        header.add("seat_count");
        header.add("active_vehicles");
        header.add("enroute_count");
        header.add("denied");
        header.add("finished");
        header.add("pk_delay");
        header.add("total_delay");


        for (int i = 0; i <= vehicleCapacity; i++) {
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
                String.format("V%d-%d_R%d.csv",
                        nOfVehicles,
                        vehicleCapacity,
                        maxNumberOfTrips));

        System.out.println("!!!!!!" + this.outputFile);

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
                                     Set<User> finishedRequests,
                                     Set<User> deniedRequests,
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

        /*********************************************************************************************************
         ///////Model.Vehicle stats //////////////////////////////////////////////////////////////////////////////////////
         */

        for (Vehicle v : listVehicles) {

            // Increment occupancy
            vehicleOccupancy[v.getLoad()]++;
            fleetMakeup[v.getCapacity() - 1]++;

            int nUsers = v.getListUsers().size();

            // If there are users inside vehicle
            if (nUsers > 0) {

                for (User u : v.getListUsers()) {
                    seatCount += u.getNumPassengers();
                }

                // Number of users en-route
                enrouteCount += nUsers;

                // Active vehicles and total seats only for vehicles that left origin
                if (!(v.getCurrentNode() instanceof NodeOrigin)) {
                    // Model.Vehicle is active
                    activeVehicles++;
                }
            }

            // Active vehicles and total seats only for vehicles that left origin
            if (!(v.getCurrentNode() instanceof NodeOrigin)) {
                // Model.Vehicle is active
                totalSeats += v.getCapacity();
            }
        }

        // Stats
        double finishedRequestsPercentage = (double) finishedRequests.size() / allRequests.size();
        double deniedRequestsPercentage = (double) deniedRequests.size() / allRequests.size();


        this.addEntryCSV(currentTime,
                seatCount,
                activeVehicles,
                enrouteCount,
                deniedRequests.size(),
                finishedRequests.size(),
                vehicleOccupancy,
                fleetMakeup,
                pkDelay,
                totalDelay,
                runTime);


        return String.format(
                "## REAL TIME:" +
                        "\n    En-route: %6s (%.2f%%)" +
                        "\n  Seat count: %6s (%.2f%%)" +
                        "\n Fleet usage: %6s (%.2f%%)" +

                        "\n## OVERALL:" +
                        "\n      Denied: %6s (%.2f%%)" +
                        "\n    Finished: %6s (%.2f%%)" +
                        "\nPickup delay: %d" +
                        "\n Total delay: %d" +
                        "\n   Occupancy: %s" +
                        "\n    Run time: %.2f s ",
                String.valueOf(enrouteCount), Math.abs((double) enrouteCount / allRequests.size()) * 100,
                String.valueOf(seatCount), (double) seatCount / totalSeats * 100,
                String.valueOf(activeVehicles), (double) activeVehicles * 100 / listVehicles.size(),
                String.valueOf(deniedRequests.size()), deniedRequestsPercentage * 100,
                String.valueOf(finishedRequests.size()), finishedRequestsPercentage * 100,
                (int) pkDelay,
                (int) totalDelay,
                String.valueOf(Arrays.toString(vehicleOccupancy)),
                (double) runTime / 1000);


    }

    public void addEntryCSV(int currentTime,
                            int seatCount,
                            int activeVehicles,
                            int enrouteCount,
                            int deniedRequests,
                            int finishedRequests,
                            int[] vehicleOccupancy,
                            int[] fleetMakeup,
                            double pkDelay,
                            double totalDelay,
                            long runTime) {

        List<String> entry = new ArrayList<>();
        entry.add(Config.sec2Datetime(currentTime));
        entry.add(String.valueOf(seatCount));
        entry.add(String.valueOf(activeVehicles));
        entry.add(String.valueOf(enrouteCount));
        entry.add(String.valueOf(deniedRequests));
        entry.add(String.valueOf(finishedRequests));
        entry.add(String.valueOf(pkDelay));
        entry.add(String.valueOf(totalDelay));

        for (int o : vehicleOccupancy) {
            entry.add(String.valueOf(o));
        }

        for (int s : fleetMakeup) {
            entry.add(String.valueOf(s));
        }

        entry.add(String.valueOf(runTime));

        this.entries.add(entry);
    }
}
