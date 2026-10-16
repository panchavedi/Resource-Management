package com.talent.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ManagementApplication {

    public static void main(String[] args) {

        SpringApplication.run(ManagementApplication.class, args);
    }
}
