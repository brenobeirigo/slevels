package model.graph;

// Edge class

public class Edge {
    protected Vertex from;
    protected Vertex to;

    public Edge(Vertex from, Vertex to) {
        super();
        this.from = from;
        this.to = to;
    }


    public Vertex getFrom() {
        return from;
    }

    public Vertex getTo() {
        return to;
    }
}