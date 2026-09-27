import { defineConfig, createLogger, type Logger } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

/**
 * Maven/Windows 默认控制台常为 GBK：Vite 的 ✓/✗ 等 UTF-8 符号会被显示成「鉁?」。
 * 构建日志改为 ASCII，避免打包输出显示乱码
 */
function asciiBuildLogger(): Logger {
  const base = createLogger()
  const scrub = (msg: string) =>
    msg
      .replace(/\u2713/g, '[OK]') // ✓
      .replace(/\u2714/g, '[OK]') // ✔
      .replace(/\u2717/g, '[X]') // ✗
      .replace(/\u2718/g, '[X]') // ✘
      .replace(/\u26A0/g, '[!]') // ⚠
      .replace(/\u2139/g, '[i]') // ℹ
  return {
    ...base,
    info: (msg, opts) => base.info(scrub(msg), opts),
    warn: (msg, opts) => base.warn(scrub(msg), opts),
    warnOnce: (msg, opts) => base.warnOnce(scrub(msg), opts),
    error: (msg, opts) => base.error(scrub(msg), opts)
  }
}

// Insight Server 控制台（源码位于 insight-server/ui）
export default defineConfig({
  // 控制台挂在根路径 /，与业务侧 Agent 解耦
  base: '/',
  customLogger: asciiBuildLogger(),
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
