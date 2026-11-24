#!/usr/bin/env node
/**
 * 抽检：控制台 ApiService / AuthService 使用的路径，是否出现在 OpenAPI（springdoc）中。
 *
 * 用法：
 *   node scripts/check-ui-api-paths.mjs
 *   node scripts/check-ui-api-paths.mjs --url http://localhost:9966/v3/api-docs
 *   node scripts/check-ui-api-paths.mjs --file path/to/api-docs.json
 *
 * 故意改错 ApiService 某一路径后应非 0 退出。
 */

import { readFileSync, existsSync, readdirSync, statSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(__dirname, '..')
const defaultApiService = resolve(repoRoot, 'insight-server/ui/src/services/ApiService.ts')
const defaultAuthService = resolve(repoRoot, 'insight-server/ui/src/services/AuthService.ts')
const defaultUrl = 'http://localhost:9966/v3/api-docs'

/**
 * @param {string[]} argv
 */
function parseArgs(argv) {
  /** @type {{ url: string | null, file: string | null, apiService: string, authService: string, help: boolean }} */
  const out = {
    url: null,
    file: null,
    apiService: defaultApiService,
    authService: defaultAuthService,
    help: false
  }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--help' || a === '-h') out.help = true
    else if (a === '--url') out.url = argv[++i]
    else if (a === '--file') out.file = argv[++i]
    else if (a === '--api-service') out.apiService = resolve(argv[++i])
    else if (a === '--auth-service') out.authService = resolve(argv[++i])
  }
  if (!out.url && !out.file) out.url = defaultUrl
  return out
}

/**
 * 去掉 query，并把 ${...} / encodeURIComponent(...) 段换成 {param}。
 * @param {string} raw
 */
function normalizeClientPath(raw) {
  let p = String(raw || '').trim()
  // 查询串变量拼接（如 budget${q}、?${qs.toString()}）先去掉，避免变成路径段
  p = p.replace(/\$\{q\}/g, '')
  p = p.replace(/\$\{qs\.toString\(\)\}/g, '')
  const q = p.indexOf('?')
  if (q >= 0) p = p.slice(0, q)
  p = p.replace(/\$\{[^}]+\}/g, '{param}')
  p = p.replace(/\{param\}\{param\}/g, '{param}')
  if (!p.startsWith('/')) p = `/${p}`
  p = p.replace(/\/{2,}/g, '/')
  if (p.length > 1 && p.endsWith('/')) p = p.slice(0, -1)
  return p
}

/**
 * 从 TS 源码抽出相对 baseURL 的路径字面量。
 * @param {string} source
 * @param {string} basePrefix 完整前缀，如 /api/v1/ui
 */
