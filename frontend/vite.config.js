import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ command, mode }) => {
  // A production build without VITE_API_BASE_URL would silently call http://localhost:8080/api.
  const env = loadEnv(mode, '.', 'VITE_')
  if (command === 'build' && !env.VITE_API_BASE_URL) {
    console.warn('\n[CareerAI] VITE_API_BASE_URL is not set: this build will call http://localhost:8080/api.\n'
      + '           Set it to the deployed backend, e.g. https://api.example.com/api\n')
  }

  return {
    plugins: [react()],
  }
})
