package com.plantshop.auth.security;

/** OAuth2 scopes of the Plant Shop API (docs/plan/07-auth-design.md §6). */
public final class Scopes {

  /** Read the catalog. */
  public static final String CATALOG_READ = "catalog.read";

  /** Read the own cart. */
  public static final String CART_READ = "cart.read";

  /** Modify the own cart. */
  public static final String CART_WRITE = "cart.write";

  private Scopes() {}

  /**
   * Returns the Spring Security authority for a scope.
   *
   * @param scope the scope
   * @return {@code SCOPE_<scope>}
   */
  public static String authority(String scope) {
    return "SCOPE_" + scope;
  }
}
