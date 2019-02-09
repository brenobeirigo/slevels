import config.Config;
import config.Rebalance;
import simulation.Simulation;
import simulation.SimulationFCFS;

public class TestInstance {

    public static void main(String[] args) {
        // Setup QoS class

        Config.Qos qos1 = new Config.Qos("A", 180, 180, 0.9, 1.0, false);
        Config.Qos qos2 = new Config.Qos("B", 300, 600, 0.8, 0, true);
        Config.Qos qos3 = new Config.Qos("C", 600, 900, 0.7, 0, true);
        //Config.Qos qos4 = new Config.Qos("R", 300, 600, 0.95,0, true);
        Config.getInstance().qosDic.put("A", qos1);
        Config.getInstance().qosDic.put("B", qos2);
        Config.getInstance().qosDic.put("C", qos3);

        Simulation fcfs = new SimulationFCFS(
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
                "month",
                "Hire",
                new Rebalance(true, true, true, true, "N21"));

        fcfs.run(Simulation.ROUND_INFO);
    }
}