<script setup lang="ts" generic="T extends object">
import { computed } from 'vue'
import type { SiColumn } from './siTable'

type KeyedRow = T & { __siKey: string; __siIndex: number }

const props = withDefaults(
  defineProps<{
    rows: readonly T[]
    columns: readonly SiColumn<T>[]
    rowKey?: string | ((row: T, index: number) => string | number)
    clickable?: boolean
    emptyText?: string
    rowClass?: (row: T, index: number) => string | undefined
    rowStyle?: (row: T, index: number) => Record<string, string> | undefined
  }>(),
  {
    clickable: false,
    emptyText: '没有记录'
  }
)

const emit = defineEmits<{
  rowClick: [row: T]
}>()

function keyOf(row: T, index: number): string | number {
  const rowKey = props.rowKey
  if (typeof rowKey === 'function') return rowKey(row, index)
  if (typeof rowKey === 'string') {
    const value = (row as Record<string, unknown>)[rowKey]
    if (value == null || value === '') return index
    return String(value)
  }
  return index
}

/** 带稳定 key / 下标，供 el-table row-key（其回调无 index） */
const keyedRows = computed(() =>
  props.rows.map((row, index) => {
    const keyed = { ...(row as object), __siKey: String(keyOf(row, index)), __siIndex: index } as KeyedRow
    return keyed
  })
)

function originalOf(keyed: KeyedRow): T {
  return (props.rows[keyed.__siIndex] ?? keyed) as T
}

function textOf(row: T, column: SiColumn<T>): string {
  if (column.value) {
    const value = column.value(row)
    if (value == null || value === '') return '—'
    return String(value)
  }
  const raw = (row as Record<string, unknown>)[column.key]
  if (raw == null || raw === '') return '—'
  return String(raw)
}

function cellClass(row: T, column: SiColumn<T>): string[] {
  const extra = typeof column.cellClass === 'function' ? column.cellClass(row) : column.cellClass
  return [
    column.numeric ? 'si-data-table__num' : '',
    column.truncate ? 'si-data-table__truncate' : '',
    typeof extra === 'string' ? extra : extra || ''
  ]
}

/** 静态 cellClass 挂到 td；函数型挂到单元格内层 */
function columnClassName(column: SiColumn<T>): string {
  const parts = [
    column.numeric ? 'si-data-table__num' : '',
    column.truncate ? 'si-data-table__truncate' : '',
    typeof column.cellClass === 'string' ? column.cellClass : ''
  ]
  return parts.filter(Boolean).join(' ')
}

function cellTitle(row: T, column: SiColumn<T>): string | undefined {
  const title = column.title?.(row)
  return title ? title : undefined
}

function onRowClick(keyed: KeyedRow) {
  if (!props.clickable) return
  emit('rowClick', originalOf(keyed))
}

function onCellClick(event: MouseEvent, column: SiColumn<T>) {
  if (column.stopRowClick) event.stopPropagation()
}

function tableRowClassName({ row }: { row: KeyedRow }): string {
  const parts = [
    props.clickable ? 'si-data-table__row--clickable' : '',
    props.rowClass?.(originalOf(row), row.__siIndex) || ''
  ]
  return parts.filter(Boolean).join(' ')
}

function tableRowStyle({ row }: { row: KeyedRow }): Record<string, string> | undefined {
  return props.rowStyle?.(originalOf(row), row.__siIndex)
}

function columnWidth(column: SiColumn<T>): string | undefined {
  return column.width
}
</script>

<template>
  <div class="si-data-table-wrap">
    <el-table
      class="si-data-table"
      :data="keyedRows"
      row-key="__siKey"
      size="small"
      :show-header="true"
      :empty-text="emptyText"
      :row-class-name="tableRowClassName"
      :row-style="tableRowStyle"
      :highlight-current-row="false"
      style="width: 100%"
      @row-click="onRowClick"
    >
      <el-table-column
        v-for="column in columns"
        :key="column.key"
        :prop="column.key"
        :label="column.label"
        :width="columnWidth(column)"
        :align="column.numeric ? 'right' : 'left'"
        :header-align="column.numeric ? 'right' : 'left'"
        :class-name="columnClassName(column)"
        :label-class-name="[column.numeric ? 'si-data-table__num' : '', column.headerClass || ''].filter(Boolean).join(' ')"
        :show-overflow-tooltip="false"
      >
        <template #default="{ row }">
          <div
            class="si-data-table__cell"
            :class="typeof column.cellClass === 'function' ? cellClass(originalOf(row), column) : undefined"
            :title="cellTitle(originalOf(row), column)"
            @click="onCellClick($event, column)"
          >
            <slot :name="`cell-${column.key}`" :row="originalOf(row)" :index="row.__siIndex">
              {{ textOf(originalOf(row), column) }}
            </slot>
          </div>
        </template>
      </el-table-column>

      <template #empty>
        <div class="si-data-table__empty">
          <slot name="empty">{{ emptyText }}</slot>
        </div>
      </template>
    </el-table>
  </div>
</template>

<style scoped>
.si-data-table-wrap {
  width: 100%;
  overflow-x: auto;
}

.si-data-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: var(--si-surface-2, #f7f9f7);
  --el-table-row-hover-bg-color: var(--si-table-hover);
  --el-table-border-color: var(--si-table-cell-border);
  --el-table-text-color: var(--si-ink);
  --el-table-header-text-color: var(--si-ink-soft);
}

.si-data-table :deep(.el-table__header th),
.si-data-table :deep(.el-table__body td) {
  vertical-align: middle;
  box-sizing: border-box;
}

.si-data-table :deep(th.si-data-table__num),
.si-data-table :deep(td.si-data-table__num),
.si-data-table :deep(.si-data-table__num) {
  text-align: right;
  font-family: var(--font-numeric);
  font-variant-numeric: tabular-nums;
}

.si-data-table :deep(td.si-data-table__truncate),
.si-data-table :deep(.si-data-table__truncate) {
  max-width: 16rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.85rem;
}

.si-data-table :deep(.si-data-table__row--clickable) {
  cursor: pointer;
}

/* Bootstrap 行态类在 el-table 上的兜底 */
.si-data-table :deep(.el-table__row.table-danger > td.el-table__cell) {
  background-color: var(--si-table-danger-bg) !important;
  color: var(--si-table-danger-color);
}

.si-data-table :deep(.el-table__row.table-active > td.el-table__cell) {
  background-color: var(--si-active-brand) !important;
}

.si-data-table :deep(.el-table__row.table-warning > td.el-table__cell) {
  background-color: var(--si-table-warning-bg) !important;
  color: var(--si-table-warning-color);
}

.si-data-table__cell {
  min-width: 0;
}

.si-data-table__empty {
  text-align: center;
  color: var(--si-muted);
  padding: 1.5rem 1rem;
}
</style>
