package model.graph;

import model.Vehicle;

public class VertexVehicle extends Vertex {

    private Vehicle v;

    public VertexVehicle(Vehicle v) {
        super(v.getId());
        this.v = v;
    }


    @Override
    public String toString() {
        return "V" + String.valueOf(v.getId());
    }

}