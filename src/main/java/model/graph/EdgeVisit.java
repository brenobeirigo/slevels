package model.graph;

import model.Visit;

public class EdgeVisit extends Edge {

    private Visit visit;

    public EdgeVisit(Vertex from, Vertex to, Visit visit) {
        super(from, to);
        this.visit = visit;
    }

    @Override
    public String toString() {
        return "(" + this.from + "-" + this.to + ") = " + visit;
    }

    public Visit getVisit() {
        return visit;
    }
}
