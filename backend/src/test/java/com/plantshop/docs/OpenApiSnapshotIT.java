package com.plantshop.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plantshop.support.MySqlTestcontainersConfiguration;
import com.plantshop.support.OpenApiContract;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contract drift guard (docs/plan/09-testing-strategy.md §3.5): the generated OpenAPI document must
 * equal the committed {@code docs/api/openapi.yaml}. Regenerate intentionally with {@code ./mvnw
 * verify -Pupdate-openapi}.
 */
@SpringBootTest(properties = {"app.swagger.enabled=true", "app.auth.mode=github,local"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Import(MySqlTestcontainersConfiguration.class)
class OpenApiSnapshotIT {

  @Autowired private MockMvc mvc;

  @Test
  @DisplayName("generated OpenAPI document matches docs/api/openapi.yaml")
  void generatedSpec_matchesCommittedSnapshot() throws Exception {
    String generated =
        mvc.perform(get("/v3/api-docs.yaml"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    if (Boolean.getBoolean("openapi.update")) {
      Files.writeString(OpenApiContract.SPEC, generated, StandardCharsets.UTF_8);
    }

    String committed =
        Files.exists(OpenApiContract.SPEC)
            ? Files.readString(OpenApiContract.SPEC, StandardCharsets.UTF_8)
            : "";
    assertThat(generated)
        .as("OpenAPI drift: run `./mvnw verify -Pupdate-openapi` and commit docs/api/openapi.yaml")
        .isEqualTo(committed);
  }

  @Test
  @DisplayName("Swagger UI is served when enabled")
  void swaggerUi_isServed() throws Exception {
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }
}
