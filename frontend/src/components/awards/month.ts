/** "2026-01" → "2025-12". Null before 2000, where there are no standings. */
export function previousMonth(yyyyMm: string): string | null {
  const [y, m] = yyyyMm.split('-').map(Number)
  const prev = m === 1 ? `${y - 1}-12` : `${y}-${String(m - 1).padStart(2, '0')}`
  return prev < '2000-01' ? null : prev
}

/** "2025-12" → "2026-01" */
export function nextMonth(yyyyMm: string): string {
  const [y, m] = yyyyMm.split('-').map(Number)
  return m === 12 ? `${y + 1}-01` : `${y}-${String(m + 1).padStart(2, '0')}`
}
