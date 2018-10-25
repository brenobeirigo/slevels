import simulation.Simulation;
import simulation.SimulationFCFS;

public class Main {

    public static void main(String[] args) {

        //SimulationRTV fcfs = new SimulationRTV();
        Simulation fcfs = new SimulationFCFS();
        fcfs.run();
        //Simulation rtv = new SimulationRTV();
        //rtv.run();

    }
}
