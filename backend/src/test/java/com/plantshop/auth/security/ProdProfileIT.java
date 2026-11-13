package com.plantshop.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plantshop.support.MySqlTestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** NFR-SEC-08 / threat T7: the prod profile has no Local AS, no users and no Swagger. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Import(MySqlTestcontainersConfiguration.class)
class ProdProfileIT {

  @Autowired private MockMvc mvc;
  @Autowired private ApplicationContext context;

  @Test
  @DisplayName("[NFR-SEC-08] no Local AS clients or in-memory users exist in prod")
  void localAuthorizationServer_isAbsent() {
    assertThat(context.getBeanNamesForType(RegisteredClientRepository.class)).isEmpty();
    assertThat(context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
  }

  @Test
  @DisplayName("[NFR-SEC-08] Local AS endpoints and the login page are denied in prod")
  void localEndpoints_areDenied() throws Exception {
    mvc.perform(get("/oauth2/authorize").param("client_id", "plant-spa"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist("Location"));
    mvc.perform(get("/login")).andExpect(status().isUnauthorized());
    mvc.perform(get("/oauth2/jwks")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("[D20] Swagger is off in prod by default")
  void swagger_isOff() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
  }
}
