//import config.Config;
//import config.CustomerBaseConfig;
//import config.InstanceConfig;
//import dao.DateUtil;
//import dao.Logging;
//import model.demand.User;
//import model.Vehicle;
//import org.testng.annotations.Test;
//import util.pdcombinatorics.PDGeneratorSingleInsertion;
//
//import java.io.IOException;
//import java.text.ParseException;
//import java.util.*;
//
//public class TestPDPInsertion {
//
//
//    @Test
//    public static void main(String[] args) {
//
//        String s = "D:\\projects\\dev\\slevels\\config.json";
//        InstanceConfig instanceSettings;
//        try {
//            instanceSettings = Config.createInstanceFrom(s);
//
//            Date earliestTime = DateUtil.formatter_date_time.parse("2011-02-12 00:00:00");
//            CustomerBaseConfig customerBaseSettings = instanceSettings.getCustomerBaseSettingsArray().get(0);
//            Config.getInstance().updateQosDic(customerBaseSettings.qosDic);
//            //Load requests 5 minutes apart
//            User u1 = new User("2011-02-12 00:00:00",earliestTime, 1, 0, 1, 3, 2, 3, 2);
//            User u5 = new User("2011-02-12 00:00:00",earliestTime, 1, 0, 1, 3, 2, 3, 2);
//            User u2 = new User("2011-02-12 00:05:00",earliestTime, 1, 2, 3, 3, 2, 3, 2);
//            User u3 = new User("2011-02-12 00:10:00",earliestTime, 1, 3, 4, 3, 2, 3, 2);
//            User u4 = new User("2011-02-12 00:15:00",earliestTime, 1, 4, 5, 3, 2, 3, 2);
//
//            Set<User> listWaitingUsers = new HashSet<>(List.of(new User[]{u1, u2, u3}));
//
//            Vehicle v1 = new Vehicle(4, 0, 0, false, 4000);
//            Vehicle v2 = new Vehicle(4, 1, 0, false, 4000);
//            Vehicle v3 = new Vehicle(4, 2, 0, false, 4000);
//
//            PDGeneratorSingleInsertion insert = new PDGeneratorSingleInsertion(u1, new ArrayList<>(Arrays.asList(u5.getNodePk(), u5.getNodeDp())));
//
//            while (insert.hasNext()){
//                Logging.logger.info(Arrays.toString(insert.next()));
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        } catch (ParseException e) {
//            e.printStackTrace();
//        }
//    }
//}
//
