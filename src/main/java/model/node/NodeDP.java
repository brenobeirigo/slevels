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

}
