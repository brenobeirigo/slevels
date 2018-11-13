package model.node;

public class NodePK extends Node {

    public NodePK(int id_network, double lat, double lon, int tripId, int earliest, int latest, int load) {
        super(tripId, id_network, lat, lon, earliest, latest, load);
        this.tripId = tripId;
    }


    @Override
    public String toString() {
        return String.format("%7s", "PK" + String.valueOf(tripId));
    }

}
