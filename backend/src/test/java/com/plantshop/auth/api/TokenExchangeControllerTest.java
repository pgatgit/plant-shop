package com.plantshop.auth.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plantshop.auth.github.GitHubUnavailableException;
import com.plantshop.auth.github.OAuthErrorException;
import com.plantshop.auth.github.TokenExchangeService;
import com.plantshop.support.WebMvcSecurityTestConfig;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TokenExchangeController.class)
@Import(WebMvcSecurityTestConfig.class)
@Tag("US-01")
class TokenExchangeControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private TokenExchangeService service;

  private org.springframework.test.web.servlet.ResultActions exchange() throws Exception {
    return mvc.perform(
        post("/api/v1/auth/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("grant_type", "authorization_code")
            .param("code", "c"));
  }

  @Test
  void success_isPublicAndNeverCached() throws Exception {
    when(service.exchange(any()))
        .thenReturn(Map.of("access_token", "gho_x", "token_type", "bearer"));

    exchange()
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("Pragma", "no-cache"))
        .andExpect(jsonPath("$.access_token").value("gho_x"));
  }

  @Test
  void oauthError_usesRfc6749Body() throws Exception {
    when(service.exchange(any())).thenThrow(new OAuthErrorException("invalid_grant", "expired"));

    exchange()
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.error").value("invalid_grant"))
        .andExpect(jsonPath("$.error_description").value("expired"));
  }

  @Test
  void githubDown_is502TemporarilyUnavailable() throws Exception {
    when(service.exchange(any())).thenThrow(new GitHubUnavailableException("down", null));

    exchange()
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("temporarily_unavailable"));
  }
}
