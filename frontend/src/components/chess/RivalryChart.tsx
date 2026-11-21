import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { Stats } from '../../api/types'
import { useI18n } from '../../i18n/I18nProvider'

type History = Stats['ratingHistory']
type Row = { t: number; a?: number; b?: number }

const A = 'var(--c-accent)'
const B = 'var(--c-ink)'

/**
 * Two players' ratings over time on one axis: one line each (accent and ink, told apart by a legend and the
 * tooltip too), each starting where its player joined. Steps, because a rating only changes with a game.
 */
export function RivalryChart({ a, b }: { a: { name: string; since: string; history: History }; b: { name: string; since: string; history: History } }) {
  const { t, f } = useI18n()
  const points = (since: string, h: History) => [{ t: Date.parse(since), r: 1500 }, ...h.map((p) => ({ t: Date.parse(p.date), r: p.rating }))]
  const pa = points(a.since, a.history)
  const pb = points(b.since, b.history)
  const times = [...new Set([...pa, ...pb].map((p) => p.t))].sort((x, y) => x - y)
  const last = (ps: { t: number; r: number }[], at: number) => {
    let r: number | undefined
    for (const p of ps) if (p.t <= at) r = p.r
    return r
  }
  const data: Row[] = times.map((tm) => ({ t: tm, a: last(pa, tm), b: last(pb, tm) }))
  const all = data.flatMap((d) => [d.a, d.b]).filter((x): x is number => x !== undefined)
  const min = Math.floor((Math.min(...all) - 25) / 50) * 50
  const max = Math.ceil((Math.max(...all) + 25) / 50) * 50
  const now = data[data.length - 1]

  return (
    <figure className="m-0">
      <figcaption className="sr-only">{t.rivalry.chartCaption(a.name, now?.a ?? 1500, b.name, now?.b ?? 1500)}</figcaption>
      <div className="h-64 w-full sm:h-72">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -8 }}>
            <CartesianGrid vertical={false} stroke="var(--c-line)" />
            <XAxis
              dataKey="t"
              type="number"
              scale="time"
              domain={['dataMin', 'dataMax']}
              tickLine={false}
              axisLine={false}
              tick={{ fill: 'var(--c-faint)', fontSize: 12 }}
              tickFormatter={(v: number) => f.shortDate(new Date(v).toISOString())}
              minTickGap={40}
            />
            <YAxis domain={[min, max]} tickLine={false} axisLine={false} width={48} tick={{ fill: 'var(--c-faint)', fontSize: 12 }} allowDecimals={false} />
            <Tooltip
              cursor={{ stroke: 'var(--c-faint)', strokeDasharray: '3 3' }}
              content={(p) => {
                if (!p.active || !p.payload?.length) return null
                const row = p.payload[0].payload as Row
                return (
                  <div className="rounded-control border border-line bg-surface px-3 py-2 text-sm shadow-lg">
                    <p className="text-faint">{f.date(new Date(row.t).toISOString())}</p>
                    {row.a !== undefined && (
                      <p className="flex items-center gap-2">
                        <span aria-hidden className="h-0.5 w-3 rounded-full" style={{ background: A }} />
                        {a.name} <span className="ml-auto pl-3 font-semibold tabular-nums">{row.a}</span>
                      </p>
                    )}
                    {row.b !== undefined && (
                      <p className="flex items-center gap-2">
                        <span aria-hidden className="h-0.5 w-3 rounded-full" style={{ background: B }} />
                        {b.name} <span className="ml-auto pl-3 font-semibold tabular-nums">{row.b}</span>
                      </p>
                    )}
                  </div>
                )
              }}
            />
            <Legend verticalAlign="top" align="right" height={28} iconType="plainline" wrapperStyle={{ fontSize: 13, color: 'var(--c-muted)' }} />
            <Line name={a.name} type="stepAfter" dataKey="a" stroke={A} strokeWidth={2} dot={false} connectNulls isAnimationActive={false} />
            <Line name={b.name} type="stepAfter" dataKey="b" stroke={B} strokeWidth={2} dot={false} connectNulls isAnimationActive={false} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </figure>
  )
}
