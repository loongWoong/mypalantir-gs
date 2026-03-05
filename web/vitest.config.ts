import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

const allureResultsDir = path.join(__dirname, '..', 'test-reports', 'allure-results');

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['allure-vitest/setup'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      reportsDirectory: path.join(__dirname, '..', 'test-reports', 'frontend', 'coverage'),
      exclude: ['node_modules/', '**/*.d.ts', '**/*.config.*', 'src/main.tsx'],
    },
    reporters: [
      'default',
      'junit',
      'html',
      ['allure-vitest/reporter', { resultsDir: allureResultsDir }],
    ],
    outputFile: {
      junit: path.join(__dirname, '..', 'test-reports', 'frontend', 'junit.xml'),
      html: path.join(__dirname, '..', 'test-reports', 'frontend', 'index.html'),
    },
  },
});
