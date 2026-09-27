import { rmSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * 打包前清理 Server static 输出目录。
 * Windows 上 Vite emptyOutDir（rmdirSync）常因目录非空失败，故改用 rmSync force。
 */
const uiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const outDir = resolve(uiRoot, '../target/classes/static')

try {
  rmSync(outDir, { recursive: true, force: true })
  console.log(`[ui] cleaned ${outDir}`)
} catch (err) {
  console.warn(`[ui] clean skipped: ${err instanceof Error ? err.message : err}`)
}
