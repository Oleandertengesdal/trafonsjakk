// Locale-aware formatting. Norwegian uses nb-NO conventions (12. jan. 2026, 60 %), English uses en-GB.

export type Formatters = ReturnType<typeof makeFormatters>

export function makeFormatters(lang: 'nb' | 'en') {
  const locale = lang === 'nb' ? 'nb-NO' : 'en-GB'
  const date = new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', year: 'numeric' })
  const shortDate = new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short' })
  const time = new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit' })
  const month = new Intl.DateTimeFormat(locale, { month: 'long', timeZone: 'UTC' })
  const monthYear = new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric', timeZone: 'UTC' })
  const oneDecimal = new Intl.NumberFormat(locale, { minimumFractionDigits: 1, maximumFractionDigits: 1 })
  const narrowMonth = new Intl.DateTimeFormat(locale, { month: 'narrow', timeZone: 'UTC' })
  const weekdayName = new Intl.DateTimeFormat(locale, { weekday: 'long', timeZone: 'UTC' })
  const rtf = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' })
  const percent = new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 0 })
  const decimal = new Intl.NumberFormat(locale, { maximumFractionDigits: 2 })

  return {
    locale,
    date: (iso: string) => date.format(new Date(iso)),
    shortDate: (iso: string) => shortDate.format(new Date(iso)),
    dateTime: (iso: string) => `${date.format(new Date(iso))}, ${time.format(new Date(iso))}`,
    /** "2026-03" → "mars" / "March" */
    month: (yyyyMm: string) => month.format(new Date(`${yyyyMm}-01T00:00:00Z`)),
    /** "2026-03" → "mars 2026" / "March 2026" */
    monthYear: (yyyyMm: string) => monthYear.format(new Date(`${yyyyMm}-01T00:00:00Z`)),
    /** One decimal with a real minus sign: +1,9 / −0,4 / ±0,0 */
    signedDecimal: (v: number) => {
      const r = Math.round(v * 10) / 10
      const s = oneDecimal.format(Math.abs(r))
      return r > 0 ? `+${s}` : r < 0 ? `−${s}` : `±${s}`
    },
    monthInitial: (yyyyMm: string) => narrowMonth.format(new Date(`${yyyyMm}-01T00:00:00Z`)).toUpperCase(),
    relative: (iso: string, now = Date.now()) => {
      const diff = new Date(iso).getTime() - now
      const abs = Math.abs(diff)
      if (abs < 3_600_000) return rtf.format(Math.round(diff / 60_000), 'minute')
      if (abs < 86_400_000) return rtf.format(Math.round(diff / 3_600_000), 'hour')
      if (abs < 30 * 86_400_000) return rtf.format(Math.round(diff / 86_400_000), 'day')
      return date.format(new Date(iso))
    },
    /** 1 = Monday … 7 = Sunday → "tirsdag" / "Tuesday" */
    weekday: (day: number) => weekdayName.format(new Date(Date.UTC(2024, 0, day))), // 1 Jan 2024 was a Monday
    /** "tirsdager" / "Tuesdays" */
    weekdays: (day: number) => `${weekdayName.format(new Date(Date.UTC(2024, 0, day)))}${lang === 'nb' ? 'er' : 's'}`,
    pct: (v: number) => percent.format(v),
    decimal: (v: number) => decimal.format(v),
    /** Always one decimal: 1,5 / 0,9 / 1,0 */
    decimal1: (v: number) => oneDecimal.format(v),
  }
}

/** Rating change with a real minus sign: +12, −8, ±0 */
export const signed = (n: number) => (n > 0 ? `+${n}` : n < 0 ? `−${Math.abs(n)}` : '±0')

/** Tournament points with halves: 2½, ½, 3 */
export const points = (n: number) => {
  const whole = Math.floor(n)
  const half = n - whole >= 0.5
  if (whole === 0 && half) return '½'
  return half ? `${whole}½` : String(whole)
}

/** Chess result notation is the same in every language. */
export const notation = { WHITE_WIN: '1–0', BLACK_WIN: '0–1', DRAW: '½–½' } as const

/** Local value for <input type="datetime-local"> */
export function toLocalInput(d: Date): string {
  const off = d.getTimezoneOffset()
  return new Date(d.getTime() - off * 60_000).toISOString().slice(0, 16)
}
