package com.plantshop.auth.api;

import static com.plantshop.support.TestAuth.githubUser;
import static com.plantshop.support.TestAuth.shopper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plantshop.support.WebMvcSecurityTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MeController.class)
@Import(WebMvcSecurityTestConfig.class)
@Tag("US-01")
class MeControllerTest {

  @Autowired private MockMvc mvc;

  @Test
  @DisplayName("[AC-01.6] a GitHub user sees name and avatar")
  void me_githubUser() throws Exception {
    mvc.perform(get("/api/v1/me").with(githubUser(583231, "octocat")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ownerId").value("github:583231"))
        .andExpect(jsonPath("$.displayName").value("octocat"))
        .andExpect(jsonPath("$.avatarUrl").value("https://avatars.example/583231"))
        .andExpect(jsonPath("$.provider").value("github"));
  }

  @Test
  void me_localUser() throws Exception {
    mvc.perform(get("/api/v1/me").with(shopper()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ownerId").value("local:shopper"))
        .andExpect(jsonPath("$.provider").value("local"))
        .andExpect(jsonPath("$.avatarUrl").doesNotExist());
  }

  @Test
  void me_anonymous_is401() throws Exception {
    mvc.perform(get("/api/v1/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("unauthenticated"));
  }
}
