import model.Vehicle;

import java.util.ArrayList;
import java.util.List;

public class TestHelperMethods {



    public static List<Vehicle> createListVehicles(int n, int size) {
        List<Vehicle> listVehicle = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            listVehicle.add(new Vehicle(size, 0, 2.3, 3.4));
        }
        return listVehicle;
    }

}
