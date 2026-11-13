-- E2E profile only: pin stock values that Playwright specs rely on, independent of seed edits
-- (docs/plan/06-data-model-and-migrations.md §4.1). Idempotent.
UPDATE product SET stock = 3 WHERE sku = 'FRN-BIRDNEST';
UPDATE product SET stock = 0 WHERE sku = 'FRN-MAIDENHAIR';
UPDATE product SET stock = 50 WHERE sku = 'TRL-SPIDER';
