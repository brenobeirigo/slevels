package simulation;

import config.InstanceData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


class SimulationTest {

    @Test
    public void testGetRequests(){

        InstanceData i = new InstanceData("instance_config.json");
        Simulation s = new Simulation(new Environment(i),0);

    }
}