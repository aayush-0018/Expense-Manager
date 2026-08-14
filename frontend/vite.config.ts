/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'

const API_PROXY = {
  '/api': {
    target: process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
    changeOrigin: true,
  },
}

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(import.meta.dirname, './src') },
  },
  // Proxying /api keeps the browser on one origin, so CORS never enters the picture and
  // headers behave the same as they would behind a reverse proxy in production.
  server: {
    port: 5173,
    proxy: API_PROXY,
  },
  // `vite preview` serves the built bundle; without the same proxy every /api call from it
  // would 404, so the production build could not be smoke-tested locally.
  preview: {
    port: 4173,
    proxy: API_PROXY,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
    css: false,
  },
})
