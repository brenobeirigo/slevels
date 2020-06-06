package helper;

import config.Config;
import model.User;
import model.Vehicle;
import model.node.NodeOrigin;
import model.node.NodeStop;
import model.node.NodeTargetRebalancing;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class HelperIO {

    public static String getHeaderTW(int start_time,
                                     int duration,
                                     int left_tw,
                                     int right_tw,
                                     List<User> req_dic,
                                     Map<Integer, User> all_req,
                                     int n_vehicles,
                                     int t_horizon,
                                     int round,
                                     int totalRounds) {
        //String str = String.format("\n###### TW: [%s - %s] ###############################################", config.Config.sec2TStamp(left_tw), config.Config.sec2TStamp(right_tw));
        String str = String.format("\n###### TW: [%s - %s] ###############################################", Config.sec2Datetime(left_tw), Config.sec2Datetime(right_tw));
        //str = str + String.format("\n// PK delay: %d  //////  Trip delay: %d",max_pk_time, max_trip_time);
        str = str + String.format("\n||    Start: %10s  ||    Duration: %4s s", String.valueOf(start_time), String.valueOf(duration));
        str = str + String.format("\n|| Vehicles: %10s  ||  T. Horizon: %4s s", String.valueOf(n_vehicles), String.valueOf(t_horizon));
        str = str + String.format("\n||    Round: %10s  ||    Requests: %4s (TW) /%3s (Total)", String.valueOf(round + "/" + totalRounds), String.valueOf(req_dic != null ? req_dic.size() : 0), all_req.size());

        str = str + "\n################################################################################";
        return str;
    }


    public static String getVehicleInfo(List<Vehicle> vehicles,
                                        int currentTime,
                                        boolean showIdle,
                                        boolean showEnRoute,
                                        boolean showOrigin) {

        StringBuffer str = new StringBuffer();

        List<Vehicle> idle = new ArrayList<>();

        List<Vehicle> enroute = new ArrayList<>();

        List<Vehicle> origin = new ArrayList<>();

        for (Vehicle v : vehicles) {

            // If vehicle is not empty
            if (!v.getVisit().getSequenceVisits().isEmpty()) {

                // If there are passengers inside vehicle
                if (showEnRoute) {
                    enroute.add(v);
                }

            } else {

                // if current node is origin
                if (v.getLastVisitedNode() instanceof NodeOrigin && showOrigin) {

                    // Add origin
                    origin.add(v);

                } else if ((v.getLastVisitedNode() instanceof NodeStop || v.getLastVisitedNode() instanceof NodeTargetRebalancing) && showIdle) {
                    idle.add(v);
                }
            }
        }

        if (showEnRoute && !enroute.isEmpty()) {
            str.append("\n>>>>>>> EN-ROUTE >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");

            Collections.sort(enroute);

            for (Vehicle v : enroute) {
                str.append(String.format("\n %s", v.getInfo()));
            }
        }


        if (showIdle && !idle.isEmpty()) {
            str.append("\n>>>>>>> IDLE >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
            Collections.sort(idle);

            for (Vehicle v : idle) {
                str.append(String.format("\n %s", v.getInfo()));
            }

        }

        if (showOrigin && !idle.isEmpty()) {
            Collections.sort(origin);
            str.append("\n>>>>>>> ORIGIN >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");

            for (Vehicle v : origin) {
                str.append(String.format("\n %s", v.getInfo()));
            }
        }

        str.append("\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");

        return str.toString();
    }


    public static void openResultFile(int nOfVehicles,
                                      int maxNumberOfTrips,
                                      int vehicleCapacity,
                                      int[] vehicleOccupancy) {

        // File path
        Path outputFile = Paths.get(String.format("V%4d-%4d_R%4d.csv", nOfVehicles, vehicleCapacity, maxNumberOfTrips));

        BufferedWriter writer;
        try {
            writer = Files.newBufferedWriter(outputFile);

            List<String> headerOccupancy = new ArrayList<>();
            for (int i = 0; i < vehicleOccupancy.length; i++) {
                headerOccupancy.add(String.valueOf(i));
            }

            String[] a = headerOccupancy.toArray(new String[0]);

            CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(a));
            csvPrinter.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    public static String printJourneys(List<Vehicle> vd) {
        String str = ("\n######### JOURNEYS #########################################");
        for (Vehicle v : vd) {
            str += v.getJourneyInfo();
        }

        return str;
    }
}
