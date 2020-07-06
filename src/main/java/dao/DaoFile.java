package dao;

import java.util.ArrayList;
import java.util.List;

public class DaoFile extends Dao{

    public List<Integer> getShortestPathBetween(int o, int d) {

        if (this.shortestPathsNodeIds.get(o).get(d) == null) {

            List<Integer> sp = ServerUtil.getShortestPathBetween(o, d);
            List<Integer> sp3 = new ArrayList<>();

            for (int i = 0; i < sp.size(); i++) {
                sp3.add(sp.get(i).intValue());
            }

            shortestPathsNodeIds.get(o).set(d, sp3);
        }

        return shortestPathsNodeIds.get(o).get(d);
    }
}