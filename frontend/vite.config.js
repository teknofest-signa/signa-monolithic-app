import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The console talks to the producer service. In development everything under
// /api is proxied so the browser sees one origin and CORS stays out of the way.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.SIGNA_API_URL || 'http://localhost:9090',
        changeOrigin: true,
      },
    },
  },
});
