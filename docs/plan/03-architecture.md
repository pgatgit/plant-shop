# 03 — Architecture

> Related: [04 backend](04-backend-design.md) · [05 frontend](05-frontend-design.md) · [07 auth](07-auth-design.md) · [08 docker](08-docker-and-environments.md)
> ADRs: [002](adr/002-github-oauth-direct-token.md), [003](adr/003-local-authorization-server-dev-e2e.md), [007](adr/007-cross-origin-cors-topology.md), [008](adr/008-layered-package-by-feature.md)

## 1. System context

```mermaid
flowchart LR
  shopper([Shopper<br/>browser])
  dev([Developer / tester<br/>Swagger UI])
  subgraph PS[Plant Shop]
    spa[Frontend SPA]
    api[Backend API]
  end
  gh[(GitHub OAuth<br/>+ REST API)]
  shopper -->|HTTPS| spa
  shopper -->|API calls, Bearer token| api
  dev -->|Swagger UI| api
  shopper -.->|login redirect - prod| gh
  api -->|code exchange, token check - prod| gh
```

## 2. Containers

```mermaid
flowchart TB
  browser([Browser])
  subgraph compose[docker compose]
    fe["frontend<br/>nginx-unprivileged<br/>:3000→8080<br/>static SPA + /config.json"]
    be["backend<br/>Spring Boot 4 / Java 17<br/>:8080 API · :8081 mgmt"]
    db[("mysql 8.4<br/>:3306<br/>volume mysql-data")]
  end
  gh[(github.com /<br/>api.github.com)]
  browser -->|GET static| fe
  browser -->|"CORS XHR /api/v1/** (Bearer)"| be
  browser -.->|"dev/e2e: /oauth2/authorize, /login"| be
  browser -.->|prod: authorize| gh
  be -->|JDBC + Flyway| db
  be -->|prod: token exchange + check| gh
```

| Container | Responsibility | Tech |
|---|---|---|
| frontend | Serve built SPA, generate `/config.json` from env at start, security headers, SPA fallback | React 19, TS, Vite, nginx |
| backend | REST API (catalog, cart, me), token exchange proxy, token validation, Local AS (dev/e2e), OpenAPI + Swagger UI, Flyway, Actuator | Spring Boot 4, Security 7 (incl. Authorization Server), JPA |
| mysql | Persistent storage of catalog and carts | MySQL 8.4 LTS, utf8mb4 |
| GitHub (external) | Identity provider in prod | OAuth App |

## 3. Backend components

```mermaid
flowchart LR
  subgraph backend
    subgraph security[auth / security]
      resolver[TokenAuthenticationManagerResolver]
      ghIntro[GitHubTokenIntrospector<br/>+ Caffeine cache]
      jwt[Local JwtDecoder<br/>dev/e2e only]
      xchg[TokenExchangeController]
      las[Local Authorization Server<br/>dev/e2e only]
      rl[RateLimitFilter]
    end
    subgraph catalog
      pc[ProductController] --> ps[ProductService] --> pr[ProductRepository]
    end
    subgraph cart
      cc[CartController] --> cs[CartService] --> cr[CartRepository]
      cs --> pr
    end
    me[MeController]
    err[GlobalExceptionHandler<br/>Problem Details]
  end
  resolver --> ghIntro
  resolver --> jwt
  ghIntro --> ghc[GitHubClient] --> GH[(GitHub)]
  xchg --> ghc
  rl --> xchg
  pr --> DB[(MySQL)]
  cr --> DB
```

