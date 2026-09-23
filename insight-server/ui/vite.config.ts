import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// Insight Server 控制台（源码位于 insight-server/ui）
export default defineConfig({
  // 控制台挂在根路径 /，与业务侧 Agent 解耦
  base: '/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api/v1': {
        // 本地联调默认指向 insight-server（9966）
        target: 'http://localhost:9966',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false
  }
})
