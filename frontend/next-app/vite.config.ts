import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
// - base relativa: o mesmo build funciona servido pelo BFF, por proxy reverso ou em subpasta (GitHub Pages).
// - em desenvolvimento, /api é encaminhado ao next-bff (porta 8080), mantendo a mesma origem.
export default defineConfig({
  base: './',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: process.env.NEXT_BFF_URL ?? 'http://localhost:8080', changeOrigin: false },
    },
  },
  build: {
    outDir: process.env.NEXT_OUT_DIR ?? 'dist',
    emptyOutDir: true,
    sourcemap: false,
  },
})
