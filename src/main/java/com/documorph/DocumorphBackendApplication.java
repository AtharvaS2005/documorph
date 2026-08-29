package com.documorph;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DocumorphBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(DocumorphBackendApplication.class, args);
	}

}