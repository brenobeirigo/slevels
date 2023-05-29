package model.route;

import java.util.Collections;
import java.util.List;

public class RouteUtil {
    public static int getInsertionPoint(int elapsedTime, List<Integer> intermediateArrivalsList) {
        // Find position of first higher arrival
        int insertionPoint = Collections.binarySearch(intermediateArrivalsList, elapsedTime);

        // Correct for elapsedTime not in shortest path (<0)
        // Position that element would be inserted into the list = (-(insertion point) - 1).
        insertionPoint = insertionPoint >= 0 ? insertionPoint : -1 - insertionPoint;
        return insertionPoint;
    }
}
