package com.SwampDragonInn.trackmyboardgames;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Starts Spring Boot; component scanning finds the controllers, services, repositories, and configuration below this package.
@SpringBootApplication
public class TrackmyboardgamesApplication
{

	public static void main(String[] args)
	{
		SpringApplication.run(TrackmyboardgamesApplication.class, args);
	}

}
