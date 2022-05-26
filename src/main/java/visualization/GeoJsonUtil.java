package visualization;

import config.Config;
import dao.Dao;
import dao.ServerUtil;
import model.User;
import model.Vehicle;
import model.node.Node;
import model.node.NodeDP;
import model.node.NodePK;
import model.node.NodeTargetRebalancing;

import java.awt.geom.Point2D;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class GeoJsonUtil {

    public static final String OUTPUT_GEOJSON = "output_geojson";

    public static String getGeoJson(Date earliestDatetime, Node n) {

        Point2D location = Dao.getInstance().getLocation(n.getNetworkId());

        /*
        Map<String, String> colors = new HashMap<>();

        colors.put("origin", "#ffffff");
        colors.put("pickup", "#00ff00");
        colors.put("destination", "#ff0000");
        colors.put("target", "#0000ff");
        colors.put("stop", "#7e7e7e");
        colors.put("middle", "#f4d742");

        String properties_placeholder =
                "        \"marker-color\": \"%s\",\n" +
                        "        \"marker-size\": \"%s\",\n" +
                        "        \"type\": \"%s\",\n";
        */
        //String properties = String.format(properties_placeholder, colors.get(n.getType()), "small", n.getType());

        User u = User.mapOfUsers.getOrDefault(n.getTripId(), null);
        String nodeInfo = String.format(
                "        \"type\": \"%s\",\n" +
                        "        \"arrival\": \"%s\",\n" +
                        "        \"departure\": %s,\n" +
                        "        \"duration\": %s,\n" +
                        "        \"earliest\": \"%s\",\n" +
                        "        \"latest\": \"%s\",\n" +
                        "        \"user_id\": \"%s\",\n" +
                        "        \"user_class\": \"%s\",\n" +
                        "        \"n_passengers\": %s,\n" +
                        "        \"network_id\": \"%s\",\n" +
                        "        \"id\": \"%s\"\n",
                n.getType(),
                Config.sec2Datetime(earliestDatetime,n.getArrival()),
                n.getDeparture() == null ? null : String.format("\"%s\"" ,Config.sec2Datetime(earliestDatetime,n.getDeparture())),
                n.getDeparture() == null ? null : n.getDeparture() - n.getArrival(),
                Config.sec2Datetime(earliestDatetime,n.getEarliest()),
                Config.sec2Datetime(earliestDatetime,n.getLatest()),
                u != null ? u.toString().trim() : "-",
                u != null ? u.getPerformanceClass() : "-",
                Math.abs(n.getLoad()),
                n.getNetworkId(),
                n.toString().trim());

        String s = String.format("{\n" +
                "      \"type\": \"Feature\",\n" +
                "      \"properties\": {\n" +
                "%s" +

                "      },\n" +
                "      \"geometry\": {\n" +
                "        \"type\": \"Point\",\n" +
                "        \"coordinates\": [\n" +
                "          %f,\n" +
                "          %f\n" +
                "        ]\n" +
                "      }\n" +
                "    }", nodeInfo, location.getX(), location.getY());

        return s;
    }

    public static String getJourneyInfo(Vehicle v, List<Node> journey, Date earliestDatetime) {
        int load = 0;
        List<String> listFeatures = new ArrayList<>();
        List<String> listFeaturesLines = new ArrayList<>();

        for (int i = 0; i < journey.size() - 1; i++) {

            Node from = journey.get(i);
            Node to = journey.get(i + 1);
            load += from.getLoad();
            listFeatures.add(GeoJsonUtil.getGeoJson(earliestDatetime,from));
            String line = GeoJsonUtil.getGeoJson(earliestDatetime,from, to, v, load, 0);
            if (line != null)
                //listFeatures.add(line);
                listFeaturesLines.add(line);
        }

        listFeatures.add(GeoJsonUtil.getGeoJson(earliestDatetime,journey.get(journey.size() - 1)));

        return String.valueOf(listFeatures);
    }

    public static String getJourneyComplete(Date earliestDatetime, Vehicle v) {

        List<Node> journey = v.getJourney();

        List<String> listFeatures = new ArrayList<>();
        int load = 0;

        Set<Integer> singlePassengers = new HashSet<>();
        for (int i = 0; i < journey.size() - 1; i++) {


            Node from = journey.get(i);
            Node to = journey.get(i + 1);

            if (from instanceof NodePK) {
                singlePassengers.add(from.getTripId());
            }

            if (from instanceof NodeDP) {
                singlePassengers.remove(from.getTripId());
            }

            Node previousFrom = null;
            //When rebalancing, the actual path skips the rebalancing node
            if (from instanceof NodeTargetRebalancing) {
                previousFrom = journey.get(i - 1);
                listFeatures.add(GeoJsonUtil.getGeoJson(earliestDatetime,from));
                String line = GeoJsonUtil.getGeoJson(earliestDatetime,previousFrom, to, v, load, 0);
                if (line != null)
                    listFeatures.add(line);
            } else {
                load += from.getLoad();
                listFeatures.add(GeoJsonUtil.getGeoJson(earliestDatetime,from));
                String line = GeoJsonUtil.getGeoJson(earliestDatetime,from, to, v, load, singlePassengers.size());
                if (line != null)
                    listFeatures.add(line);
            }
        }

        listFeatures.add(GeoJsonUtil.getGeoJson(earliestDatetime,journey.get(journey.size() - 1)));

        return String.valueOf(listFeatures);
    }

    public static String getJourneyInfoLines(Date earliestDatetime, Vehicle v, List<Node> journey) {

        List<String> listFeaturesLines = new ArrayList<>();
        int load = 0;
        int nPassengers = 0;

        for (int i = 0; i < journey.size() - 1; i++) {

            Node from = journey.get(i);
            Node to = journey.get(i + 1);


            load += from.getLoad();

            String line = GeoJsonUtil.getGeoJson(earliestDatetime,from, to, v, load, 0);
            if (line != null)
                //listFeatures.add(line);
                listFeaturesLines.add(line);
        }


        return String.valueOf(listFeaturesLines);
    }

    private static String getGeoJson(Date earliestDatetime, Node from, Node to, Vehicle v, int load, int numberOfSinglePassengers) {
        int fromId = from.getNetworkId();
        int toId = to.getNetworkId();

        int dist = Dao.getInstance().getDistSec(fromId, toId);
        int waiting = to.getArrival() - dist - from.getDeparture();

        List<String> listCoords = Dao.getInstance().getServer().getShortestPathCoordsBetween(fromId, toId);
        if (listCoords.size() == 1) {
            return null;
        }
        //TODO How to include trip durations? Smooth coordinates don't have ids
        //List<Short> arrayTripDurations = Dao.getArrayTravelDurationsBetweenInSeconds(from, to);


        String properties = String.format(
                "\"load\": %s, " +
                        "\"number_of_requests\": %s, " +
                        "\"capacity\": %s, " +
                        "\"distance\": \"%s\"," +
                        "\"wait\": \"%s\"," +
                        "\"distance_s\": %s," +
                        "\"from\": \"%s\"," +
                        "\"to\": \"%s\"",
                //"\"to\": \"%s\","+
                //"\"trip_durations\": %s",
                load,
                numberOfSinglePassengers,
                v.getCapacity(),
                Config.sec2TStamp(earliestDatetime, dist),
                Config.sec2TStamp(earliestDatetime, waiting),
                dist,
                from.getType(),
                to.getType());
        //to.getType(),
        //arrayTripDurations);

        String linestring = String.format("{\n" +
                "      \"type\": \"Feature\",\n" +
                "      \"properties\": {%s},\n" +
                "      \"geometry\": {\n" +
                "        \"type\": \"LineString\",\n" +
                "        \"coordinates\": %s\n" +
                "      }}", properties, listCoords);

        return linestring;
    }

    public static void saveGeoJson(String fileName, String data) {
        Path path = Paths.get(
                String.format("%s/%s.geojson", GeoJsonUtil.OUTPUT_GEOJSON, fileName));
        //Use try-with-resource to get auto-closeable writer instance
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getLinestringFromNodes(List<Node> line) {


        StringBuilder b = new StringBuilder();
        b.append("{\n" +
                "      \"type\": \"Feature\",\n" +
                "      \"properties\": {" +
                "\"stroke\": \"#ff0000\",\n" +
                "        \"stroke-width\": 2,\n" +
                "        \"stroke-opacity\": 1" +
                "},\n" +
                "      \"geometry\":{\n" +
                "  \"type\": \"LineString\",\n" +
                "  \"coordinates\": [\n");

        b.append(String.join(",", line.stream()
                .map(p -> String.format("[%f,%f]",
                        Dao.getInstance().getLocation(p.getNetworkId()).getX(),
                        Dao.getInstance().getLocation(p.getNetworkId()).getY()))
                .collect(Collectors.toList())));

        b.append("]}}");

        return b.toString();
    }


}
