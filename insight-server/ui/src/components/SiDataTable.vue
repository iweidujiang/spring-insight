<script setup lang="ts" generic="T extends object">
import type { SiColumn } from './siTable'

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
    extra || ''
  ]
}

function cellTitle(row: T, column: SiColumn<T>): string | undefined {
  const title = column.title?.(row)
  return title ? title : undefined
}

function onRowClick(row: T) {
  if (!props.clickable) return
  emit('rowClick', row)
}

function onCellClick(event: MouseEvent, column: SiColumn<T>) {
  if (column.stopRowClick) event.stopPropagation()
}
</script>

<template>
  <div class="table-responsive">
    <table class="table table-hover mb-0 si-data-table">
      <thead class="table-light">
        <tr>
          <th
            v-for="column in columns"
            :key="column.key"
            scope="col"
            :class="[column.numeric ? 'si-data-table__num' : '', column.headerClass || '']"
            :style="column.width ? { width: column.width } : undefined"
          >
            {{ column.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="rows.length === 0">
          <td :colspan="Math.max(columns.length, 1)" class="si-data-table__empty">
            <slot name="empty">{{ emptyText }}</slot>
          </td>
        </tr>
        <tr
          v-for="(row, index) in rows"
          :key="keyOf(row, index)"
          :class="[clickable ? 'si-data-table__row--clickable' : '', props.rowClass?.(row, index)]"
          :style="props.rowStyle?.(row, index)"
          @click="onRowClick(row)"
        >
          <td
            v-for="column in columns"
            :key="column.key"
            :class="cellClass(row, column)"
            :title="cellTitle(row, column)"
            @click="onCellClick($event, column)"
          >
            <slot :name="`cell-${column.key}`" :row="row" :index="index">
              {{ textOf(row, column) }}
            </slot>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.si-data-table th.si-data-table__num,
.si-data-table td.si-data-table__num {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.si-data-table td.si-data-table__truncate {
  max-width: 16rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.85rem;
}

.si-data-table__row--clickable {
  cursor: pointer;
}

.si-data-table__empty {
  text-align: center;
  color: var(--si-muted);
  padding: 1.5rem 1rem;
}
</style>
