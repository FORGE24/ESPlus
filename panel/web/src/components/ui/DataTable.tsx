import { ReactNode } from 'react'

export interface Column<T> {
  key: string
  header: string
  render?: (row: T) => ReactNode
  className?: string
  width?: string
}

interface DataTableProps<T> {
  columns: Column<T>[]
  data: T[]
  emptyMessage?: string
  rowKey?: (row: T, index: number) => string | number
}

export function DataTable<T extends Record<string, unknown>>({
  columns,
  data,
  emptyMessage = '暂无数据',
  rowKey,
}: DataTableProps<T>) {
  if (!data || data.length === 0) {
    return (
      <div style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--ink-500)' }}>
        <p style={{ fontSize: 14 }}>{emptyMessage}</p>
      </div>
    )
  }

  return (
    <div style={{ overflowX: 'auto' }}>
      <table className="data-table">
        <thead>
          <tr>
            {columns.map((col) => (
              <th key={col.key} style={col.width ? { width: col.width } : undefined}>{col.header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.map((row, i) => (
            <tr key={rowKey ? rowKey(row, i) : i}>
              {columns.map((col) => (
                <td key={col.key} className={col.className}>
                  {col.render ? col.render(row) : String(row[col.key] ?? '')}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function Badge({ type, children }: { type: 'ok' | 'warn' | 'danger' | 'info' | 'violet'; children: ReactNode }) {
  return <span className={`badge badge-${type}`}>{children}</span>
}
