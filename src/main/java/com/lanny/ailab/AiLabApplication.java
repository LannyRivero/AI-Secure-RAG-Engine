package com.lanny.ailab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiLabApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiLabApplication.class, args);
	}

}
