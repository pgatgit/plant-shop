package com.plantshop.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The e2e profile additionally applies {@code db/e2e/R__e2e_fixtures.sql}. Uses a dedicated MySQL
 * container so the extra Flyway history row does not leak into other test classes.
 */
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/e2e")
@Import(E2eFixturesIT.DedicatedMySql.class)
class E2eFixturesIT {

  @Autowired private JdbcTemplate jdbc;

  @TestConfiguration(proxyBeanMethods = false)
  static class DedicatedMySql {
    @Bean
    @ServiceConnection
    MySQLContainer e2eMysqlContainer() {
      return new MySQLContainer(DockerImageName.parse("mysql:8.4"))
          .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");
    }
  }

  @Test
  @DisplayName("[06 §4.1] the repeatable e2e fixture runs after the versioned migrations")
  void e2eFixture_isApplied() {
    assertThat(
            jdbc.queryForList(
                "SELECT script FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class))
        .last()
        .isEqualTo("R__e2e_fixtures.sql");
    assertThat(
            jdbc.queryForObject(
                "SELECT stock FROM product WHERE sku = 'TRL-SPIDER'", Integer.class))
        .isEqualTo(50);
    assertThat(
            jdbc.queryForObject(
                "SELECT stock FROM product WHERE sku = 'FRN-BIRDNEST'", Integer.class))
        .isEqualTo(3);
  }
}