function extractClientPaths(source, basePrefix) {
  /** @type {Set<string>} */
  const found = new Set()
  // request(...) / requestWithDefault(...) / apiClient.get|post|...(...) 的首参字符串
  const callRe =
    /(?:request(?:WithDefault)?(?:\s*<[^>]*>)?|apiClient\.(?:get|post|put|patch|delete)|authClient\.(?:get|post|put|patch|delete))\s*\(\s*([`'"])([\s\S]*?)\1/g
  let m
  while ((m = callRe.exec(source)) !== null) {
    const body = m[2]
    // 跳过纯变量拼接且不以 / 开头的（如 exportFile 的 path 参数）
    if (!body.trimStart().startsWith('/')) continue
    const rel = normalizeClientPath(body)
    found.add(`${basePrefix}${rel}`)
  }
  // exportFile('/export/...') 调用处（ApiService 定义或 Vue 调用）
  const exportRe = /exportFile\s*\(\s*([`'"])(\/[^`'"]+)\1/g
  while ((m = exportRe.exec(source)) !== null) {
    const rel = normalizeClientPath(m[2])
    found.add(`${basePrefix}${rel}`)
  }
  return found
}

/**
 * 扫描目录下 .ts/.vue 中的 exportFile('/...')。
 * @param {string} dir
 * @param {string} basePrefix
 * @param {Set<string>} into
 */
function scanExportFileCalls(dir, basePrefix, into) {
  /** @param {string} d */
  function walk(d) {
    for (const name of readdirSync(d)) {
      if (name === 'node_modules' || name === 'dist') continue
      const full = resolve(d, name)
      const st = statSync(full)
      if (st.isDirectory()) {
        walk(full)
        continue
      }
      if (!/\.(ts|vue)$/.test(name)) continue
      const src = readFileSync(full, 'utf8')
      const exportRe = /(?:ApiService\.)?exportFile\s*\(\s*([`'"])(\/[^`'"]+)\1/g
      let m
      while ((m = exportRe.exec(src)) !== null) {
        into.add(`${basePrefix}${normalizeClientPath(m[2])}`)
      }
    }
  }
  if (existsSync(dir)) walk(dir)
}

/**
 * OpenAPI path 转可匹配形态：{foo} → {param}
 * @param {string} openApiPath
 */
function normalizeOpenApiPath(openApiPath) {
  let p = String(openApiPath || '')
  p = p.replace(/\{[^}]+\}/g, '{param}')
  if (p.length > 1 && p.endsWith('/')) p = p.slice(0, -1)
  return p
}

/**
 * 客户端路径是否被某一 OpenAPI 路径覆盖（段级；{param} 可匹配任意段）。
 * @param {string} clientFull
 * @param {string[]} openApiNormalized
 */
function coveredByOpenApi(clientFull, openApiNormalized) {
  const c = normalizeOpenApiPath(clientFull)
  if (openApiNormalized.includes(c)) return true
  const cSegs = c.split('/')
  for (const o of openApiNormalized) {
    const oSegs = o.split('/')
    if (oSegs.length !== cSegs.length) continue
    let ok = true
    for (let i = 0; i < cSegs.length; i++) {
      const a = cSegs[i]
      const b = oSegs[i]
      if (a === b) continue
      if (a === '{param}' || b === '{param}') continue
      ok = false
      break
    }
    if (ok) return true
  }
  return false
}

/**
 * @param {string} url
 */
async function fetchJson(url) {
  const res = await fetch(url, { headers: { Accept: 'application/json' } })
  if (!res.ok) {
    throw new Error(`拉取 OpenAPI 失败: HTTP ${res.status} ${url}`)
  }
  return res.json()
}

async function main() {
  const args = parseArgs(process.argv.slice(2))
  if (args.help) {
    console.log(`用法:
  node scripts/check-ui-api-paths.mjs [--url URL] [--file api-docs.json]
  默认 URL: ${defaultUrl}
`)
    process.exit(0)
  }

  if (!existsSync(args.apiService)) {
    console.error(`找不到 ApiService: ${args.apiService}`)
    process.exit(2)
  }

  /** @type {any} */
  let doc
  if (args.file) {
    const fp = resolve(args.file)
    if (!existsSync(fp)) {
      console.error(`找不到 OpenAPI 文件: ${fp}`)
      process.exit(2)
    }
    doc = JSON.parse(readFileSync(fp, 'utf8'))
    console.log(`OpenAPI 来源: file ${fp}`)
  } else {
    try {
      doc = await fetchJson(args.url)
      console.log(`OpenAPI 来源: ${args.url}`)
    } catch (e) {
      console.error(String(e instanceof Error ? e.message : e))
      console.error('提示: 先启动 insight-server，或改用 --file 指向已导出的 /v3/api-docs JSON。')
      process.exit(2)
    }
  }

  const pathsObj = doc?.paths
  if (!pathsObj || typeof pathsObj !== 'object') {
    console.error('OpenAPI JSON 缺少 paths')
    process.exit(2)
  }

  const openApiNormalized = Object.keys(pathsObj).map(normalizeOpenApiPath)
  const openApiUi = openApiNormalized.filter((p) => p.startsWith('/api/v1/ui'))
  console.log(`OpenAPI 路径数: ${openApiNormalized.length}（其中 /api/v1/ui* ${openApiUi.length}）`)

  const apiSrc = readFileSync(args.apiService, 'utf8')
  const clientUi = extractClientPaths(apiSrc, '/api/v1/ui')
  const uiSrcRoot = resolve(repoRoot, 'insight-server/ui/src')
  scanExportFileCalls(uiSrcRoot, '/api/v1/ui', clientUi)

  /** @type {Set<string>} */
  let clientAuth = new Set()
  if (existsSync(args.authService)) {
    const authSrc = readFileSync(args.authService, 'utf8')
    clientAuth = extractClientPaths(authSrc, '/api/v1/auth')
  }

  const clientAll = [...clientUi, ...clientAuth].sort()
  console.log(`客户端抽出路径数: ${clientAll.length}（ui ${clientUi.size} + auth ${clientAuth.size}）`)

  const missing = []
  for (const p of clientAll) {
    if (!coveredByOpenApi(p, openApiNormalized)) {
      missing.push(p)
    }
  }

  if (missing.length === 0) {
    console.log('OK: 客户端路径均可在 OpenAPI 中匹配到。')
    process.exit(0)
  }

  console.error(`FAIL: ${missing.length} 条客户端路径未在 OpenAPI 中找到：`)
  for (const p of missing) {
    console.error(`  - ${p}`)
  }
  process.exit(1)
}

main().catch((e) => {
  console.error(e)
  process.exit(2)
})
