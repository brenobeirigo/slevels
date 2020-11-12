import config.Config;
import config.Qos;
import simulation.matching.Matching;

public class TestInstance {

    private static Matching matchingSettings;

    public static void main(String[] args) {
        // Setup QoS class

//        Qos qos1 = new Qos("A", 180, 180, 0.9, 1.0, false);
//        Qos qos2 = new Qos("B", 300, 600, 0.8, 0, true);
//        Qos qos3 = new Qos("C", 600, 900, 0.7, 0, true);
//        //Qos qos4 = new Qos("R", 300, 600, 0.95,0, true);
//        Config.getInstance().qosDic.put("A", qos1);
//        Config.getInstance().qosDic.put("B", qos2);
//        Config.getInstance().qosDic.put("C", qos3);

        /*Simulation fcfs = new SimulationFCFS(
                "test_reb_nocleant",
                1000,
                6,
                1000,
                30,
                24 * 3600,
                true,
                3600,
                true,
                true,
                false,
                "month",
                "Hire",
                new Rebalance(true, true, true, true, "N21"), matchingSettings);

    */
    }
}