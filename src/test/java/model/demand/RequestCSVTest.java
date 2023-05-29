package model.demand;

import dao.DateUtil;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

class RequestCSVTest {


    private RequestCSV requests;


    @Test
    void readAllRequestsFromCSVFile() {
        String source = "./data/nyc/processed/trip_records/20190220170000-20190220190000_2019-2-yellow-Manhattan-00-00-00-23-59-59.csv";
        requests = new RequestCSV(source);
        requests.getRequestList().stream().limit(10).forEach(System.out::println);
    }

    @Test
    void getRequests() {
        readAllRequestsFromCSVFile();

        for (int sec = 0; sec < 60; sec++) {
            System.out.println("Requests before " + sec);
            requests.getRequestsBetween("2019-02-20 17:00:00", sec).forEach(System.out::println);
        }

        String startStr = "2019-02-20 17:00:00";
        String endStr = "2019-02-20 17:00:30";


        System.out.println("30 secs:");
        requests.getRequestsBetween("2019-02-20 17:00:00", "2019-02-20 17:00:30").forEach(System.out::println);


        LocalDateTime startDatetime = LocalDateTime.parse(startStr, DateUtil.formatter_local_date_time);
        LocalDateTime endDatetime = LocalDateTime.parse(endStr, DateUtil.formatter_local_date_time);
        assert (requests.getRequestsBetween(startDatetime, endDatetime).equals(requests.getRequestsBetween(startStr, endStr)));
        assert (requests.getRequestsBetween(startDatetime, endDatetime).equals(requests.getRequestsBetween(startStr, 30)));
        assert (requests.getRequestsBetween(startDatetime, endDatetime).equals(requests.getRequestsBetween(startDatetime, 30)));


        int tw = 30;
        for (int sec = 0; sec < 7600; sec += tw) {
            LocalDateTime start = LocalDateTime.parse(startStr, DateUtil.formatter_local_date_time).plusSeconds(sec);
            System.out.println("Requests between " + start + " and " + start.plusSeconds(tw));
            requests.getRequestsBetween(start, tw).forEach(System.out::println);
        }
    }
}