package com.plantshop.common.web;

import static com.plantshop.support.TestAuth.shopper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.plantshop.support.MySqlTestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** NFR-OBS-02: request ids, trace ids in logs and problems, owner hash instead of owner id. */
@SpringBootTest(properties = "logging.level.com.plantshop.common.error=DEBUG")
@AutoConfigureMockMvc
@Import(MySqlTestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
@Tag("NFR-OBS-02")
class ObservabilityIT {

  @Autowired private MockMvc mvc;

  @Test
  @DisplayName("every response echoes or assigns X-Request-Id, also for 401s")
  void requestId_isAlwaysReturned() throws Exception {
    mvc.perform(get("/api/v1/cart"))
        .andExpect(status().isUnauthorized())
        .andExpect(
            header()
                .string(
                    Correlation.REQUEST_ID_HEADER,
                    org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}")));
    mvc.perform(get("/api/v1/cart").header(Correlation.REQUEST_ID_HEADER, "client-req-42"))
        .andExpect(header().string(Correlation.REQUEST_ID_HEADER, "client-req-42"));
  }

  @Test
  @DisplayName("problems from the security chain carry a 32-hex traceId")
  void securityProblem_hasTraceId() throws Exception {
    mvc.perform(get("/api/v1/cart"))
        .andExpect(status().isUnauthorized())
        .andExpect(
            jsonPath("$.traceId").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{32}")));
  }

  @Test
  @DisplayName("an API problem's traceId appears in the log line of that request, with no owner id")
  void apiProblem_traceIdIsInLogs(CapturedOutput output) throws Exception {
    MvcResult result =
        mvc.perform(delete("/api/v1/cart/items/999999").with(shopper()))
            .andExpect(status().isNotFound())
            .andReturn();
    String traceId = JsonPath.read(result.getResponse().getContentAsString(), "$.traceId");

    assertThat(traceId).matches("[0-9a-f]{32}");
    assertThat(output.getAll())
        .contains("Request rejected with cart-item-not-found")
        .contains(traceId)
        .doesNotContain("local:shopper");
  }
}
