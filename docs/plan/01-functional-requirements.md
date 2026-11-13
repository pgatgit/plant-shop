# 01 — Functional Requirements

> Related: [00 overview](00-overview.md) · [04 API contract](04-backend-design.md#4-rest-api-contract) · [05 UX](05-frontend-design.md) · [09 traceability](09-testing-strategy.md#8-traceability-matrix)

## 1. Roles

| Role | How obtained | Can |
|---|---|---|
| Anonymous | — | Nothing except reach the login redirect, `/login`, health endpoints (D6) |
| **Shopper** | Any GitHub user (prod) or in-memory user with all scopes (dev/e2e) | Browse, search, use own cart. Scopes `catalog.read cart.read cart.write` |
| Viewer *(dev/e2e only)* | In-memory user `viewer` with `catalog.read` only | Browse/search; cart calls return 403. Exists to test scope enforcement |

## 2. User stories & acceptance criteria

### US-01 Sign in

*As a visitor I want to sign in so that I can use the store.*

- **AC-01.1** Given I am not signed in, when I open any app URL (e.g. `/cart`), then I am redirected to the configured authorization endpoint, and after successful login I land back on the URL I originally requested.
- **AC-01.2** Given profile `prod`, when I am redirected, then it is GitHub's authorize page requesting scope `read:user`.
- **AC-01.3** Given profile `dev`/`e2e`, when I am redirected, then I see the Local AS login form and can sign in with a configured in-memory user.
- **AC-01.4** Given I deny consent or the provider returns `error`, when I return to `/auth/callback`, then I see `/login` with a readable error and a "Try again" button; no token is stored.
- **AC-01.5** Given the `state` parameter in the callback does not match the one I sent, then login is rejected with a "Login could not be verified" error.
- **AC-01.6** Given I am signed in, then the header shows my display name (and avatar in prod) from `GET /api/v1/me`.
- **AC-01.7** Given I reload the page, then I must re-authenticate (token is memory-only, D5); the provider may approve silently and I land on the same URL.

### US-02 Sign out

- **AC-02.1** Given I am signed in, when I choose "Sign out", then the in-memory token and all cached server data are cleared and I see `/login` with "You have been signed out".
- **AC-02.2** Given I signed out, when I press Back, then no cart or catalog data is rendered from cache.
- **AC-02.3** Given profile `dev`/`e2e`, signing out also ends the Local AS session (`/logout`) so the next login shows the form.

### US-03 Browse the catalog

- **AC-03.1** Given I am signed in, when I open `/plants`, then I see page 1 with up to **12** product cards sorted by name A–Z.
- **AC-03.2** Each card shows: image (with alt text), name, category, price in USD (`$12.99`), exact stock (`12 in stock` / `Out of stock`), light needs, watering, pet-safety badge, description clamped to 3 lines with a "Show more" disclosure, and "Add to cart".
- **AC-03.3** Given there are 28 products, then pagination shows "Page 1 of 3" and Previous/Next/numbered controls; the current page is reflected in the URL (`?page=2`, 1-based) and is linkable.
- **AC-03.4** Given I open `?page=99`, then I am redirected (history replace) to the last existing page.
- **AC-03.5** Given `?page=abc` or `?page=0`, then page 1 is shown.
- **AC-03.6** While loading I see skeleton cards; on failure I see an error state with "Retry".

### US-04 Sort the catalog

- **AC-04.1** Sort options: *Name A–Z* (default), *Name Z–A*, *Price low–high*, *Price high–low*; reflected in URL `?sort=name-asc|name-desc|price-asc|price-desc`.
- **AC-04.2** Changing sort resets to page 1.
- **AC-04.3** Items with equal price are ordered by name then id (deterministic across pages).
- **AC-04.4** Given an unknown `sort` URL value, then the default sort is used.

### US-05 Search products by name

- **AC-05.1** Given I type `mon` in the search box, when 300 ms pass without typing, then I see only products whose **name starts with** "mon" ignoring case — e.g. *Monstera Deliciosa* — and **not** *Swiss Cheese Monstera* (prefix-only, D9).
- **AC-05.2** `MON`, `Mon` and `mon` return identical results; accented and unaccented forms match (`creme` matches a name starting `Crème…`; verified with test-only fixture rows since the seed catalog has no accented names — [09 §5](09-testing-strategy.md#5-test-data-strategy)).
- **AC-05.3** Input is trimmed and inner whitespace collapsed (`"  peace   li "` → `peace li`).
- **AC-05.4** Given fewer than 2 characters after normalisation, then no search is sent and the full catalog is shown, with hint "Type at least 2 characters".
- **AC-05.5** Search combines with sort and resets to page 1; URL holds `?q=`.
- **AC-05.6** Given no match, then I see "No plants start with "xyz"" and a "Clear search" button.
- **AC-05.7** `%`, `_` and `\` are treated literally (searching `%` does not return everything).
- **AC-05.8** Input max length 50; result count is announced to screen readers ("3 plants found").

### US-06 Add a product to the cart

- **AC-06.1** Given a product with stock ≥ 1 and not in my cart, when I click "Add to cart", then a line with quantity 1 is created, the header badge increments, and a status toast "Added Boston Fern to cart" is announced.
- **AC-06.2** Given the product is already in my cart, when I add it again, then that line's quantity increases by 1 (no duplicate line).
- **AC-06.3** Given the line would exceed `min(stock, 10)`, then the server answers 409 and I see an alert such as "You can have at most 3 of Bird's Nest Fern in your cart"; the cart is unchanged.
- **AC-06.4** Given stock is 0, then "Add to cart" is disabled and labelled "Out of stock".
- **AC-06.5** While the request is pending, the button is disabled (no double-submit).
- **AC-06.6** Given the product no longer exists, then 404 → alert "This plant is no longer available".

### US-07 View the cart

- **AC-07.1** `/cart` lists lines with image, name, **current** unit price, quantity, line total; plus total item count and subtotal (USD).
- **AC-07.2** Prices are always live (D7): if a product's price changed since adding, the new price is shown with no notice.
- **AC-07.3** Given a line's quantity now exceeds stock, then the line shows a warning ("Only 2 left — reduce quantity"); given stock is 0, "Out of stock — remove this item". Lines are never auto-modified.
- **AC-07.4** Given the cart is empty, then I see "Your cart is empty" with a link to the catalog.
- **AC-07.5** The header badge shows total item count (sum of quantities) on every page.

### US-08 Update a line quantity

- **AC-08.1** A quantity stepper (− / value / +) allows 1…`maxQuantity`; + is disabled at the limit, − at 1.
- **AC-08.2** Changes are sent to the server (non-optimistic); the line shows a pending state; totals update from the response.
- **AC-08.3** Given the server rejects (409, e.g. stock dropped), then the previous value is restored and an alert explains why.
- **AC-08.4** Quantity 0 is not accepted (use Remove).

### US-09 Remove a line

- **AC-09.1** Each line has a "Remove" button labelled "Remove {name} from cart"; activating it removes the line without confirmation.
- **AC-09.2** Focus moves to the next line's Remove button, or to the cart heading if none remain.
- **AC-09.3** Removing a product not in the cart returns 404 and the UI refreshes the cart.

## 3. Business rules

| ID | Rule | Enforced in |
|---|---|---|
| BR-01 | Every API except `/api/v1/auth/token`, health, OpenAPI/Swagger (when enabled) and Local AS endpoints requires authentication | Backend security; SPA guard |
| BR-02 | Page size default 12, allowed 1–48; page index 0-based in API, 1-based in UI URL | API validation; SPA mapping |
| BR-03 | Sort whitelist: `name`, `price` × `asc`/`desc`; tie-breakers `name`, `id` | API |
| BR-04 | Search normalisation: trim, collapse whitespace, length 2–50 after normalisation, else 400 | API + SPA (same algorithm, tested in both) |
| BR-05 | Search semantics: prefix on `name`, case- & accent-insensitive via `utf8mb4_0900_ai_ci`; LIKE wildcards escaped | Repository |
| BR-06 | One cart per owner, created lazily on first add; never expires | Cart service |
| BR-07 | Re-adding a product increments its line | Cart service |
| BR-08 | Line quantity ∈ [1, `min(stock,10)`]; violation → 409 `quantity-limit-exceeded` (or `out-of-stock` when stock = 0) | Cart service |
| BR-09 | Prices are live: unit price read from `product` at read time; no snapshots | Cart read model |
| BR-10 | Money: `DECIMAL(10,2)`/`BigDecimal`, USD; line total = unit × qty, subtotal = Σ line totals, scale 2, `HALF_UP` | Domain |
| BR-11 | Stock is not reserved by carts | — |
| BR-12 | Each line reports `stockStatus`: `OK`, `INSUFFICIENT` (qty > stock > 0), `OUT_OF_STOCK` | Cart read model |
| BR-13 | Users only ever see/modify their own cart (owner = token subject) | Service layer; no owner id in URLs |

## 4. Edge cases

| Case | Expected |
|---|---|
| Concurrent adds of the same product from two tabs | Unique `(cart_id, product_id)` + optimistic locking; loser retried once server-side, then 409 `concurrent-modification` |
| Stock decreases below an existing line's quantity | Line flagged `INSUFFICIENT`; further increments rejected; decrement allowed |
| Token expires mid-session (local JWT 15 min) | API 401 → SPA starts a silent re-login keeping current URL |
| GitHub unavailable on a cache miss | 503 `auth-provider-unavailable` with `Retry-After`; cached tokens continue to work |
| Malformed JSON / wrong types | 400 `malformed-request` |
| Unknown query params | Ignored |
| `size` > 48 or < 1 | 400 `validation-failed` |
| Product id non-numeric in path | 400 |
| Very long search input pasted | SPA truncates at 50; API rejects > 50 |
| Swagger "Try it out" without authorising | 401 Problem Details |
