import { setupServer } from 'msw/node';
import { handlers } from './handlers';

/** MSW server used by Vitest; unhandled requests fail the test. */
export const server = setupServer(...handlers);
