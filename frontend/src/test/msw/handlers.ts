import type { AnyHandler } from 'msw';

/**
 * Default (happy-path) API handlers shared by all component tests. Tests override them per case
 * with `server.use(...)`. Handlers are added per milestone as endpoints appear.
 */
export const handlers: AnyHandler[] = [];
