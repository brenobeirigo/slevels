package dao;

import model.node.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class ServerUtil {

    //SERVER
    private static final String ADDRESS_SERVER = "http://localhost:5000";
    private static final String ADDRESS_ALLNODES = "http://localhost:5000/nodes";
    private static String restPoint = ADDRESS_SERVER + "/point_style/%d/%%23%s/%s/%s";
    private static String restLine = ADDRESS_SERVER + "/linestring_style/%d/%d/%%23%s/%s/%s";
    private static Map<String, HashMap<String, String>> styleNode = new HashMap<String, HashMap<String, String>>() {{

        put("pickup_point",
                new HashMap<String, String>() {{
                    put("marker-color", "00b20b");
                    put("marker-size", "small");
                    put("marker-symbol", "circle");
                }});
        put("origin_point",
                new HashMap<String, String>() {{
                    put("marker-color", "0000FF");
                    put("marker-size", "small");
                    put("marker-symbol", "circle");
                }});
        put("destination_point",
                new HashMap<String, String>() {{
                    put("marker-color", "FF0000");
                    put("marker-size", "small");
                    put("marker-symbol", "circle");
                }});

        put("middle_point",
                new HashMap<String, String>() {{
                    put("marker-color", "c1c1c1");
                    put("marker-size", "small");
                    put("marker-symbol", "circle");
                }});

        put("vehicle",
                new HashMap<String, String>() {{
                    put("marker-color", "FF0000");
                    put("marker-size", "small");
                    put("marker-symbol", "circle");
                }});

    }};
    private static Map<String, HashMap<String, String>> styleEdge = new HashMap<String, HashMap<String, String>>() {{

        put("cruising",
                new HashMap<String, String>() {{
                    put("stroke", "EEEEEE");
                    put("stroke-width", "2.0");
                    put("stroke-opacity", "1.0");
                }});

        put("rebalancing",
                new HashMap<String, String>() {{
                    put("stroke", "CCCCCC");
                    put("stroke-width", "2.0");
                    put("stroke-opacity", "1.0");
                }});

        put("s1",
                new HashMap<String, String>() {{
                    put("stroke", "6d6d6d");
                    put("stroke-width", "2.0");
                    put("stroke-opacity", "1.0");
                }});

        put("s2",
                new HashMap<String, String>() {{
                    put("stroke", "416bf4");
                    put("stroke-width", "2.0");
                    put("stroke-opacity", "1.0");
                }});

        put("s3",
                new HashMap<String, String>() {{
                    put("stroke", "f441c1");
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

        String lineType = "s1";

        String rest = String.format(
                restLine,
                o.getNetworkId(),
                d.getNetworkId(),
                styleEdge.get(lineType).get("stroke"),
                styleEdge.get(lineType).get("stroke-width"),
                styleEdge.get(lineType).get("stroke-opacity"));

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
                "origin_point" : "stop_point";

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
        String rest = String.format("http://localhost:5000/sp/%d/%d", o, d);
        ArrayList<Short> list_ids = null;
        list_ids = (ArrayList) Arrays.asList(requestTo(rest).split(";")).stream().map(n -> Short.valueOf(n)).collect(Collectors.toList());
        return list_ids;
    }

    /**
     * Get all nodes from transportation network (ids and coordinates)
     *
     * @return String with json of format {"nodes"=[{"id"=1, "x": 45.56, "y":75.43}]}
     */
    public static String getNodeList() {
        return requestTo(ADDRESS_ALLNODES);
    }

    public void printGeoJsonJourney(List<Node> journey) {

        List<String> listFeatures = new ArrayList<>();
        for (int i = 0; i < journey.size() - 1; i++) {
            String o = getGeoJsonPointfromServer(journey.get(i));
            String d = getGeoJsonPointfromServer(journey.get(i + 1));
            String edge = getGeoJsonSPBetweenODfromServer(journey.get(i), journey.get(i + 1));
            listFeatures.add(o);
            listFeatures.add(d);
            listFeatures.add(edge);
        }

        System.out.println(String.join(",", listFeatures));
    }


}
