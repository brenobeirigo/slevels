package model.node;


public class NodeOrigin extends Node {


    public NodeOrigin(int id, int id_network, double lat, double lon, int load) {
        super(id, id_network, lat, lon, 0, 0, load);
        this.tripId = id;
    }

    public NodeOrigin(int id, int id_network, int load) {
        super(id, id_network, 0, 0, load);
        this.tripId = id;
    }

    @Override
    public String toString() {
        return String.format("%7s", "OR" + String.valueOf(this.getId() - Node.MAX_NUMBER_NODES * 2));
    }
}
