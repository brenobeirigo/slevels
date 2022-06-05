package util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.sql.SQLOutput;

public class TestLog {

    public String slowMethod(String a){
        System.out.println(a);
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return a;
    }
    @Test
    public void testLogger(){
        Logger logger = LoggerFactory.getLogger(TestLog.class);
        //logger.trace("Hello World");

        logger.debug("Hello World {}", "a"+ slowMethod("debug"));
        logger.info("Hello World {}", "a"+slowMethod("info"));
        //logger.warn("Hello World");
        //logger.error("Hello World");
    }
}