package dao;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import model.Vehicle;
import model.learn.FleetStateActionSpaceObject;
import model.node.*;
import org.springframework.web.util.UriUtils;
import visualization.GeoJsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ServerUtil {

    //SERVER
    public String ADDRESS_SERVER;
    private String ADDRESS_ALLNODES = "%s/nodes/GPS";
    private String restPoint = "%s/point_style/%d/%%23%s/%s/%s";
    private String restLine = "%s/linestring_style/%d/%d/%%23%s/%s/%s";
    private String restShortestPath = "%s/sp/%d/%d";
    private String restSmoothShortestPath = "%s/sp_smooth/%d/%d";
    private String restSmoothDurations = "%s/sp_smooth/%d/%d/%d";
    private String restShortestPathCoords = "%s/sp_coords/%d/%d";
    private String restPredictPostDecisionSpace = "%s/predict/%s";
    private String restPredictPostDecisionSpace2 = "%s/predict_detail/%s";
    private String restSaveModelFileName = "%s/savemodel/%s";
    private String restSaveModelAt = "%s/savemodelat/%s";
    private String restLoadModelFileName = "%s/loadmodel/%s";
    private String restLoadModelAt = "%s/loadmodelat/%s";
    private String restTrackExp = "%s/trackexpat/%s";

    private String restCanReachSet = "%s/can_reach/%d/%d";
    //http://TUD256023.tudelft.net:4999/sp_coords/1/2


    public ServerUtil(String ADDRESS_SERVER) {
        this.ADDRESS_SERVER = ADDRESS_SERVER;
    }

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

    public static String postJsonFileToURL(String filepath, String URL) {
        String response = null;

        try {
            HttpURLConnection con = getHttpURLConnection(URL);
            postFile(filepath, con);
            response = extractResponseFromUrlConnection(con);

        } catch (IOException e) {
            e.printStackTrace();
        }

        return response;
    }


    public static String postJsonObjectToURL(Object obj, String URL) {
        String response = null;

        try {
            HttpURLConnection con = getHttpURLConnection(URL);
            postObject(obj, con);
            response = extractResponseFromUrlConnection(con);

        } catch (IOException e) {
            e.printStackTrace();
        }

        return response;
    }

    private static void postFile(String filepath, HttpURLConnection con) throws IOException {
        try (OutputStream os = con.getOutputStream()) {
            byte[] input = Files.readAllBytes(Path.of(filepath));
            os.write(input, 0, input.length);
        }
    }

    private static void postObject(Object object, HttpURLConnection con) throws IOException {

        try (OutputStream os = con.getOutputStream()) {
            Gson gson = new GsonBuilder().serializeNulls().create();
            byte[] input = gson.toJson(object).getBytes();
            os.write(input, 0, input.length);
        }
    }

    public static HttpURLConnection getHttpURLConnection(String serverURL) throws IOException {
        URL url = new URL(serverURL);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json; utf-8");
        con.setRequestProperty("Accept", "application/json");
        con.setDoOutput(true);
        return con;
    }

    public static String extractResponseFromUrlConnection(HttpURLConnection con) throws IOException {
        String r = null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            r = response.toString();
        }
        return r;
    }

