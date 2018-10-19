package model.graph;

import model.User;

public class VertexRequest extends Vertex {
    private User u;

    public VertexRequest(User u) {
        super(u.getId());
        this.u = u;
    }

    public User getU() {
        return u;
    }

    public void setU(User u) {
        this.u = u;
    }

    @Override
    public String toString() {
        return "R" + String.valueOf(u.getId());
    }
}