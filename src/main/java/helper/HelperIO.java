package helper;

import config.Config;
import model.User;
import model.Vehicle;
import model.node.NodeOrigin;
import model.node.NodeStop;
import model.node.NodeTargetRebalancing;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class HelperIO {

    public static Map<String, FileOutputStream> logs = new HashMap<>();

    public static String getHeaderTW(int start_time,
                                     int duration,
                                     int left_tw,
                                     int right_tw,
                                     Set<User> req_dic,
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

    public static String readFileFromPath(String pathFile){
        String contentFile = null;
        try {
            contentFile = Files.readString(Paths.get(pathFile));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return contentFile;
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

        List<Vehicle> rebalancing = new ArrayList<>();

        for (Vehicle v : vehicles) {

            // If vehicle is not empty
            try{
                if (v.getVisit()!=null) {

                    // If there are passengers inside vehicle
                    if (showEnRoute) {
                        if (v.isRebalancing())
                            rebalancing.add(v);
                        else
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
            }catch (Exception e){
                System.out.println("AAAAAAAAAAAAAAAAaaaa" + v);
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

        if (showOrigin && origin.isEmpty()) {
            Collections.sort(origin);
            str.append("\n>>>>>>> ORIGIN >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");

            for (Vehicle v : origin) {
                str.append(String.format("\n %s", v.getInfo()));
            }
        }

        if (showOrigin && !rebalancing.isEmpty()) {
            Collections.sort(rebalancing);
            str.append("\n>>>>>>> REBALANCING >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");

            for (Vehicle v : rebalancing) {
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

    /**
     * Helper to save .csv files with data columns.
     *
     * Example:
     * >> t1 = 3
     * >> t2 = 1
     * >> saveDataWithHeaders("C:\\project\\time_comparison.csv", String.format("%d %d", t1, t2), "OLD NEW", true);
     *
     * Will generate .csv:
     * OLD NEW
     * 3 1
     *
     * @param fileName Where file is saved
     * @param line Data row to be saved
     * @param headers Headers of the .csv table
     * @param append If True, append new rows to file
     */
    public static void saveDataWithHeaders(String fileName, String line, String headers, boolean append){

        FileOutputStream fos;
        try {
            fos = logs.computeIfAbsent(fileName, f-> {
                FileOutputStream fo = null;
                try {
                    fo = new FileOutputStream(f, append);
                    fo.write(String.format("%s\r\n",headers).getBytes());
                    return fo;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return fo;
            });
            assert fos != null;
            fos.write(String.format("%s\r\n",line).getBytes());
        } catch (Exception e) {
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
