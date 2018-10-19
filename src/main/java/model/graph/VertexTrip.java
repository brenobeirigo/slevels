package model.graph;

import model.User;

import java.util.Set;

public class VertexTrip extends Vertex {

    public static int tripCount = 1;

    private Set<User> trip;

    public VertexTrip(Set<User> trip) {
        super(VertexTrip.tripCount);
        VertexTrip.tripCount++;
        this.trip = trip;
    }

    public Set<User> getTrip() {
        return trip;
    }

    @Override
    public String toString() {
        String str = "{";
        for (User u :
                trip) {
            str += "R" + u.getId() + ",";

        }
        str = str.substring(0, str.length() - 1) + "}";
        return str;
    }


}
