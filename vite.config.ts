import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  // 关键：相对路径，确保打包成 APK 后资源能正确加载
  base: './',
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    // 关键：兼容低端安卓 WebView 内核
    target: 'chrome79',
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        // 代码分割，减小首包体积
        manualChunks: {
          'vendor-react': ['react', 'react-dom', 'react-router-dom'],
          'vendor-state': ['zustand'],
          'vendor-utils': ['idb', 'framer-motion'],
        },
      },
    },
  },
});
