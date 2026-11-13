package com.plantshop.docs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plantshop.support.MySqlTestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** D20: API docs and Swagger UI are not exposed unless explicitly enabled (prod default). */
@SpringBootTest(properties = "app.swagger.enabled=false")
@AutoConfigureMockMvc
@Import(MySqlTestcontainersConfiguration.class)
class SwaggerDisabledIT {

  @Autowired private MockMvc mvc;

  @Test
  @DisplayName("[D20] /v3/api-docs and Swagger UI return 404 when disabled")
  void docs_whenDisabled_areNotFound() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
  }
}
