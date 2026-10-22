import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    proxy: { '/api': 'http://127.0.0.1:18080', '/actuator': 'http://127.0.0.1:18080' },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: true,
    // Playwright owns browser specs; Vitest must not try to execute them as
    // unit tests (its test() lifecycle is a different runner).
    exclude: ['tests/e2e/**', 'node_modules/**'],
  },
});
