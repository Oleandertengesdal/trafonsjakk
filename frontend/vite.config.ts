import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// In development, /api is proxied to the Spring Boot backend so cookies stay same-origin,
// exactly like the Vercel rewrite does in production.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: 5173,
      proxy: {
        '/api': { target: env.VITE_DEV_API_TARGET || 'http://localhost:8080', changeOrigin: false },
      },
    },
    build: { sourcemap: false },
  }
})
