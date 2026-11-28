import { defineConfig } from 'vite';

export default defineConfig({
  esbuild: { jsx: 'automatic' },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api/slots': 'http://localhost:8081',
      '/api/holds': 'http://localhost:8081',
      '/api/orders': 'http://localhost:8082',
      '/api/payments': 'http://localhost:8083',
      '/api/ledger': 'http://localhost:8083',
    },
  },
});
