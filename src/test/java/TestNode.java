import dao.Logging;
import model.node.Node;
import model.node.NodeDP;
import model.node.NodePK;
import simulation.Method;

public class TestNode {

    public static void main(String[] arg) {
        Node n1 = new NodePK(2, 2.3, 4, 1, 300, 600, 4);
        Node n2 = new NodeDP(1644, 4.3, 3.4, 1, 1000, 2000, -4);

        int latest = Method.getEarliestDp(300, 2, 1644, "A");
        Logging.logger.info("Latest:" + latest);

        //2011-02-12 00:00:00,2,1644,2842,40.780187,-73.952865,40.771375,-73.95999999999998

        Logging.logger.info(n1.toString());
        Logging.logger.info(n2.toString());

        int[] d1 = Method.feasibleTrip(n1, n2, 100);
        Logging.logger.info("{}", String.format("%d and %d", d1[0], d1[1]));
    }
}
