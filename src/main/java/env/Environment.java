package env;

import java.util.List;

public interface Environment {


    double getDistanceBetween(int origin, int destination);
    List<Request> getRequestsFromStep(int t);

}
