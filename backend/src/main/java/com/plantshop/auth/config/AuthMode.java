package com.plantshop.auth.config;

/** Where access tokens come from (docs/plan/07-auth-design.md §1). */
public enum AuthMode {
  /** GitHub OAuth App: opaque GitHub tokens validated with GitHub's "check a token" API. */
  GITHUB,
  /** Embedded Spring Authorization Server with in-memory users (dev/e2e profiles only). */
  LOCAL
}
