package model.node;

import simulation.Environment;

import java.time.LocalDateTime;

public class NodePickup extends Node {

    private LocalDateTime departureDateTime;
    private LocalDateTime earliestDepartureDateTime;
    private LocalDateTime latestDateTime;
    private LocalDateTime earliestDateTime;

    public NodePickup(int id_network, double lat, double lon, int tripId, int earliest, int latest, int load) {
        super(tripId, id_network, lat, lon, earliest, latest);
        this.load = load;
        this.tripId = tripId;
        this.delay = null;
        this.departure = null;
        this.arrival = null;
        this.arrivalSoFar = null;

        // TODO If reservation-based, earliest time can be in the future
        this.earliestDeparture = earliest;

        // User is somewhere where vehicles cannot reach fast enough. Increase hotness to attract vehicles.
        this.increaseHotness();
    }

    public NodePickup(
            int tripId,
            int idNetwork,
            LocalDateTime pickupDateTime,
            LocalDateTime latestPickupDateTime,
            int numPassengers) {

        super(idNetwork);
        this.earliestDateTime = pickupDateTime;
        this.latestDateTime = latestPickupDateTime;
        this.earliestDepartureDateTime = this.earliestDateTime;
        this.load = numPassengers;
        this.tripId = tripId;
        this.departureDateTime = null;
        this.delay = null;
        this.departure = null;
        this.arrival = null;
        this.arrivalSoFar = null;

        // TODO If reservation-based, earliest time can be in the future
        this.earliestDeparture = earliest;
    }

    @Override
    public String toString() {
        return String.format("%10s", "PK" + String.valueOf(tripId));
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
                "    }", "#0e8b07", "small", "circle", env.getNetwork().getLon(getNetworkId()), env.getNetwork().getLat(getNetworkId()));

        return s;
    }

    @Override
    public String getType() {
        return "pickup";
    }
}
