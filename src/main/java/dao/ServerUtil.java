package dao;

import model.Vehicle;
import model.node.*;
import visualization.GeoJsonUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ServerUtil {

    //SERVER
    private static final String ADDRESS_SERVER = "http://TUD256023.tudelft.net:4999";
    private static final String ADDRESS_ALLNODES = ADDRESS_SERVER + "/nodes";
    private static String restPoint = ADDRESS_SERVER + "/point_style/%d/%%23%s/%s/%s";
    private static String restLine = ADDRESS_SERVER + "/linestring_style/%d/%d/%%23%s/%s/%s";
    private static String restShortestPath = ADDRESS_SERVER + "/sp/%d/%d";
    private static String restSmoothShortestPath = ADDRESS_SERVER + "/sp_smooth/%d/%d";
    private static String restSmoothDurations = ADDRESS_SERVER + "/sp_smooth/%d/%d/%d";
    private static String restShortestPathCoords = ADDRESS_SERVER + "/sp_coords/%d/%d";

    private static String restCanReachSet = ADDRESS_SERVER + "/can_reach/%d/%d";
    //http://TUD256023.tudelft.net:4999/sp_coords/1/2

    private static Map<String, HashMap<String, String>> styleNode = new HashMap<String, HashMap<String, String>>() {{

        put("pickup_point",
                new HashMap<String, String>() {{
                    put("marker-color", "00ff00");
                    put("marker-size", "small");
                    put("marker-symbol", "circle");
                }});
        put("origin_point",
                new HashMap<String, String>() {{
                    put("marker-color", "ffc700");
                    put("marker-size", "small");
                    put("marker-symbol", "circle");
                }});
        put("destination_point",
                new HashMap<String, String>() {{
                    put("marker-color", "ff0000");
                    put("marker-size", "small");
                    put("marker-symbol", "circle");
                }});

        put("middle_point",
                new HashMap<String, String>() {{
                    put("marker-color", "cccccc");
                    put("marker-size", "small");
                    put("marker-symbol", "circle");
                }});

        put("stop_point",
                new HashMap<String, String>() {{
                    put("marker-color", "444444");
                    put("marker-size", "small");
                    put("marker-symbol", "circle");
                }});

        put("target_point",
                new HashMap<String, String>() {{
                    put("marker-color", "000000");
                    put("marker-size", "small");
                    put("marker-symbol", "circle");
                }});

    }};
    private static Map<String, HashMap<String, String>> styleEdge = new HashMap<String, HashMap<String, String>>() {{

        put("cruising",
                new HashMap<String, String>() {{
                    put("stroke", "ff0000");
                    put("stroke-width", "2.0");
                    put("stroke-opacity", "1.0");
                }});

        put("rebalancing",
                new HashMap<String, String>() {{
                    put("stroke", "0000ff");
                    put("stroke-width", "2.0");
                    put("stroke-opacity", "1.0");
                }});

        put("servicing",
                new HashMap<String, String>() {{
                    put("stroke", "00ff00");
                    put("stroke-width", "2.0");
                    put("stroke-opacity", "1.0");
                }});
    }};

    /**
     * Make request to REST server.
     *
     * @param address Request url. E.g., localhost:5000/linestring/1/2
     * @return Server response
     */
    public static String requestTo(String address) {

        String result = null;

        try {

            URL url = new URL(address);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("Failed : HTTP Error code : "
                        + conn.getResponseCode());
            }
            InputStreamReader in = new InputStreamReader(conn.getInputStream());
            BufferedReader br = new BufferedReader(in);
            result = br.readLine();
        } catch (Exception e) {
            System.out.println(String.format("Requests %s - Exception in NetClientGet:- ", address) + e);
        } finally {
            return result;
        }

    }

    /**
     * Get the id path between OD (included) from REST server.
     *
     * @param o Origin node id
     * @param d Destination node id
     * @return list of ids
     */
    public static String getGeoJsonSPBetweenODfromServer(Node o, Node d) {
        String lineType = "servicing";
        System.out.println(o.getClass().getName() + " - " + d.getClass().getName());
        if (d instanceof NodeTargetRebalancing) {
            lineType = "rebalancing";
        } else if ((o instanceof NodeOrigin || o instanceof NodeMiddle || o instanceof NodeTargetRebalancing || o instanceof NodeStop) && d instanceof NodePK) {
            lineType = "cruising";
        }

        String rest = String.format(
                restLine,
                o.getNetworkId(),
                d.getNetworkId(),
                styleEdge.get(lineType).get("stroke"),
                styleEdge.get(lineType).get("stroke-width"),
                styleEdge.get(lineType).get("stroke-opacity"));

        System.out.println("Rest line:" + rest);
        String response = requestTo(rest);
        return response;
    }

    /**
     * Get GeoJson point from REST server.
     *
     * @param n Node with the point information.
     * @return
     */
    public static String getGeoJsonPointfromServer(Node n) {

        System.out.println(n);
        System.out.println(n.getNetworkId());
        String nodeType = n instanceof NodePK ?
                "pickup_point" : n instanceof NodeDP ?
                "destination_point" : n instanceof NodeMiddle ?
                "middle_point" : n instanceof NodeOrigin ?
                "origin_point" : n instanceof NodeTargetRebalancing ?
                "target_point" : "stop_point";

        String rest = String.format(
                restPoint,
                n.getNetworkId(),
                styleNode.get(nodeType).get("marker-color"),
                styleNode.get(nodeType).get("marker-size"),
                styleNode.get(nodeType).get("marker-symbol"));

        System.out.println(rest);
        String response = null;

        try {
            URL url = new URL(rest);//your url i.e fetch data from .
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("Failed : HTTP Error code : "
                        + conn.getResponseCode());
            }
            InputStreamReader in = new InputStreamReader(conn.getInputStream());
            BufferedReader br = new BufferedReader(in);
            response = br.readLine();

        } catch (Exception e) {
            System.out.println(String.format("%d - Exception in NetClientGet:- ", n) + e);
        }

        return response;
    }

    /**
     * Get the id path between OD (included) from REST server.
     *
     * @param o Origin node id
     * @param d Destination node id
     * @return list of ids
     */
    public static ArrayList<Short> getShortestPathBetween(int o, int d) {
        String rest = String.format(restShortestPath, o, d);
        ArrayList<Short> list_ids = null;
        list_ids = (ArrayList) Arrays.asList(requestTo(rest).split(";")).stream().map(n -> Short.valueOf(n)).collect(Collectors.toList());
        return list_ids;
    }


    /**
     * Get the id path between OD (included) from REST server.
     *
     * @param n              Reachable node id
     * @param maxTripTimeSec Destination node id
     * @return list of ids
     */
    public static ArrayList<Short> getAllCanReachNode(int n, int maxTripTimeSec) {
        String rest = String.format(restCanReachSet, n, maxTripTimeSec);
        return (ArrayList<Short>) Arrays.stream(requestTo(rest).split(";")).map(Short::valueOf).collect(Collectors.toList());
    }


    /**
     * Get the id path between OD (included) from REST server.
     * Also includes the node ids needed to create a smooth path.
     *
     * @param o Origin node id
     * @param d Destination node id
     * @return list of ids
     */
    public static ArrayList<Short> getSmoothShortestPathBetween(int o, int d) {
        String rest = String.format(restShortestPath, o, d);
        ArrayList<Short> list_ids = null;
        list_ids = (ArrayList) Arrays.asList(requestTo(rest).split(";")).stream().map(n -> Short.valueOf(n)).collect(Collectors.toList());
        return list_ids;
    }

    /**
     * Get the id path between OD (included) from REST server.
     *
     * @param o Origin node id
     * @param d Destination node id
     * @return list of ids
     */
    public static ArrayList<String> getShortestPathCoordsBetween(int o, int d) {
        String rest = String.format(restShortestPathCoords, o, d);
        ArrayList<String> listCoords = null;
        listCoords = (ArrayList) Arrays.asList(requestTo(rest).split(";")).stream().collect(Collectors.toList());
        return listCoords;
    }

    /**
     * Get all nodes from transportation network (ids and coordinates)
     *
     * @return String with json of format {"nodes"=[{"id"=1, "x": 45.56, "y":75.43}]}
     */
    public static String getNodeList() {
        return requestTo(ADDRESS_ALLNODES);
    }

    public static void printGeoJsonJourney(Vehicle v) {
        /*
        List<String> listFeatures = new ArrayList<>();
        System.out.println("size:"+ journey.size());
        if(journey.size() == 1){
            System.out.println(getGeoJsonPointfromServer(journey.get(0)));
            return;
        }

        //journey = journey.stream().filter(n -> n instanceof NodeDP).collect(Collectors.toList());

        for (int i = 0; i < journey.size() - 1; i++) {
            Node nodeO = journey.get(i);
            Node nodeD = journey.get(i + 1);

            String o = GeoJsonUtil.getGeoJson(nodeO);


            String edge = getGeoJsonSPBetweenODfromServer(nodeO, nodeD);
            listFeatures.add(o);
            listFeatures.add(edge);
        }

        String d = GeoJsonUtil.getGeoJson(journey.get(journey.size()-1));
        listFeatures.add(d);

        GeoJsonUtil.getJourneyInfo(journey);
        */
        //System.out.println("POINTS:" + String.join(",\n", GeoJsonUtil.getJourneyInfo(journey)));
        //System.out.println("LINES:" + String.join(",\n", GeoJsonUtil.getJourneyInfoLines(journey)));


        String geojson = String.format("{\n" +
                "    \"type\": \"FeatureCollection\",\n" +
                "    \"features\": %s\n}", String.join(",\n", GeoJsonUtil.getJourneyComplete(v)));

        GeoJsonUtil.saveGeoJson("teste", geojson);

        System.out.println("GEOJSON" + geojson);

    }


}
