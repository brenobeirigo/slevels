import config.Config;
import simulation.Simulation;
import simulation.SimulationFCFS;

public class TestInstance {

    public static void main(String[] args) {
        // Setup QoS class

        Config.Qos qos1 = new Config.Qos("A", 180, 180, 0.9, 0.16, false);
        Config.Qos qos2 = new Config.Qos("B", 300, 600, 0.8, 0.68, true);
        Config.Qos qos3 = new Config.Qos("C", 600, 900, 0.7, 0.16, true);
        //Config.Qos qos4 = new Config.Qos("R", 300, 600, 0.95,0, true);
        Config.getInstance().qosDic.put("A", qos1);
        Config.getInstance().qosDic.put("B", qos2);
        Config.getInstance().qosDic.put("C", qos3);

        Simulation fcfs = new SimulationFCFS(
                "FCFS",
                1,
                6,
                1000,
                30,
                600,
                1,
                120,
                2,
                false,
                false,
                "month",
                "Hire");

        fcfs.run(Simulation.ALL_INFO);
    }
}