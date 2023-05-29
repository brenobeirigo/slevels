package model.node;

import simulation.Environment;

import java.time.LocalDateTime;

public class NodeDropoff extends Node {


    public NodeDropoff(int networkId, double lat, double lon, int tripId, int earliest, int latest, int load) {
        super(tripId + Node.MAX_NUMBER_NODES, networkId, lat, lon, earliest, latest);
        this.load = load;
        this.tripId = tripId;
        this.delay = null;
        this.departure = null;
        this.arrival = null;
        this.arrivalSoFar = null;
        this.earliestDeparture = earliest;
//        this.earliestDeparture = Math.max(earliest, Environment.currentTime);

    }

    @Override
    public String toString() {
        return String.format("%10s", "DP" + String.valueOf(this.tripId));
    }

    public String toGeoJson(Environment env) {

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
                "    }", "#ea0000", "small", "circle", env.getNetwork().getLon(getNetworkId()), env.getNetwork().getLat(getNetworkId()));

        return s;
    }

    @Override
    public String getType() {
        return "destination";
    }
}
