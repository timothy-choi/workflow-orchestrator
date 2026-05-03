import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const api = process.env.VITE_PROXY_TARGET || 'http://localhost:8082';

const apiProxy = {
  '/workflows': api,
  '/executions': api,
  '/step-executions': api,
};

// Dev: browser navigations must not use `/executions/:id` as a UI path — that prefix is proxied
// to Spring and returns JSON on refresh. UI detail route is `/ui/executions/:id`; API calls stay
// `fetch('/executions/...')` and are proxied.

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: apiProxy,
  },
  preview: {
    proxy: apiProxy,
  },
});
