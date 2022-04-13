import config.Config;
import config.CustomerBaseConfig;
import config.InstanceConfig;
import model.User;
import model.Vehicle;
import model.graph.GraphRV;
import org.testng.annotations.Test;

import java.util.*;

public class TestGraphRV {

    @Test
    public void testName() {
        String s = "D:\\projects\\dev\\slevels\\config.json";
        InstanceConfig instanceSettings;
        try {
            instanceSettings = Config.createInstanceFrom(s);

            Date earliestTime = Config.formatter_date_time.parse("2011-02-12 00:00:00");
            Config.getInstance().setEarliestTime(earliestTime);
            CustomerBaseConfig customerBaseSettings = instanceSettings.getCustomerBaseSettingsArray().get(0);
            Config.getInstance().updateQosDic(customerBaseSettings.qosDic);


            //Load requests 5 minutes apart
            User u1 = new User("2011-02-12 00:00:00", 1, 0, 1, 3, 2, 3, 2);
            User u5 = new User("2011-02-12 00:00:00", 1, 0, 1, 3, 2, 3, 2);
            User u2 = new User("2011-02-12 00:05:00", 1, 2, 3, 3, 2, 3, 2);
            User u3 = new User("2011-02-12 00:10:00", 1, 3, 4, 3, 2, 3, 2);
            User u4 = new User("2011-02-12 00:15:00", 1, 4, 5, 3, 2, 3, 2);

            List<User> listWaitingUsers = new ArrayList<>(List.of(new User[]{u1, u2, u3}));

            Vehicle v1 = new Vehicle(4, 0, 0, false, 4000);
            Vehicle v2 = new Vehicle(4, 1, 0, false, 4000);
            Vehicle v3 = new Vehicle(4, 2, 0, false, 4000);

            List<Vehicle> listVehicles = new ArrayList<>(List.of(new Vehicle[]{v1, v2}));


            int vehicleCapacity = 3;
            int maxEdgesRV = 3;
            int maxEdgesRR = 2;
            GraphRV graph = new GraphRV(listWaitingUsers, listVehicles, vehicleCapacity, maxEdgesRV, maxEdgesRR);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
