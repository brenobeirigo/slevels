package model.demand;

import dao.DateUtil;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RequestCSV implements RequestUtil {
    public List<Request> getRequestList() {
        return requestList;
    }

    private final List<Request> requestList;

    public RequestCSV(String pathRequestList) {
        this.requestList = readAllRequestsFromCSVFile(pathRequestList);
    }

    public List<Request> readAllRequestsFromCSVFile(String pathRequestList) {

        List<Request> requestList = new ArrayList<>();
        try {
            CSVParser records = CSVParser.parse(new FileReader(pathRequestList), CSVFormat.RFC4180.withFirstRecordAsHeader());

            // Continue reading records
            for (CSVRecord record : records) {
                Request requestRecord = getRequestFromCSVRecord(record);
                requestList.add(requestRecord);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Collections.sort(requestList);

        return requestList;
    }

    private Request getRequestFromCSVRecord(CSVRecord record) {
        return new Request(
                Integer.parseInt(record.get(Request.USER_ID)),
                LocalDateTime.parse(record.get(Request.PICKUP_DATETIME), DateUtil.formatter_local_date_time),
                Integer.parseInt(record.get(Request.PICKUP_NODE_ID)),
                Integer.parseInt(record.get(Request.DROPOFF_NODE_ID)),
                Integer.parseInt(record.get(Request.PASSENGER_COUNT)));
    }

    @Override
    public List<Request> getRequestsBetween(LocalDateTime earliestDateTime, LocalDateTime latestDateTime) {
        List<Request> requestsBetweenEarliestAndLatest = new ArrayList<>();
        for (Request request : requestList) {

            if (request.pickupDateTime().isBefore(earliestDateTime))
                continue;

            if (request.pickupDateTime().isAfter(latestDateTime) || request.pickupDateTime().equals(latestDateTime))
                break;

            if (request.pickupDateTime().isBefore(latestDateTime))
                requestsBetweenEarliestAndLatest.add(request);
        }
        return requestsBetweenEarliestAndLatest;
    }

    @Override
    public List<Request> getRequestsBetween(String earliestDateTimeStr, String latestDateTimeStr) {
        return getRequestsBetween(
                LocalDateTime.parse(earliestDateTimeStr, DateUtil.formatter_local_date_time),
                LocalDateTime.parse(latestDateTimeStr, DateUtil.formatter_local_date_time)
        );
    }

    @Override
    public List<Request> getRequestsBetween(LocalDateTime earliestDateTime, int timeWindowSeconds) {
        return getRequestsBetween(earliestDateTime, earliestDateTime.plusSeconds(timeWindowSeconds));
    }

    @Override
    public List<Request> getRequestsBetween(String earliestDateTimeStr, int timeWindowSeconds) {
        LocalDateTime earliestDateTime = LocalDateTime.parse(earliestDateTimeStr, DateUtil.formatter_local_date_time);
        return getRequestsBetween(earliestDateTime, timeWindowSeconds);
    }
}
