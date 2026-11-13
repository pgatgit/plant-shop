package com.plantshop.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real MySQL 8.4 for tests (never H2: search semantics depend on the MySQL collation, ADR-011). One
 * container per JVM, shared by every Spring test context that imports this configuration.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestcontainersConfiguration {

  private static final MySQLContainer MYSQL =
      new MySQLContainer(DockerImageName.parse("mysql:8.4"))
          .withDatabaseName("plantshop")
          .withUsername("plantshop")
          .withPassword("plantshop-test")
          .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");

  @Bean
  @ServiceConnection
  MySQLContainer mysqlContainer() {
    return MYSQL;
  }
}
