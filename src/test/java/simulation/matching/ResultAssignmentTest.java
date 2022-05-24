package simulation.matching;

import config.Config;
import config.CustomerBaseConfig;
import config.InstanceConfig;
import dao.Dao;
import model.User;
import model.Vehicle;
import model.Visit;
import model.VisitObj;
import model.graph.GraphRV;
import model.node.Node;
import org.junit.jupiter.api.Test;
import simulation.Method;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResultAssignmentTest {

    @Test
    void getSnapshot() {
        String s = "D:\\projects\\dev\\slevels\\config.json";
        InstanceConfig instanceSettings;
        try {
            instanceSettings = Config.createInstanceFrom(s);

            Date earliestTime = Config.formatter_date_time.parse("2011-02-12 00:00:00");
            Config.getInstance().setEarliestTime(earliestTime);
            CustomerBaseConfig customerBaseSettings = instanceSettings.getCustomerBaseSettingsArray().get(0);
            Config.getInstance().updateQosDic(customerBaseSettings.qosDic);


            ResultAssignment r = new ResultAssignment(0);
            //Load requests 5 minutes apart
            User u1 = new User(4,300, 600, 300, 1, 0, 1, 3, 2, 3, 2);
            User u2 = new User(3,300, 600, 300, 3, 0, 1, 3, 2, 3, 2);
            User u3 = new User(2,300, 600, 300, 2, 0, 1, 3, 2, 3, 2);
            User u4 = new User(1,300, 600, 300, 2, 0, 1, 3, 2, 3, 2);
            User u5 = new User(0,300, 600, 300, 2, 0, 1, 3, 2, 3, 2);
            User u6 = new User(0,300, 600, 300, 2, 0, 1, 3, 2, 3, 2);

            Vehicle fullVehicle = new Vehicle(4, 0, 0, false, 4000);
            Vehicle emptyVehicle = new Vehicle(4, 0, 0, false, 4000);

            VisitObj bestVisit = Method.getBestVisitFromInsertion(fullVehicle, u1);
            System.out.println(bestVisit);

            //public Visit(Node[] sequencePD, int delay, int idleness, Vehicle vehicle, Set<User> requests) {
//            Node[] sequence = new Node[]{u5.getNodeDp(), u6.getNodeDp()};
//            HashSet<User> requests = new HashSet<>(List.of(new User[]{u1, u2}));
//
//            fullVehicle.setVisit(new Visit(sequence, 2, 0, fullVehicle, requests));
//
//
//            Set<User> listWaitingUsers = new HashSet<>(List.of(new User[]{u1, u2, u3}));
//
//            Vehicle v1 = new Vehicle(4, 0, 0, false, 4000);
//            Vehicle v2 = new Vehicle(4, 1, 0, false, 4000);
//            Vehicle v3 = new Vehicle(4, 2, 0, false, 4000);
//
//            Set<Vehicle> listVehicles = new HashSet<>(List.of(new Vehicle[]{v1, v2}));


//            int vehicleCapacity = 3;
//            int maxEdgesRV = 3;
//            int maxEdgesRR = 2;
//            GraphRV graph = new GraphRV(listWaitingUsers, listVehicles, vehicleCapacity, maxEdgesRV, maxEdgesRR);

        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}