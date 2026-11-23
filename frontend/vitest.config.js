import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// Frontend unit/regression tests (jsdom, no network: every backend call is mocked).
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{js,jsx}'],
    restoreMocks: true,
  },
})
