package dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.geometry.Point2D;

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
    public static Map<Integer, Point2D> getNodeDictionary(String json) {
        JsonObject jsonObject = new JsonParser().parse(json).getAsJsonObject();
        Map<Integer, Point2D> nodes = new HashMap<>();
        JsonArray arr = jsonObject.getAsJsonArray("nodes");
        for (int i = 0; i < arr.size(); i++) {
            int id = arr.get(i).getAsJsonObject().get("id").getAsInt();
            double x = arr.get(i).getAsJsonObject().get("x").getAsDouble();
            double y = arr.get(i).getAsJsonObject().get("y").getAsDouble();
            nodes.put(id, new Point2D(x, y));
        }
        return nodes;
    }
}