//    public static String postJsonAndReadResponse(String json) {
//        String serviceUrl = "http://localhost:5001/predict/";
//        HttpClient client = HttpClient.newHttpClient();
//        HttpRequest request = HttpRequest.newBuilder()
//                .uri(URI.create(serviceUrl))
//                .POST(HttpRequest.BodyPublishers.ofString(json))
//                .build();
//
//        HttpResponse<String> response = null;
//        try {
//            response = client.send(request, HttpResponse.BodyHandlers.ofString());
//            return response.body();
//        } catch (InterruptedException | IOException e) {
//            e.printStackTrace();
//        }
//        return null;
//    }

    /**
     * Make request to REST server.
     *
     * @param address Request url. E.g., localhost:5000/linestring/1/2
     * @return Server response
     */
    public String requestTo(String address) {

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
    public String getGeoJsonSPBetweenODfromServer(Node o, Node d) {
        String lineType = "servicing";
        System.out.println(o.getClass().getName() + " - " + d.getClass().getName());
        if (d instanceof NodeTargetRebalancing) {
            lineType = "rebalancing";
        } else if ((o instanceof NodeOrigin || o instanceof NodeMiddle || o instanceof NodeStop) && d instanceof NodePK) {
            lineType = "cruising";
        }

        String rest = String.format(
                restLine,
                this.ADDRESS_SERVER,
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
    public String getGeoJsonPointfromServer(Node n) {

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
    public ArrayList<Integer> getShortestPathBetween(int o, int d) {
        //System.out.println(o + " ->" + d);
        String rest = String.format(restShortestPath, this.ADDRESS_SERVER, o, d);
        ArrayList<Integer> list_ids = null;
        list_ids = (ArrayList) Arrays.asList(requestTo(rest).split(";")).stream().map(n -> Integer.valueOf(n)).collect(Collectors.toList());
        return list_ids;
    }


    /**
     * Get the id path between OD (included) from REST server.
     *
     * @param n              Reachable node id
     * @param maxTripTimeSec Destination node id
     * @return list of ids
     */
    public ArrayList<Integer> getAllCanReachNode(int n, int maxTripTimeSec) {
        String rest = String.format(restCanReachSet, this.ADDRESS_SERVER, n, maxTripTimeSec);
        return (ArrayList<Integer>) Arrays.stream(requestTo(rest).split(";")).map(Integer::valueOf).collect(Collectors.toList());
    }


    /**
     * Get the id path between OD (included) from REST server.
     * Also includes the node ids needed to create a smooth path.
     *
     * @param o Origin node id
     * @param d Destination node id
     * @return list of ids
     */
    public ArrayList<Integer> getSmoothShortestPathBetween(int o, int d) {
        String rest = String.format(restShortestPath, this.ADDRESS_SERVER, o, d);
        ArrayList<Integer> list_ids = null;
        list_ids = (ArrayList) Arrays.asList(requestTo(rest).split(";")).stream().map(n -> Integer.valueOf(n)).collect(Collectors.toList());
        return list_ids;
    }

    /**
     * Get the id path between OD (included) from REST server.
     *
     * @param o Origin node id
     * @param d Destination node id
     * @return list of ids
     */
    public ArrayList<String> getRESTShortestPathCoordsBetween(int o, int d) {
        String rest = String.format(restShortestPathCoords, this.ADDRESS_SERVER, o, d);
        ArrayList<String> listCoords = null;
        listCoords = (ArrayList) Arrays.asList(requestTo(rest).split(";")).stream().collect(Collectors.toList());
        return listCoords;
    }

    /**
     * Map o vfs per vehicle
     * @param obj
     * @return Value functions
     */
    public Map<Integer, List<Double>> getPredictionsFromDecisionSpace(FleetStateActionSpaceObject obj) {
        String url = String.format(restPredictPostDecisionSpace, this.ADDRESS_SERVER, "");
        String response = Dao.getInstance().getServer().postJsonObjectToURL(obj, url);

        Type t = new TypeToken<Map<Integer, List<Double>>>() {
        }.getType();

        Gson g = new Gson();
        return g.fromJson(response, t);
    }

    /**
     * Map o vfs per vehicle
     * @param obj
     * @return Value functions
     */
    public Map<Integer, List<Double>> getPredictionsFromDecisionSpace2(FleetStateActionSpaceObject obj) {
        String url = String.format(restPredictPostDecisionSpace2, this.ADDRESS_SERVER, "");
        String response = Dao.getInstance().getServer().postJsonObjectToURL(obj, url);

        Type t = new TypeToken<Map<Integer, List<Double>>>() {
        }.getType();

        Gson g = new Gson();
        return g.fromJson(response, t);
    }

    /**
     * Values separated by ";"
     * @param stateSpaceJsonFilepath
     * @return Value functions
     */
    public ArrayList<Double> getPredictionsFromJsonFile(String stateSpaceJsonFilepath) {
        String url = String.format(restPredictPostDecisionSpace, this.ADDRESS_SERVER, "");
        String response = ServerUtil.postJsonFileToURL(stateSpaceJsonFilepath, url);
        ArrayList<Double> valueFunctionArray = null;
        valueFunctionArray = (ArrayList<Double>) Arrays.stream(response.split(";"))
                .map(Double::valueOf)
                .collect(Collectors.toList());
        return valueFunctionArray;
    }

    /**
     * Values separated by ";"
     * @param obj
     * @return Value functions
     */
    public ArrayList<Double> getPredictionsFrom(FleetStateActionSpaceObject obj) {
        String url = String.format(restPredictPostDecisionSpace, this.ADDRESS_SERVER, "");
        Gson gson = new Gson();
        gson.toJson(obj);
        String response = ServerUtil.postJsonObjectToURL(obj, url);
        ArrayList<Double> valueFunctionArray = null;
        valueFunctionArray = (ArrayList<Double>) Arrays.stream(response.split(";"))
                .map(Double::valueOf)
                .collect(Collectors.toList());
        return valueFunctionArray;
    }

    public List<String> getShortestPathCoordsBetween(int o, int d) {
        String rest = String.format(restShortestPathCoords, this.ADDRESS_SERVER, o, d);
        List<String> listCoords = null;
        listCoords = Dao.getInstance().getLonLatList(Dao.getInstance().getShortestPathBetween(o, d));

        return listCoords;
    }

    /**
     * Get all nodes from transportation network (ids and coordinates)
     *
     * @return String with json of format {"nodes"=[{"id"=1, "x": 45.56, "y":75.43}]}
     */
    public String getNodeList() {
        return requestTo(String.format(ADDRESS_ALLNODES, this.ADDRESS_SERVER));
    }


    public void printGeoJsonJourney(Vehicle v) {
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


    public String saveModelWithLabel(String fileName) {
        return requestTo(String.format(restSaveModelFileName, this.ADDRESS_SERVER, fileName));
    }

    public String saveModelAt(String path) {
        String fileName = UriUtils.encode(path, "UTF-8");
        return requestTo(String.format(restSaveModelAt, this.ADDRESS_SERVER, fileName));
    }

    public String loadModelAt(String path) {
        String fileName = UriUtils.encode(path, "UTF-8");
        return requestTo(String.format(restLoadModelAt, this.ADDRESS_SERVER, fileName));
    }

    public String loadModelWithLabel(String fileName) {
        return requestTo(String.format(restLoadModelFileName, this.ADDRESS_SERVER, fileName));
    }

    public String createExperiencesFolderAt(String experiencesFolder) {
        String fileName = UriUtils.encode(experiencesFolder, "UTF-8");
        return requestTo(String.format(restTrackExp, this.ADDRESS_SERVER, fileName));
    }

}
