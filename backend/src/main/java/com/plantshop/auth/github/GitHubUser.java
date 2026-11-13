package com.plantshop.auth.github;

/**
 * The GitHub user a valid token belongs to.
 *
 * @param id numeric GitHub user id (stable; logins can change)
 * @param login GitHub login
 * @param name display name, may be {@code null}
 * @param avatarUrl avatar URL, may be {@code null}
 */
public record GitHubUser(long id, String login, String name, String avatarUrl) {

  /**
   * Returns the cart owner id for this user.
   *
   * @return {@code github:{id}}
   */
  public String ownerId() {
    return "github:" + id;
  }
}
