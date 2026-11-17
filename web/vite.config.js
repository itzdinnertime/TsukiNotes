import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Vitest (npm test): components run in a simulated browser (jsdom); the API is mocked in each test.
  // On Windows, run it from a terminal whose path starts with a capital "C:\" (see README).
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.js',
  },
})
