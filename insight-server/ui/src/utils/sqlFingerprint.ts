/**
 * SQL 指纹启发式归一化（与 Server 侧 SqlFingerprintNormalizer 对齐）。
 * 字面量改为 ?，压缩空白，便于按语句形态聚合与跨页跳转。
 */

const MAX_LEN = 512

const STRING_LITERAL = /'(?:[^']|'')*'|"(?:[^"]|"")*"/gs
const NUMBER_LITERAL = /(?<![\w.$])(?:0x[0-9a-fA-F]+|\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)(?![\w.$])/g
const IN_LIST = /\(\s*\?(?:\s*,\s*\?)+\s*\)/gi
const WHITESPACE = /\s+/g
const TRAILING_ELLIPSIS = /…+\s*$/g

/**
 * 归一化 SQL 文本为指纹；空白时返回空串。
 */
export function normalizeSqlFingerprint(sql: string | null | undefined): string {
  if (sql == null) return ''
  let s = String(sql).trim()
  if (!s) return ''
  s = s.replace(TRAILING_ELLIPSIS, '')
  s = s.replace(STRING_LITERAL, '?')
  s = s.replace(NUMBER_LITERAL, '?')
  s = s.replace(IN_LIST, '(?)')
  s = s.replace(WHITESPACE, ' ').trim()
  if (s.length > MAX_LEN) {
    s = `${s.slice(0, MAX_LEN - 1)}…`
  }
  return s
}
