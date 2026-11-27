import { defineConfig } from 'vite';

export default defineConfig({
  root: 'web',
  server: {
    host: '0.0.0.0',
    strictPort: true,
    port: 5173
  },
  build: {
    outDir: '../dist',
    emptyOutDir: true
  }
});
