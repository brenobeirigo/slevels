package model.node;

import simulation.Simulation;

public class NodePK extends Node {

    public NodePK(int id_network, double lat, double lon, int tripId, int earliest, int latest, int load) {
        super(tripId, id_network, lat, lon, earliest, latest);
        this.load = load;
        this.tripId = tripId;
        this.delay = null;
        this.departure = null;
        this.arrival = null;
        this.arrivalSoFar = null;

        // If reservation-based, earliest time can be in the future
        this.earliestDeparture = Math.max(earliest, Simulation.rightTW);

        // User is somewhere where vehicles cannot reach fast enough. Increase hotness to attract vehicles.
        this.increaseHotness();
    }

    @Override
    public String toString() {
        return String.format("%10s", "PK" + String.valueOf(tripId));
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
                "    }", "#0e8b07", "small", "circle", this.getLon(), this.getLat());

        return s;
    }

    @Override
    public String getType() {
        return "pickup";
    }
}
