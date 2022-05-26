package dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import model.node.NodeNetwork;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;

public class ParseJsonUtil {

    /**
     * Parse json of containing list of nodes. E.g., {"nodes"=[{"id"=1, "x": 45.56, "y":75.43}]}
     * <p>
     * json list can be pulled from REST server using address localhost:5000/nodes
     *
     * @param json
     * @return
     */
    public static Map<Integer, NodeNetwork> getNodeDictionaryFromJsonString(String json) {

        JsonObject jsonObject = new JsonParser().parse(json).getAsJsonObject();
        Map<Integer, NodeNetwork> nodes = new HashMap<>();
        JsonArray arr = jsonObject.getAsJsonArray("nodes");
        for (int i = 0; i < arr.size(); i++) {
            int id = arr.get(i).getAsJsonObject().get("id").getAsShort();
            double x = arr.get(i).getAsJsonObject().get("x").getAsDouble();
            double y = arr.get(i).getAsJsonObject().get("y").getAsDouble();

            //TODO center node is centroid
            // Closest center that can reach node at each step (e.g., 30, 60, 90, ..., 600)
            //JsonObject centers = arr.get(i).getAsJsonObject().get("step_center").getAsJsonObject();
            Map<Integer, Integer> centerNodeId = new HashMap<>();
//            for (Map.Entry<String, JsonElement> entry : centers.entrySet()) {
//                centerNodeId.put(Integer.valueOf(entry.getKey()), Integer.valueOf(entry.getKey()));
//            }
            Point2D point = new Point2D.Double(x, y);
            NodeNetwork node = new NodeNetwork(id, point, centerNodeId);

            nodes.put(id, node);
        }
        System.out.printf("# %d nodes read.\n", nodes.size());
        return nodes;
    }
}
