package model.network;

import model.node.Node;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Set;

public interface TransportNetwork {

    Double getDistance(int originNetworkId, int destinationNetworkId);

    Set<Integer> getNodeIds();

    Point2D getLocation(int id);

    List<Integer> getShortestPathBetween(int originNetworkId, int destinationNetworkId);

    List<Integer> getNClosestZones(int originNetworkId, int nZones);

    List<Integer> getZoneIds();

    int getDistSec(int fromId, int toId);

    double getLon(int networkId);

    double getLat(int networkId);

    int getNodeBetweenAndExtraDelay(Node from,
                                    Node to,
                                    int elapsedTime);

    int getIntermediateNodeNetworkId(int vehicleOriginNetworkId, int vehicleDestinationNetworkId, int i);

    List<String> getShortestPathLonLatBetween(int originNetworkId, int destinationNetworkId);

    String getInfo(Node n);
}
