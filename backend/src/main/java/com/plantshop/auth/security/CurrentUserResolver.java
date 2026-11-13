package com.plantshop.auth.security;

import com.plantshop.auth.github.GitHubTokenIntrospector;
import java.util.Map;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Maps a Spring Security authentication to the {@link CurrentUser} the application uses. */
@Component
public class CurrentUserResolver {

  /**
   * Resolves the current user.
   *
   * @param authentication the authentication of the current request
   * @return the user
   * @throws InsufficientAuthenticationException when the authentication is not a supported token
   */
  public CurrentUser resolve(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      Jwt jwt = jwtAuth.getToken();
      return new CurrentUser("local:" + jwt.getSubject(), jwt.getSubject(), null, "local");
    }
    if (authentication instanceof BearerTokenAuthentication bearer
        && bearer.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
      Map<String, Object> attributes = principal.getAttributes();
      Object name = attributes.get(GitHubTokenIntrospector.ATTR_NAME);
      Object login = attributes.get(GitHubTokenIntrospector.ATTR_LOGIN);
      Object avatar = attributes.get(GitHubTokenIntrospector.ATTR_AVATAR_URL);
      return new CurrentUser(
          principal.getName(),
          String.valueOf(name != null ? name : login),
          avatar == null ? null : avatar.toString(),
          "github");
    }
    throw new InsufficientAuthenticationException("Unsupported authentication type");
  }
}
