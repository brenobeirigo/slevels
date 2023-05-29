package dao;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class DateUtil {

    public static DateFormat formatter_t = new SimpleDateFormat("HH:mm:ss");
    public static DateFormat formatter_date_time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public static DateFormat formatter_date = new SimpleDateFormat("yyyy-MM-dd");
    public static DateTimeFormatter formatter_local_date_time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String sec2TStamp(@JsonProperty("start_datetime") LocalDateTime earliestDatetime, int sec) {
        return formatter_t.format(seconds2Date(earliestDatetime, sec));
    }

    public static String sec2Datetime(LocalDateTime earliestDatetime, int sec) {
        return formatter_date_time.format(seconds2Date(earliestDatetime, sec));
    }

    public static int date2Seconds(Date earliestTime, String departureDate) {
        int secs = -1;
        try {
            secs = (int) (formatter_date_time.parse(departureDate).getTime() - earliestTime.getTime()) / 1000;
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return secs;
    }

    public static LocalDateTime seconds2Date(LocalDateTime earliestDateTime, int addedSeconds) {
        return earliestDateTime.plusSeconds(addedSeconds);
    }
}
