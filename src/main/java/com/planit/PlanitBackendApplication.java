package com.planit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class PlanitBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(PlanitBackendApplication.class, args);
	}

}
