package model.demand;

import java.time.LocalDateTime;
import java.util.List;

public interface RequestUtil {

    List<Request> getRequestsBetween(LocalDateTime earliestDateTime, LocalDateTime latestDateTime);

    List<Request> getRequestsBetween(String earliestDateTimeStr, String latestDateTimeStr);

    List<Request> getRequestsBetween(LocalDateTime earliestDateTime, int timeWindow);

    List<Request> getRequestsBetween(String earliestDateTimeStr, int timeWindow);
}
