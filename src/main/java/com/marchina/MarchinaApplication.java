package com.marchina;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.marchina.agent",
    "com.marchina.controller",
    "com.marchina.model",
    "com.marchina.config"
})
public class MarchinaApplication {

	public static void main(String[] args) {
		SpringApplication.run(MarchinaApplication.class, args);
	}

}
