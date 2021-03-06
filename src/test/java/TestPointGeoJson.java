import config.Config;
import config.Qos;
import dao.Dao;
import model.User;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class TestPointGeoJson {

    public static void main(String args[]) throws ParseException {

//        Qos qos1 = new Qos("A", 180, 180, 0.9, 0.16, false);
//        Qos qos2 = new Qos("B", 300, 600, 0.8, 0.68, true);
//        Qos qos3 = new Qos("C", 600, 900, 0.7, 0.16, true);
//        //Qos qos4 = new Qos("R", 300, 600, 0.95,0, true);
//        Config.getInstance().qosDic.put("A", qos1);
//        Config.getInstance().qosDic.put("B", qos2);
//        Config.getInstance().qosDic.put("C", qos3);

        Dao dao = Dao.getInstance();
        Date earliestTime = Config.formatter_date_time.parse("2011-02-12 00:00:00");
        Set<User> listUsers = dao.getListTripsClassed(earliestTime,30, 10, 10);

        /*

        List<Node> nodes = new ArrayList<>();
        List<String> edgeList = new ArrayList<>();
        for (User u: listUsers) {
            Node from = u.getNodePk();
            Node to = u.getNodeDp();
            nodes.add(from);
            nodes.add(to);
            String edge = dao.getGeoJsonSPBetweenODfromServer(from, to);
            edgeList.add(edge);
        }

        List<String> listFeatures = new ArrayList<>();
        for (int i = 0; i < nodes.size()-1; i++) {
            String o = dao.getGeoJsonPointfromServer(nodes.get(i));
            String d = dao.getGeoJsonPointfromServer(nodes.get(i+1));
            String edge = dao.getGeoJsonSPBetweenODfromServer(nodes.get(i), nodes.get(i+1));
            listFeatures.add(o);
            listFeatures.add(d);
            listFeatures.add(edge);
        }




        Logging.logger.info(String.join(",", nodes.stream().map(n -> dao.getGeoJsonPointfromServer(n)).collect(Collectors.toList())));
        Logging.logger.info(String.join(",", edgeList));
        */
    }
}
