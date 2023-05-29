package visualization;

import com.fasterxml.jackson.annotation.JsonProperty;
import config.InstanceConfig;
import dao.Dao;
import dao.DateUtil;
import dao.FileUtil;
import dao.Logging;
import model.demand.User;
import model.Vehicle;
import model.network.TransportNetwork;
import model.node.Node;
import model.node.NodeDropoff;
import model.node.NodePickup;
import model.node.NodeTargetRebalancing;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import simulation.Environment;

import java.awt.geom.Point2D;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class GeoJsonUtil {

    public static final String OUTPUT_GEOJSON = "output_geojson";
    private Environment environment;

    public GeoJsonUtil(Environment environment){
        this.environment = environment;
    }
    public String getGeoJson(@JsonProperty("start_datetime") LocalDateTime earliestDatetime, Node n) {

        Point2D location = this.environment.getNetwork().getLocation(n.getNetworkId());

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
                DateUtil.sec2Datetime(earliestDatetime,n.getArrival()),
                n.getDeparture() == null ? null : String.format("\"%s\"" , DateUtil.sec2Datetime(earliestDatetime,n.getDeparture())),
                n.getDeparture() == null ? null : n.getDeparture() - n.getArrival(),
                DateUtil.sec2Datetime(earliestDatetime,n.getEarliest()),
                DateUtil.sec2Datetime(earliestDatetime,n.getLatest()),
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

    public String getJourneyInfo(Vehicle v, List<Node> journey, @JsonProperty("start_datetime") LocalDateTime earliestDatetime) {
        int load = 0;
        List<String> listFeatures = new ArrayList<>();
        List<String> listFeaturesLines = new ArrayList<>();

        for (int i = 0; i < journey.size() - 1; i++) {

            Node from = journey.get(i);
            Node to = journey.get(i + 1);
            load += from.getLoad();
            listFeatures.add(getGeoJson(earliestDatetime,from));
            String line = getGeoJson(earliestDatetime,from, to, v, load, 0);
            if (line != null)
                //listFeatures.add(line);
                listFeaturesLines.add(line);
        }

        listFeatures.add(getGeoJson(earliestDatetime,journey.get(journey.size() - 1)));

        return String.valueOf(listFeatures);
    }

    public String getJourneyComplete(@JsonProperty("start_datetime") LocalDateTime earliestDatetime, Vehicle v) {

        CircularFifoQueue<Node> journey = v.getJourney();

        List<String> listFeatures = new ArrayList<>();
        int load = 0;

        Set<Integer> singlePassengers = new HashSet<>();
        for (int i = 0; i < journey.size() - 1; i++) {


            Node from = journey.get(i);
            Node to = journey.get(i + 1);

            if (from instanceof NodePickup) {
                singlePassengers.add(from.getTripId());
            }

            if (from instanceof NodeDropoff) {
                singlePassengers.remove(from.getTripId());
            }

            Node previousFrom = null;
            //When rebalancing, the actual path skips the rebalancing node
            if (from instanceof NodeTargetRebalancing) {
                previousFrom = journey.get(i - 1);
                listFeatures.add(getGeoJson(earliestDatetime,from));
                String line = getGeoJson(earliestDatetime,previousFrom, to, v, load, 0);
                if (line != null)
                    listFeatures.add(line);
            } else {
                load += from.getLoad();
                listFeatures.add(getGeoJson(earliestDatetime,from));
                String line = getGeoJson(earliestDatetime,from, to, v, load, singlePassengers.size());
                if (line != null)
                    listFeatures.add(line);
            }
        }

        listFeatures.add(getGeoJson(earliestDatetime,journey.get(journey.size() - 1)));

        return String.valueOf(listFeatures);
    }

    public String getJourneyInfoLines(@JsonProperty("start_datetime") LocalDateTime earliestDatetime, Vehicle v, List<Node> journey) {

        List<String> listFeaturesLines = new ArrayList<>();
        int load = 0;
        int nPassengers = 0;

        for (int i = 0; i < journey.size() - 1; i++) {

            Node from = journey.get(i);
            Node to = journey.get(i + 1);


            load += from.getLoad();

            String line = getGeoJson(earliestDatetime,from, to, v, load, 0);
            if (line != null)
                //listFeatures.add(line);
                listFeaturesLines.add(line);
        }


        return String.valueOf(listFeaturesLines);
    }

    private String getGeoJson(@JsonProperty("start_datetime") LocalDateTime earliestDatetime, Node from, Node to, Vehicle v, int load, int numberOfSinglePassengers) {
        int fromId = from.getNetworkId();
        int toId = to.getNetworkId();

        int dist = this.environment.getNetwork().getDistSec(fromId, toId);
        int waiting = to.getArrival() - dist - from.getDeparture();

        List<String> listCoords = environment.getNetwork().getShortestPathLonLatBetween(fromId, toId);
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
                DateUtil.sec2TStamp(earliestDatetime, dist),
                DateUtil.sec2TStamp(earliestDatetime, waiting),
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

        b.append(line.stream()
                .map(p -> String.format("[%f,%f]",
                        this.environment.getNetwork().getLocation(p.getNetworkId()).getX(),
                        this.environment.getNetwork().getLocation(p.getNetworkId()).getY()))
                .collect(Collectors.joining(",")));

        b.append("]}}");

        return b.toString();
    }

    /**
     * Get GeoJson of all points that compose a linestring (sequence of Point2D).
     *
     * @param linestring (list of points Point2D)
     * @return A GeoJson object containing all points
     */
    public static String allPoints(List<Point2D> linestring) {
        StringBuilder b = new StringBuilder();
        b.append("{\n" +
                "  \"type\": \"FeatureCollection\",\n" +
                "  \"features\": [");
        b.append(linestring.stream().map(Node::getGeoJson).collect(Collectors.joining(",")));
        b.append("]}");
        return b.toString();
    }
    

    /**
     * Get GeoJson linestring given list of coordinates.
     *
     * @param line List of Point2D coordinates
     * @return GeoJson object
     */
    public static String getLinestring(List<Point2D> line) {

        StringBuilder b = new StringBuilder();
        b.append("{\n" +
                "      \"type\": \"Feature\",\n" +
                "      \"properties\": {},\n" +
                "      \"geometry\":{\n" +
                "  \"type\": \"LineString\",\n" +
                "  \"coordinates\": [\n");

        b.append(line.stream()
                .map(p -> String.format("[%f,%f]",
                        p.getX(),
                        p.getY()))
                .collect(Collectors.joining(",")));

        b.append("]}}");

        return b.toString();
    }



    public String getJourneyInfo(Vehicle vehicle) {

        StringBuilder str = new StringBuilder();
        StringBuilder strCoord = new StringBuilder();
        str.append("\n########################################################################################");
        str.append("\n" + environment.getStats(vehicle));
        str.append("\n########################################################################################");


        List<String> coordJourney = new ArrayList<>();

        for (int i = 0; i < vehicle.getJourney().size() - 1; i++) {
            int fromId = vehicle.getJourney().get(i).getNetworkId();
            coordJourney.add(String.format("[%f, %f]", environment.getNetwork().getLon(vehicle.getJourney().get(i).getNetworkId()), environment.getNetwork().getLat(vehicle.getJourney().get(i).getNetworkId())));
            int toId = vehicle.getJourney().get(i + 1).getNetworkId();
            coordJourney.add(String.format("[%f, %f]", environment.getNetwork().getLon(vehicle.getJourney().get(i + 1).getNetworkId()), environment.getNetwork().getLat(vehicle.getJourney().get(i + 1).getNetworkId())));
            int dist = environment.getNetwork().getDistSec(fromId, toId);
            int waiting = vehicle.getJourney().get(i + 1).getArrival() - dist - vehicle.getJourney().get(i).getDeparture();
            str.append("\n" + environment.getNetwork().getInfo(vehicle.getJourney().get(i)));
            str.append(String.format("\nTravel time: %7s", dist));
            str.append(String.format("\n    Waiting: %7s", waiting));

        }

        // Print last node
        str.append("\n" + environment.getNetwork().getInfo(vehicle.getJourney().get(vehicle.getJourney().size() - 1)));
        String journeyCoord = String.join(",", coordJourney);
        str.append("\n Path: [" + journeyCoord + "]");
        return str.toString();
    }


    public String printJourneys(Set<Vehicle> vd) {
        String str = ("\n######### JOURNEYS #########################################");
        for (Vehicle v : vd) {
            str += getJourneyInfo(v);
        }

        return str;
    }

//    public void printAllJourneys() {
//
//        Logging.logger.info(printJourneys(environment.getListVehicles()));
//        Logging.logger.info("GEOJSON DATA");
//        // Dao dao = Dao.getInstance();
//        for (Vehicle v : environment.getListVehicles()) {
//            Logging.logger.info("{}->{}", v, v.getOrigin().getNetworkId());
//            //Logging.logger.info(v.getInfo());
//
//            //Logging.logger.info(v.getJourneyInfo());
//            //ServerUtil.printGeoJsonJourney(v, Environment.);
//        }
//    }

    /**
     * Save a geojson route for every vehicle in vehicle list
     *
     * @param vehicleList
     */
    public void saveGeoJsonPerVehicle(@JsonProperty("start_datetime") LocalDateTime earliestDatetime, Set<Vehicle> vehicleList, String testCaseName) {
        Logging.logger.info("Saving geojson vehicle traces...");


        // Create a folder for test case
        String caseStudyPath = String.format("%s/%s",
                InstanceConfig.getInstance().getGeojsonTrackFolder(),
                testCaseName);
        FileUtil.createDir(caseStudyPath);

        Path outputGeoJsonFleet = Paths.get(
                String.format("%s/fleetJourney.json",
                        caseStudyPath
                ));

        List<String> geojsonList = new ArrayList();

        for (Vehicle v : vehicleList) {

            // Create vehicle geojson name
            Path outputGeoJsonVehicle = Paths.get(
                    String.format("%s/%s.geojson",
                            caseStudyPath,
                            v.toString().trim()
                    ));
            try {
                BufferedWriter writer = Files.newBufferedWriter(outputGeoJsonVehicle);
                String geojson = String.format("{\n" +
                        "    \"type\": \"FeatureCollection\",\n" +
                        "    \"features\": %s\n}", String.join(",\n", getJourneyComplete(earliestDatetime, v)));

                String geojsonFleet = String.format(
                        "    {\n" +
                                "        \"id\": \"%s\"," +
                                "        \"path\": %s\n    }", v, geojson);

                geojsonList.add(geojsonFleet);

                writer.write(geojson);
                writer.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        try {
            BufferedWriter writer  = Files.newBufferedWriter(outputGeoJsonFleet);
            String geoJsonFleet = String.format("{\n" +
                    "    \"routes\": [%s]\n}", String.join(",\n", geojsonList));
            writer.write(geoJsonFleet);
            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public String allPoints(Collection<User> values) {
        StringBuilder b = new StringBuilder();
        b.append("{\n" +
                "  \"type\": \"FeatureCollection\",\n" +
                "  \"features\": [");
        for (User u : values) {
            b.append(u.getNodePk().toGeoJson(this.environment));
            b.append(",");
            b.append(u.getNodeDp().toGeoJson(this.environment));
            b.append(",");

        }
        b.append("]}");
        return b.toString();
    }

}
