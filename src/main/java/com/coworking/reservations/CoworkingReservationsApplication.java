package com.coworking.reservations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CoworkingReservationsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoworkingReservationsApplication.class, args);
    }
}
