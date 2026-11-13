package com.plantshop;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantshop.support.MySqlTestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;

/** Verifies the operational endpoints used by Docker health checks (NFR-REL-01, NFR-OBS-03). */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = "management.server.port=0")
@Import(MySqlTestcontainersConfiguration.class)
class ActuatorIT {

  private final HttpClient http = HttpClient.newHttpClient();

  @Value("${local.management.port}")
  private int managementPort;

  @Value("${local.server.port}")
  private int serverPort;

  private HttpResponse<String> get(int port, String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  @DisplayName("[NFR-REL-01] readiness probe is UP on the management port")
  void readiness_whenApplicationStarted_isUp() throws Exception {
    HttpResponse<String> response = get(managementPort, "/actuator/health/readiness");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"UP\"");
  }

  @Test
  @DisplayName("[NFR-REL-01] liveness probe is UP on the management port")
  void liveness_whenApplicationStarted_isUp() throws Exception {
    HttpResponse<String> response = get(managementPort, "/actuator/health/liveness");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"UP\"");
  }

  @Test
  @DisplayName("[NFR-OBS-03] Prometheus metrics are exposed on the management port")
  void prometheus_whenScraped_returnsJvmMetrics() throws Exception {
    HttpResponse<String> response = get(managementPort, "/actuator/prometheus");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("jvm_memory_used_bytes");
  }

  @Test
  @DisplayName("actuator is not reachable on the public API port")
  void actuator_onApiPort_isNotExposed() throws Exception {
    HttpResponse<String> response = get(serverPort, "/actuator/health");

    assertThat(response.statusCode()).isIn(401, 404);
  }
}
