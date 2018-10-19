package model.graph;

// Vertex class
public class Vertex {
    private int id;

    public Vertex(int id) {
        this.id = id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof Vertex && id == ((Vertex) obj).id);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}

