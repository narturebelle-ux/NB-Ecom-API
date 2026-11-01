package com.narturebelle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class NartureBelleApplication {

    public static void main(String[] args) {
        // Slots are local India times sent by the UI; containers usually run in UTC.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(NartureBelleApplication.class, args);
    }
}
