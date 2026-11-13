# ADR-004: SPA uses Authorization Code + PKCE with tokens held in memory

- Status: Accepted
- Date: 2026-11-13
- Decision log: D5, R1-Q2

## Context

The SPA is a public client on its own origin. Options were in-memory tokens, a BFF with httpOnly cookies, or sessionStorage.

## Options considered

1. **PKCE, in-memory** — standard SPA pattern, simple CORS; XSS can read token while page is open. *(chosen)*
2. BFF + httpOnly cookie — strongest browser security per IETF guidance; needs CSRF and same-site proxying across ports.
3. PKCE + sessionStorage — survives reloads; weaker vs XSS.

## Decision

Tokens live only in React state. `sessionStorage` holds just the PKCE transaction (state, verifier, returnTo) between redirect and callback. Reload/401 → (silent) re-login. Strict CSP and lint bans on raw HTML reduce XSS risk. Library: `oauth4webapi`.

## Consequences

- Positive: no token persistence; no cookies on API; clear CORS story.
- Negative: re-login on every reload; GitHub tokens are non-expiring, so an XSS-stolen token stays valid until revoked.
