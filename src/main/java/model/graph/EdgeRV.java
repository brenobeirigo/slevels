package model.graph;

public interface EdgeRV extends Comparable<EdgeRV>{

    boolean isHiringEdge();

    boolean isHiringEdgeAndUserHasPromptedHiring();

    boolean isRV();

    boolean isIncumbentVehicleEdge();

    int getDelay();

    Object getFrom();

    Object getTarget();


}
