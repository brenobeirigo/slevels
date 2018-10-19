package model.graph;

import model.User;
import model.Vehicle;
import model.Visit;
import simulation.Method;

import java.util.*;

public class Graph {
    private Set<Vertex> vertices;
    private List<User> requests;
    private Set<Edge> edges;
    private Map<Vertex, List<Edge>> adj;
    private String name;

    public Graph(String name) {
        this.vertices = new HashSet<>();
        this.edges = new HashSet<>();
        this.adj = new HashMap<>();
        this.name = name;
        this.requests = new ArrayList<>();
    }

    public Graph(Set<Vertex> vertices, Set<Edge> edges, Map<Vertex, List<Edge>> adj) {
        this.setVertices(vertices);
        this.setEdges(edges);
        this.setAdj(adj);
    }

    public Graph() {
        this.vertices = new HashSet<>();
        this.edges = new HashSet<>();
        this.adj = new HashMap<>();
        this.requests = new ArrayList<>();
    }

    public List<User> getRequests() {
        return requests;
    }

    public void addVertex(Vertex v) {
        this.vertices.add(v);
        this.adj.put(v, new ArrayList<>());
    }

    public VertexVehicle addVertexVehicle(Vehicle v) {
        VertexVehicle vv = new VertexVehicle(v);
        this.vertices.add(vv);
        this.adj.putIfAbsent(vv, new ArrayList<>());
        return vv;
    }

    public VertexTrip addVertexTrip(Set<User> trip) {
        VertexTrip vt = new VertexTrip(trip);
        this.vertices.add(vt);
        this.adj.putIfAbsent(vt, new ArrayList<>());
        return vt;
    }

    public VertexRequest addVertexRequest(User r) {
        VertexRequest vr = new VertexRequest(r);
        this.vertices.add(vr);
        this.adj.putIfAbsent(vr, new ArrayList<>());
        return vr;
    }

    public void addEdge(Vertex a, Vertex b, Visit visit) {
        Edge e = new EdgeVisit(a, b, visit);
        this.adj.get(a).add(e);
        this.adj.get(b).add(e);
        this.edges.add(e);
    }

    public Visit addEdge(Vehicle vehicle, Set<User> trip) {

        // Try to get a visit out of vehicle and trip
        Visit visit = Method.getVisit(trip,
                vehicle,
                false,
                100);

        if (visit != null) {

            VertexTrip t = this.addVertexTrip(trip);
            VertexVehicle v = this.addVertexVehicle(vehicle);

            // For each request in trip
            for (User u : trip) {
                // Create request
                VertexRequest r = this.addVertexRequest(u);
                //requests.add(u);
                // Create edge linking request to trip
                Edge e = new EdgeVisit(r, t, visit);
                // Add trip to adjacent list of request
                this.adj.get(r).add(e);
                // Add request to adjacent list of trip
                this.adj.get(t).add(e);
                // Add edge to list
                this.edges.add(e);
            }

            Edge e = new EdgeVisit(v, t, visit);
            // Add trip to adjacent list of request
            this.adj.get(v).add(e);
            // Add request to adjacent list of trip
            this.adj.get(v).add(e);
            this.edges.add(e);

            return visit;
        }
        return null;
    }

    public Set<Vertex> getVertices() {
        return vertices;
    }

    public void setVertices(Set<Vertex> vertices) {
        this.vertices = vertices;
    }

    public Set<Edge> getEdges() {
        return edges;
    }

    public void setEdges(Set<Edge> edges) {
        this.edges = edges;
    }

    public Map<Vertex, List<Edge>> getAdj() {
        return adj;
    }

    public void setAdj(Map<Vertex, List<Edge>> adj) {
        this.adj = adj;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append(this.name + " GRAPH:");
        b.append("\nVertices:").append(vertices);
        b.append("\nEdges:").append(edges);
        b.append("\nAdj.:").append(adj);

        return b.toString();
    }
}





