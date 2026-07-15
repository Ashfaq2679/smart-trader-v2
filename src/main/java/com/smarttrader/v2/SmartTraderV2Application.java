package com.smarttrader.v2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartTraderV2Application {

    public static void main(String[] args) {
        SpringApplication.run(SmartTraderV2Application.class, args);
    }
}
