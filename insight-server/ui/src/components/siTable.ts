export interface SiColumn<T extends object> {
  key: string
  label: string
  /** 右对齐，数字用等宽字形 */
  numeric?: boolean
  /** 单行省略；配合 title 在悬停时看全文 */
  truncate?: boolean
  width?: string
  /** 表头单元格附加类，用于列宽 */
  headerClass?: string
  /** 点击该列不触发行点击，用于勾选和行内按钮 */
  stopRowClick?: boolean
  cellClass?: string | ((row: T) => string | undefined)
  value?: (row: T) => string | number | null | undefined
  title?: (row: T) => string | undefined
}
