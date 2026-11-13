package com.plantshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point of the Plant Shop backend (catalog, cart and search REST API). */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PlantShopApplication {

  /**
   * Starts the application.
   *
   * @param args command-line arguments passed to Spring Boot
   */
  public static void main(String[] args) {
    SpringApplication.run(PlantShopApplication.class, args);
  }
}
