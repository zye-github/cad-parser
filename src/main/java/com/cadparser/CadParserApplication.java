package com.cadparser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CadParserApplication {

    public static void main(String[] args) {
        SpringApplication.run(CadParserApplication.class, args);
    }
}
