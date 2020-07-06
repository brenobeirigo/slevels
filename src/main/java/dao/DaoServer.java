package dao;
import java.util.List;

public class DaoServer extends Dao{

    public List<Integer> getShortestPathBetween(int o, int d) {

        if (this.shortestPathsNodeIds.get(o).get(d) == null) {

            //ArrayList<Integer> sp = getShortestPathBetweenODfromDB(fromNode, toNode); // FROM DB
            List<Integer> sp = ServerUtil.getShortestPathBetween(o, d);


            shortestPathsNodeIds.get(o).set(d, sp);
        }

        return shortestPathsNodeIds.get(o).get(d);
    }
}
