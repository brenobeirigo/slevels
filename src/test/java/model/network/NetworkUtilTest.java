package model.network;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class NetworkUtilTest {

    @Test
    void assertDistanceMatrixMetersIsValid() {
        String pathDistMatrix = "./data/nyc/processed/network/distance/dist_matrix_m.csv";
        List<List<Double>> distMatrix = NetworkUtil.getDistanceMatrixMeters(pathDistMatrix);

        for (int i = 0; i < distMatrix.size(); i++) {

            assert distMatrix.get(i).size() == distMatrix.size();

            for (int j = 0; j < distMatrix.size(); j++) {

                if (i != j)
                    assert distMatrix.get(i).get(j) > 0;
                else
                    assert distMatrix.get(i).get(j) == 0;
            }
        }
    }

    @Test
    void testGetAdjacencyMatrix() {
        String pathAdjacencyMatrix = "./data/nyc/processed/network/adjacency_matrix.csv";
        List<List<Integer>> adjacencyMatrix = NetworkUtil.getAdjacencyMatrix(pathAdjacencyMatrix);
    }

    @Test
    void testGetZones(){
        String pathDistMatrix = "./data/nyc/processed/network/distance/dist_matrix_m.csv";
        List<List<Double>> distMatrix = NetworkUtil.getDistanceMatrixMeters(pathDistMatrix);

        String pathZones = "./data/nyc/processed/taxi_zones/manhattan_taxi_zones.csv";
        List<Zone> zones = NetworkUtil.getZones(pathZones);

        Set<Integer> nodeIds = Arrays.stream(IntStream.range(0, distMatrix.size()).toArray()).boxed().collect(Collectors.toSet());
        Set<Integer> zoneIds = zones.stream().map(Zone::nodeId).collect(Collectors.toSet());
        assert Sets.intersection(zoneIds, nodeIds).containsAll(zoneIds);

    }

    @Test
    void testGetWeightedGraphFromAdjacencyMatrix() {
        String pathAdjacencyMatrix = "./data/nyc/processed/network/adjacency_matrix.csv";
        String pathDistMatrix = "./data/nyc/processed/network/distance/dist_matrix_m.csv";
        List<List<Integer>> adjacencyMatrix = NetworkUtil.getAdjacencyMatrix(pathAdjacencyMatrix);
        List<List<Double>> distMatrix = NetworkUtil.getDistanceMatrixMeters(pathDistMatrix);
        SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> graph = NetworkUtil.getWeightedGraphFromAdjacencyMatrix(adjacencyMatrix, distMatrix);

        assert distMatrix.size() == graph.vertexSet().size();
        assert adjacencyMatrix.size() == graph.vertexSet().size();

        for (int i = 0; i < distMatrix.size(); i++) {
            for (int j = 0; j < distMatrix.size(); j++) {
                if (adjacencyMatrix.get(i).get(j) > 0) {
                    assert graph.getAllEdges(i, j).size() > 0;
                }
                else{
                    assert graph.getAllEdges(i, j).size() == 0;
                }
            }
        }
    }
}