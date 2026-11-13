import '@testing-library/jest-dom/vitest';
// Vitest runs without globals, so RTL's automatic cleanup is not registered; do it explicitly.
// eslint-disable-next-line testing-library/no-manual-cleanup
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, expect } from 'vitest';
import * as axeMatchers from 'vitest-axe/matchers';
import { server } from './msw/server';

expect.extend(axeMatchers);

// axe-core probes <canvas>; jsdom does not implement it. Returning null keeps output clean.
HTMLCanvasElement.prototype.getContext = () => null;

beforeAll(() => {
  server.listen({ onUnhandledFrame: 'error' });
});
afterEach(() => {
  cleanup();
  server.resetHandlers();
});
afterAll(() => {
  server.close();
});
