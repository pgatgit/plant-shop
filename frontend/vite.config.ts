import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// Dev server and preview run on 127.0.0.1:3000 so origins and OAuth redirect URIs match
// docs/plan/07-auth-design.md §4 exactly.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: { host: '127.0.0.1', port: 3000, strictPort: true },
  preview: { host: '127.0.0.1', port: 3000, strictPort: true },
  build: { sourcemap: true },
});
