package com.geostat.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;

@SpringBootApplication(exclude = RabbitAutoConfiguration.class)
public class IngestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestionApplication.class, args);
    }
}
