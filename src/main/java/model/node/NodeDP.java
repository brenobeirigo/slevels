package model.node;

public class NodeDP extends Node {

    public NodeDP(int id_network, double lat, double lon, int tripId, int earliest, int latest, int load) {
        super(tripId + Node.MAX_NUMBER_NODES, id_network, lat, lon, earliest, latest, load);
        this.tripId = tripId;
    }

    @Override
    public String toString() {
        return String.format("%7s", "DP" + String.valueOf(this.tripId));
    }

    public String toGeoJson() {

        String s = String.format("{\n" +
                "      \"type\": \"Feature\",\n" +
                "      \"properties\": {\n" +
                "        \"marker-color\": \"%s\",\n" +
                "        \"marker-size\": \"%s\",\n" +
                "        \"marker-symbol\": \"%s\"\n" +
                "      },\n" +
                "      \"geometry\": {\n" +
                "        \"type\": \"Point\",\n" +
                "        \"coordinates\": [\n" +
                "          %f,\n" +
                "          %f\n" +
                "        ]\n" +
                "      }\n" +
                "    }", "#ea0000", "small", "circle", this.getLon(), this.getLat());

        return s;
    }

    @Override
    public String getType() {
        return "destination";
    }
}
