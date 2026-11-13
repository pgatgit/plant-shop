# 05 — Frontend Design

> Related: [01 stories](01-functional-requirements.md) · [03 folder structure](03-architecture.md#53-frontend) · [04 API](04-backend-design.md#4-rest-api-contract) · [07 auth](07-auth-design.md)
> ADRs: [004](adr/004-spa-token-in-memory-pkce.md), [010](adr/010-frontend-stack.md), [012](adr/012-runtime-frontend-config.md)

## 1. Stack (D11)

| Concern | Choice |
|---|---|
| Language | TypeScript `strict`, `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes` |
| Build | Vite (dev server on **127.0.0.1:3000**, `strictPort`) |
| Routing | React Router (data-less "declarative" mode) |
| Server state | TanStack Query (only cache of server data) |
| Client state | React context: `AuthProvider` (token, user, login/logout); URL search params for catalog state. No global store |
| UI | Tailwind CSS v4 (`@tailwindcss/vite`) + Radix primitives (`Select`, `DropdownMenu`, `Toast`, `VisuallyHidden`) |
| OAuth client | `oauth4webapi` (spec-compliant PKCE/state helpers; no token storage of its own) |
| API types | `openapi-typescript` generated `schema.d.ts` from `docs/api/openapi.yaml` + thin typed `fetch` wrapper |
| Forms | None needed (search box + steppers); controlled inputs, no form library |

## 2. Routes

| Path | Component | Guard | Notes |
|---|---|---|---|
| `/` | redirect → `/plants` | — | |
| `/plants` | `CatalogPage` | `RequireAuth` | URL params `page` (1-based), `sort` (`name-asc`…), `q` |
| `/cart` | `CartPage` | `RequireAuth` | |
| `/auth/callback` | `CallbackPage` | — | Handles `code`/`state`/`error` |
| `/login` | `LoginPage` | — | Shown only after logout or login error; "Sign in" button |
| `*` | `NotFoundPage` | `RequireAuth` | |

`RequireAuth`: if no token → store `returnTo` (path + search) in `sessionStorage` with the PKCE transaction, then `window.location.assign(authorizeUrl)`. Shows a full-page "Signing you in…" status while redirecting.

## 3. Component tree

```text
<App>
 ├─ <QueryClientProvider>
 │   └─ <AuthProvider config>               token in memory, user from /me
 │       └─ <Toast.Provider>
 │           └─ <RouterProvider>
 │               ├─ <AppShell>              <SkipLink/> <Header/> <main id="main">
 │               │   ├─ <Header>            logo, <CartBadge/> (count), <UserMenu/> (name, avatar, Sign out)
 │               │   ├─ CatalogPage
 │               │   │   ├─ <SearchBox/>    label, clear button, hint, aria-describedby
 │               │   │   ├─ <SortSelect/>   Radix Select
 │               │   │   ├─ <ResultStatus/> aria-live="polite" ("12 plants · page 1 of 3")
 │               │   │   ├─ <ProductGrid>   <ul> of <ProductCard/> | skeletons | EmptyState | ErrorState
 │               │   │   │   └─ <ProductCard/> image, name, category, price, stock, light, watering, pet badge,
 │               │   │   │                    <ClampedText/> (Show more/less), <AddToCartButton/>
 │               │   │   └─ <Pagination/>   <nav aria-label="Pagination">, aria-current="page"
 │               │   ├─ CartPage
 │               │   │   ├─ <h1 tabIndex=-1>Your cart</h1>
 │               │   │   ├─ <ul> <CartLine/>  image, name, unit price, <QuantityStepper/>, line total, stock warning, Remove
 │               │   │   └─ <CartSummary/>   itemCount, subtotal
 │               │   └─ NotFoundPage
 │               ├─ CallbackPage / LoginPage (no shell header actions)
 │               └─ <Toaster/>              Radix Toast region (status messages)
```

## 4. Data flow & caching

| Query key | Fetcher | staleTime | Notes |
|---|---|---|---|
| `['me']` | `GET /me` | ∞ (until logout) | Loaded after token obtained |
| `['products', {page,size,sort,q}]` | `GET /products` | 30 s | `placeholderData: keepPreviousData`; AbortSignal passed so superseded searches are cancelled |
| `['cart']` | `GET /cart` | 0 | Badge + cart page share it |

Mutations (`useAddToCart`, `useUpdateQuantity`, `useRemoveItem`) are **non-optimistic**: on success `queryClient.setQueryData(['cart'], response)`; on 409/404 show the Problem `detail` in an alert and invalidate `['cart']` and the current products page (stock may have changed). Retries: queries retry 1× on network/5xx only; never on 4xx; mutations never auto-retry.

**API client** (`api/client.ts`): injects `Authorization: Bearer`, sets `Accept`, parses `application/problem+json` into a typed `ApiProblem` error; on **401** triggers `auth.reauthenticate()` (single-flight) which redirects to login preserving the current URL. 503 `auth-provider-unavailable` shows a banner "Sign-in service is temporarily unavailable" with retry.

**Search** (`useCatalogParams` + `SearchBox`): local input state → `normalizeQuery` (same algorithm as BR-04, shared test vectors with backend) → `useDebouncedValue(300)` → if length ≥ 2 write `q` to URL (replace history entry while typing) and reset `page`; if < 2 remove `q`. Input `maxLength=50`.

**Catalog URL mapping**: UI `page` 1-based ↔ API 0-based; `sort=price-desc` ↔ `price,desc`. Invalid values fall back to defaults (AC-03.5, AC-04.4); page beyond `totalPages` → `navigate(replace)` to last page.

## 5. Auth integration

`AuthProvider` holds `{ accessToken, expiresAt?, user }` in React state only. `config.json` decides the provider:

```json
{
  "apiBaseUrl": "http://127.0.0.1:8080/api/v1",
  "auth": {
    "provider": "local",
    "authorizeUrl": "http://127.0.0.1:8080/oauth2/authorize",
    "tokenUrl": "http://127.0.0.1:8080/oauth2/token",
    "logoutUrl": "http://127.0.0.1:8080/logout",
    "clientId": "plant-spa",
    "scope": "openid catalog.read cart.read cart.write",
    "redirectUri": "http://127.0.0.1:3000/auth/callback"
  }
}
```

For `provider: "github"`: `authorizeUrl=https://github.com/login/oauth/authorize`, `tokenUrl=http://127.0.0.1:8080/api/v1/auth/token`, `scope=read:user`, no `logoutUrl`. The code path is identical; only config differs.

Local JWTs (15 min): a timer 60 s before `exp` triggers silent re-authentication via full redirect (AS session still valid → no form). GitHub tokens don't expire; logout or reload ends them client-side.

## 6. UX states

| Surface | Loading | Empty | Error |
|---|---|---|---|
| Catalog | 12 skeleton cards (`aria-busy`), previous page kept while paging | "No plants start with "x"" + Clear search; "No plants available" if catalog empty | `ErrorState` with Problem detail/generic text + Retry |
| Add to cart | Button disabled + spinner + "Adding…" (SR) | — | Inline `Alert role="alert"` under card (409/404), auto-dismiss never |
| Cart | Skeleton lines | "Your cart is empty" + link to catalog | ErrorState + Retry |
| Stepper | Line `aria-busy`, controls disabled | — | Revert value + alert |
| Auth | "Signing you in…" full page | — | `/login?error=` friendly message + "Try again" |
| Offline | Banner "You appear to be offline" (`navigator.onLine` + events) | | |

## 7. Accessibility (WCAG 2.2 AA — NFR-A11Y-*)

Semantic landmarks (`header`, `nav`, `main`), one `h1` per page, focus moved to `h1` on route change, skip link, labelled search (`<label>` visible), Radix components for Select/Menu keyboard patterns, `aria-live` regions for result counts and cart status, `role="alert"` for errors, stock/pet info always as text (icons `aria-hidden`), images with meaningful `alt` ("Illustration of a Boston Fern"), quantity stepper as `group` with labelled buttons ("Increase quantity of Boston Fern"), focus management on remove (AC-09.2), `prefers-reduced-motion` disables transitions, min target 24×24 (we use 40×40).

## 8. Styling conventions

Tailwind design tokens in `styles/index.css` (`@theme`: brand greens, neutral scale verified for contrast, radius, spacing). No arbitrary values in components except documented exceptions. Component variants via a small `cva`-free helper (`clsx` only). `prettier-plugin-tailwindcss` sorts classes.

## 9. Performance

Route-level code splitting (`CartPage` lazy); images: SVG with explicit `width/height`, `loading="lazy"` below the fold; no web fonts (system font stack); bundle budget enforced in CI ([02 NFR-PERF-05](02-non-functional-requirements.md#1-performance)).

## 10. Config

Runtime `/config.json` fetched before React renders (`loadConfig()`; on failure render a static error). Dev uses `public/config.json`; container writes it from env at start ([08 §2.2](08-docker-and-environments.md#22-frontend-dockerfile), [ADR-012](adr/012-runtime-frontend-config.md)). No `VITE_*` build-time secrets or URLs.
