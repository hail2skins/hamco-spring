package com.hamco;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the HAMCO Spring application.
 *
 * <p>{@code @SpringBootApplication} combines configuration, component scanning,
 * and Spring Boot's auto-configuration. Keeping this class at the top of the
 * {@code com.hamco} package means future feature packages beneath it will be
 * discovered without a list of manually registered controllers or services.
 */
@SpringBootApplication
public class HamcoApiApplication {

    public static void main(String[] args) {
        // The same configuration is used for the executable server and tests.
        SpringApplication.run(HamcoApiApplication.class, args);
    }
}
