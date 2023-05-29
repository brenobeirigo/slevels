//package result;
//
//import config.InstanceConfig;
//import dao.FileUtil;
//import dao.Logging;
//import model.Vehicle;
//import model.node.Node;
//import simulation.Environment;
//import visualization.GeoJsonUtil;
//
//import java.awt.geom.Point2D;
//import java.io.BufferedWriter;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.ArrayList;
//import java.util.Date;
//import java.util.List;
//import java.util.Set;
//import java.util.stream.Collectors;
//
//public class GeojsonUtil {
//    private final Environment env;
//
//    /**
//     * Get GeoJson of all points that compose a linestring (sequence of Point2D).
//     *
//     * @param linestring (list of points Point2D)
//     * @return A GeoJson object containing all points
//     */
//    public static String allPoints(List<Point2D> linestring) {
//        StringBuilder b = new StringBuilder();
//        b.append("{\n" +
//                "  \"type\": \"FeatureCollection\",\n" +
//                "  \"features\": [");
//        b.append(linestring.stream().map(Node::getGeoJson).collect(Collectors.joining(",")));
//        b.append("]}");
//        return b.toString();
//    }
//
//    public GeojsonUtil(Environment env){
//        this.env = env;
//    }
//
//    /**
//     * Get GeoJson linestring given list of coordinates.
//     *
//     * @param line List of Point2D coordinates
//     * @return GeoJson object
//     */
//    public static String getLinestring(List<Point2D> line) {
//
//        StringBuilder b = new StringBuilder();
//        b.append("{\n" +
//                "      \"type\": \"Feature\",\n" +
//                "      \"properties\": {},\n" +
//                "      \"geometry\":{\n" +
//                "  \"type\": \"LineString\",\n" +
//                "  \"coordinates\": [\n");
//
//        b.append(line.stream()
//                .map(p -> String.format("[%f,%f]",
//                        p.getX(),
//                        p.getY()))
//                .collect(Collectors.joining(",")));
//
//        b.append("]}}");
//
//        return b.toString();
//    }
//
//
//
//    public String getJourneyInfo(Vehicle vehicle) {
//
//        StringBuilder str = new StringBuilder();
//        StringBuilder strCoord = new StringBuilder();
//        str.append("\n########################################################################################");
//        str.append("\n" + env.getStats(vehicle));
//        str.append("\n########################################################################################");
//
//
//        List<String> coordJourney = new ArrayList<>();
//
//        for (int i = 0; i < vehicle.getJourney().size() - 1; i++) {
//            int fromId = vehicle.getJourney().get(i).getNetworkId();
//            coordJourney.add(String.format("[%f, %f]", env.getNetwork().getLon(vehicle.getJourney().get(i).getNetworkId()), env.getNetwork().getLat(vehicle.getJourney().get(i).getNetworkId())));
//            int toId = vehicle.getJourney().get(i + 1).getNetworkId();
//            coordJourney.add(String.format("[%f, %f]", env.getNetwork().getLon(vehicle.getJourney().get(i + 1).getNetworkId()), env.getNetwork().getLat(vehicle.getJourney().get(i + 1).getNetworkId())));
//            int dist = env.getNetwork().getDistSec(fromId, toId);
//            int waiting = vehicle.getJourney().get(i + 1).getArrival() - dist - vehicle.getJourney().get(i).getDeparture();
//            str.append("\n" + env.getNetwork().getInfo(vehicle.getJourney().get(i)));
//            str.append(String.format("\nTravel time: %7s", dist));
//            str.append(String.format("\n    Waiting: %7s", waiting));
//
//        }
//
//        // Print last node
//        str.append("\n" + env.getNetwork().getInfo(vehicle.getJourney().get(vehicle.getJourney().size() - 1)));
//        String journeyCoord = String.join(",", coordJourney);
//        str.append("\n Path: [" + journeyCoord + "]");
//        return str.toString();
//    }
//
//
//    public String printJourneys(Set<Vehicle> vd) {
//        String str = ("\n######### JOURNEYS #########################################");
//        for (Vehicle v : vd) {
//            str += getJourneyInfo(v);
//        }
//
//        return str;
//    }
//
//    public void printAllJourneys() {
//
//        Logging.logger.info(printJourneys(env.getListVehicles()));
//        Logging.logger.info("GEOJSON DATA");
//        // Dao dao = Dao.getInstance();
//        for (Vehicle v : env.getListVehicles()) {
//            Logging.logger.info("{}->{}", v, v.getOrigin().getNetworkId());
//            //Logging.logger.info(v.getInfo());
//
//            //Logging.logger.info(v.getJourneyInfo());
//            //ServerUtil.printGeoJsonJourney(v, Environment.);
//        }
//    }
//
//    /**
//     * Save a geojson route for every vehicle in vehicle list
//     *
//     * @param vehicleList
//     */
//    public void saveGeoJsonPerVehicle(Date earliestDatetime, Set<Vehicle> vehicleList) {
//        Logging.logger.info("Saving geojson vehicle traces...");
//
//
//        // Create a folder for test case
//        String caseStudyPath = String.format("%s/%s",
//                InstanceConfig.getInstance().getGeojsonTrackFolder(),
//                this.testCaseName);
//        FileUtil.createDir(caseStudyPath);
//
//        Path outputGeoJsonFleet = Paths.get(
//                String.format("%s/fleetJourney.json",
//                        caseStudyPath
//                ));
//
//        List<String> geojsonList = new ArrayList();
//
//        for (Vehicle v : vehicleList) {
//
//            // Create vehicle geojson name
//            Path outputGeoJsonVehicle = Paths.get(
//                    String.format("%s/%s.geojson",
//                            caseStudyPath,
//                            v.toString().trim()
//                    ));
//            try {
//                BufferedWriter writer = Files.newBufferedWriter(outputGeoJsonVehicle);
//                String geojson = String.format("{\n" +
//                        "    \"type\": \"FeatureCollection\",\n" +
//                        "    \"features\": %s\n}", String.join(",\n", GeoJsonUtil.getJourneyComplete(earliestDatetime, v)));
//
//                String geojsonFleet = String.format(
//                        "    {\n" +
//                                "        \"id\": \"%s\"," +
//                                "        \"path\": %s\n    }", v, geojson);
//
//                geojsonList.add(geojsonFleet);
//
//                writer.write(geojson);
//                writer.close();
//
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//
//
//        try {
//            writer = Files.newBufferedWriter(outputGeoJsonFleet);
//            String geoJsonFleet = String.format("{\n" +
//                    "    \"routes\": [%s]\n}", String.join(",\n", geojsonList));
//            writer.write(geoJsonFleet);
//            writer.close();
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//    }
//}
