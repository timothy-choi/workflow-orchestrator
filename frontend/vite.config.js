import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const api = process.env.VITE_PROXY_TARGET || 'http://localhost:8082';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/workflows': api,
      '/executions': api,
      '/step-executions': api,
    },
  },
});
