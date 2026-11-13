## What & why

<!-- One paragraph. Link the user story / business rule (e.g. US-06, BR-08). -->

## Linked story / ADR

- Story/rule:
- ADR (if a decision changed):

## How tested

<!-- Layers covered (unit, slice, IT, contract, E2E) and the commands you ran. -->

## Screenshots (UI changes)

## Checklist (Definition of Done — docs/plan/09-testing-strategy.md §9)

- [ ] Acceptance criteria automated at the planned layers, tests tagged with IDs
- [ ] `./mvnw verify` and `npm run ci` green; E2E green on the compose stack
- [ ] OpenAPI snapshot and generated TS types updated (if the API changed)
- [ ] Flyway migrations reviewed (immutable, forward-only, indexes justified)
- [ ] Docs updated (README/runbook/ADR/decision log/traceability matrix)
- [ ] No secrets, tokens or personal data in code, logs or fixtures
