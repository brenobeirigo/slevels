import config.Config;
import simulation.Simulation;
import simulation.SimulationRTV;

public class TestRTV {

    public static void main(String[] args) {
        // Setup QoS class

        Config.Qos qos1 = new Config.Qos("A", 180, 180, 1, 1, false);
        Config.Qos qos2 = new Config.Qos("B", 300, 600, 0.95, 0, true);
        Config.Qos qos3 = new Config.Qos("C", 600, 900, 0.8, 0, true);
        Config.getInstance().qosDic.put("A", qos1);
        Config.getInstance().qosDic.put("B", qos2);
        Config.getInstance().qosDic.put("C", qos3);

        Simulation rtv = new SimulationRTV(0,
                4,
                1000,
                30,
                24 * 3600,
                "S1",
                "C");
        rtv.run(Simulation.ROUND_INFO);

    }
}