Responsibilities and rules are detailed in [04 §1–3](04-backend-design.md#1-package-structure).

## 4. Sequence diagrams

### 4.1 Login — prod (GitHub, direct token)

```mermaid
sequenceDiagram
  autonumber
  participant B as Browser (SPA)
  participant GH as github.com
  participant API as Backend
  participant GHA as api.github.com
  B->>B: create state, code_verifier → sessionStorage (transient)
  B->>GH: redirect /login/oauth/authorize?client_id&redirect_uri&scope=read:user&state&code_challenge(S256)
  GH-->>B: 302 /auth/callback?code&state
  B->>B: verify state, read verifier, delete both from sessionStorage
  B->>API: POST /api/v1/auth/token (form: grant_type, code, redirect_uri, client_id, code_verifier) [CORS]
  API->>API: rate-limit, validate client_id + redirect_uri allowlist
  API->>GH: POST /login/oauth/access_token (+client_secret, Accept: json)
  GH-->>API: { access_token, token_type, scope }
  API-->>B: 200 { access_token, token_type: "bearer", scope }
  B->>B: keep token in memory (AuthProvider state)
  B->>API: GET /api/v1/me (Bearer gho_…)
  API->>GHA: POST /applications/{client_id}/token (basic client_id:secret) {access_token}
  GHA-->>API: 200 { user{id,login,name,avatar_url}, app{client_id} }
  API->>API: cache 5 min (key = SHA-256(token)), principal github:{id}
  API-->>B: 200 { ownerId, displayName, avatarUrl, provider: "github" }
```

### 4.2 Login — dev/e2e (Local AS)

```mermaid
sequenceDiagram
  autonumber
  participant B as Browser (SPA)
  participant AS as Backend: Local AS
  participant API as Backend: Resource server
  B->>AS: redirect /oauth2/authorize?client_id=plant-spa&response_type=code&scope=openid catalog.read cart.read cart.write&state&code_challenge
  AS-->>B: 302 /login (form)
  B->>AS: POST /login (username, password, CSRF)
  AS-->>B: 302 /auth/callback?code&state
  B->>AS: POST /oauth2/token (code, code_verifier, client_id) [CORS]
  AS-->>B: { access_token: JWT (15 min), id_token }
  B->>API: GET /api/v1/me (Bearer JWT)
  API->>API: resolver: JWT with iss = local issuer → JwtDecoder (in-process JWK)
  API-->>B: 200 { ownerId: "local:shopper", ... }
```

### 4.3 List products

```mermaid
sequenceDiagram
  participant UI as CatalogPage
  participant RQ as TanStack Query
  participant API as ProductController
  participant S as ProductService
  participant R as ProductRepository
  participant DB as MySQL
  UI->>RQ: useProducts({page:0,size:12,sort:"name,asc"})
  RQ->>API: GET /api/v1/products?page=0&size=12&sort=name,asc
  API->>API: auth (SCOPE_catalog.read), validate params
  API->>S: list(PageQuery)
  S->>R: findAll(Pageable sort name,id)
  R->>DB: SELECT … ORDER BY name, id LIMIT 12 OFFSET 0 (+ COUNT)
  DB-->>R: rows
  S-->>API: Page<Product> → PageResponse<ProductResponse> (MapStruct)
  API-->>RQ: 200 JSON
  RQ-->>UI: data (cached, keepPreviousData during paging)
```

### 4.4 Search

```mermaid
sequenceDiagram
  participant SB as SearchBox
  participant UI as CatalogPage
  participant API as ProductController
  participant S as ProductService
  participant R as ProductRepository
  SB->>SB: normalize(" MON ") → "MON", wait 300 ms
  SB->>UI: setParams(q="MON", page=1)
  UI->>API: GET /api/v1/products?q=MON&page=0&size=12&sort=name,asc (aborts previous)
  API->>S: list(PageQuery{q})
  S->>S: SearchTerm.of(q) — re-normalize, validate 2..50
  S->>R: findByNameStartingWith("MON", pageable)
  Note over R: WHERE name LIKE 'MON%' ESCAPE '\'<br/>collation utf8mb4_0900_ai_ci → case/accent-insensitive<br/>range scan on ix_product_name
  R-->>API: Page<Product>
  API-->>UI: 200 { content:[Monstera Deliciosa], totalElements:1, … }
  UI->>UI: aria-live "1 plant found"
```

### 4.5 Add to cart

```mermaid
sequenceDiagram
  participant C as ProductCard
  participant API as CartController
  participant S as CartService
  participant DB as MySQL
  C->>API: POST /api/v1/cart/items {productId:11, quantity:1} (Bearer)
  API->>API: SCOPE_cart.write, @Valid
  API->>S: addItem(ownerId, 11, 1)
  S->>DB: SELECT product 11
  alt not found
    S-->>API: ProductNotFoundException → 404 Problem
  end
  S->>DB: SELECT cart WHERE owner_id (create if absent)
  S->>S: newQty = existing + 1, limit = min(stock, 10)
  alt newQty > limit
    S-->>API: QuantityLimitExceededException → 409 Problem {limit, requested}
  else ok
    S->>DB: UPSERT cart_item, bump cart.version (optimistic lock)
    S-->>API: CartView (live prices)
    API-->>C: 200 CartResponse
    C->>C: setQueryData(["cart"]) → badge updates, toast
  end
```

## 5. Folder structures

### 5.1 Repository root

```text
/
├── backend/                 # Spring Boot service (own pom, Dockerfile, README)
├── frontend/                # React SPA (own package.json, Dockerfile, README)
├── docs/
│   ├── plan/                # this plan (00–12, adr/ = the project's ADRs, decision-log.md)
│   ├── api/openapi.yaml     # committed OpenAPI snapshot (contract source of truth)
│   ├── architecture.md      # living architecture doc (derived from 03 at M7)
│   ├── security.md          # ASVS L1 checklist (M7)
│   └── runbook.md
├── compose.yaml             # full stack
├── compose.e2e.yaml         # e2e overrides (profile, fixtures, Playwright base URL)
├── .env.example             # documented env vars; .env is git-ignored
├── .github/workflows/ci.yml · .github/dependabot.yml · .github/pull_request_template.md
├── .editorconfig · .gitattributes · .gitignore
└── README.md
```

### 5.2 `backend/`

```text
backend/
├── pom.xml · mvnw · .mvn/ · Dockerfile · .dockerignore · README.md
├── config/checkstyle/checkstyle.xml · config/spotbugs/exclude.xml
└── src/
    ├── main/java/com/plantshop/
    │   ├── PlantShopApplication.java
    │   ├── catalog/   api/ (ProductController, dto/) · service/ · domain/ · repository/ · mapper/
    │   ├── cart/      api/ · service/ · domain/ · repository/ · mapper/ · exception/
    │   ├── auth/      api/ (TokenExchangeController, MeController) · github/ · local/ (@Profile dev|e2e) · security/
    │   └── common/    error/ · web/ (PageResponse, RequestIdFilter, RateLimitFilter) · config/ (OpenApiConfig, ClockConfig)
    ├── main/resources/
    │   ├── application.yml · application-dev.yml · application-e2e.yml · application-prod.yml
    │   ├── db/migration/V1__…sql …        # all environments
    │   └── db/e2e/R__e2e_fixtures.sql      # e2e profile only
    ├── test/java/com/plantshop/…           # *Test.java = unit/slice (Surefire); *IT.java = integration (Failsafe)
    │   └── support/ (MySqlContainerConfig, GitHubWireMock, TestTokens, builders)
    └── test/resources/ (wiremock/github/*.json, fixtures/*.sql)
```

### 5.3 `frontend/`

```text
frontend/
├── package.json · package-lock.json · vite.config.ts · vitest.config.ts · playwright.config.ts
├── tsconfig.json · tsconfig.app.json · tsconfig.node.json · eslint.config.js · .prettierrc.json
├── index.html · Dockerfile · .dockerignore · README.md
├── nginx/ default.conf.template · config.json.template · 40-write-config.sh
├── public/ config.json (dev) · images/plants/*.svg · favicon.svg
├── e2e/ specs/*.spec.ts · fixtures/ (auth.ts, api.ts) · pages/ (page objects)
├── scripts/ check-bundle-size.mjs
└── src/
    ├── main.tsx · App.tsx · router.tsx · styles/index.css
    ├── config/ loadConfig.ts
    ├── api/ client.ts · problem.ts · schema.d.ts (generated from docs/api/openapi.yaml) · products.ts · cart.ts · me.ts
    ├── auth/ AuthProvider.tsx · oauth.ts · pkce.ts · RequireAuth.tsx · CallbackPage.tsx · LoginPage.tsx
    ├── features/catalog/ CatalogPage · ProductGrid · ProductCard · SortSelect · SearchBox · Pagination · useCatalogParams.ts
    ├── features/cart/ CartPage · CartLine · QuantityStepper · CartBadge · useCart.ts
    ├── components/ui/ Button · Alert · Toast · Skeleton · EmptyState · ErrorState · Spinner
    ├── components/layout/ AppShell · Header · UserMenu · SkipLink
    ├── lib/ formatPrice.ts · normalizeQuery.ts
    └── test/ setup.ts · msw/ (handlers.ts, server.ts, fixtures.ts) · render.tsx
```

## 6. Cross-cutting concerns

| Concern | Approach | Detail |
|---|---|---|
| Errors | RFC 9457 Problem Details everywhere except `/api/v1/auth/token` (RFC 6749 error JSON for OAuth-client compatibility) | [04 §5](04-backend-design.md#5-error-model) |
| Config | 12-factor env vars; frontend runtime `/config.json` | [08 §4](08-docker-and-environments.md#4-environment-variables) |
| Logging/metrics | ECS JSON logs, Micrometer/Prometheus | [02 §6](02-non-functional-requirements.md#6-observability) |
| API contract | Code-first springdoc → committed `docs/api/openapi.yaml` → TS types | [11 §4](11-documentation-plan.md#4-openapi--swagger-conventions) |
| Time | Injected `Clock` bean (testable) | [10](10-code-quality-and-conventions.md) |
