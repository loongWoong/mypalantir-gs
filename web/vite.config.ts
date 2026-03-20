import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0', // 允许远程访问
    port: 5174,
    strictPort: false,
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
})
