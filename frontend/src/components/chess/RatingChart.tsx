import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { Stats } from '../../api/types'
import type { Dict } from '../../i18n/en'
import type { Formatters } from '../../i18n/format'
import { useI18n } from '../../i18n/I18nProvider'

type Point = Stats['ratingHistory'][number] & { idx: number }
type TipPayload = readonly { payload?: unknown }[] | undefined

/** Single-series rating line with a crosshair tooltip. One axis, recessive grid. */
export function RatingChart({ history, memberSince }: { history: Stats['ratingHistory']; memberSince: string }) {
  const { t, f } = useI18n()
  const data: Point[] = [
    { date: memberSince, rating: 1500, rd: 350, gameId: null, opponent: null, result: null, idx: 0 },
    ...history.map((h, i) => ({ ...h, idx: i + 1 })),
  ]
  const ratings = data.map((d) => d.rating)
  const hi = Math.max(...ratings)
  const lo = Math.min(...ratings)
  const min = Math.floor((lo - 25) / 50) * 50
  const max = Math.ceil((hi + 25) / 50) * 50

  return (
    <figure className="m-0">
      <figcaption className="sr-only">{t.chart.ratingCaption(history.length, data[0].rating, data[data.length - 1].rating, hi, lo)}</figcaption>
      <div className="h-64 w-full sm:h-72">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -8 }}>
            <defs>
              <linearGradient id="ratingFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="var(--c-accent)" stopOpacity={0.2} />
                <stop offset="100%" stopColor="var(--c-accent)" stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid vertical={false} stroke="var(--c-line)" />
            <XAxis
              dataKey="idx"
              type="number"
              domain={[0, data.length - 1]}
              tickLine={false}
              axisLine={false}
              tick={{ fill: 'var(--c-faint)', fontSize: 12 }}
              tickFormatter={(i: number) => (data[i] ? f.shortDate(data[i].date) : '')}
              minTickGap={40}
            />
            <YAxis domain={[min, max]} tickLine={false} axisLine={false} width={48} tick={{ fill: 'var(--c-faint)', fontSize: 12 }} allowDecimals={false} />
            <Tooltip cursor={{ stroke: 'var(--c-faint)', strokeDasharray: '3 3' }} content={(p) => <ChartTooltip active={p.active} payload={p.payload as TipPayload} t={t} f={f} />} />
            <Area
              type="monotone"
              dataKey="rating"
              stroke="var(--c-accent)"
              strokeWidth={2}
              fill="url(#ratingFill)"
              dot={false}
              activeDot={{ r: 5, stroke: 'var(--c-surface)', strokeWidth: 2, fill: 'var(--c-accent)' }}
              isAnimationActive={false}
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </figure>
  )
}

function ChartTooltip({ active, payload, t, f }: { active?: boolean; payload?: TipPayload; t: Dict; f: Formatters }) {
  if (!active || !payload?.length) return null
  const p = payload[0].payload as Point
  const line = !p.opponent
    ? t.chart.startingRating
    : p.result === 'W'
      ? t.chart.beat(p.opponent)
      : p.result === 'L'
        ? t.chart.lostTo(p.opponent)
        : t.chart.drewWith(p.opponent)
  return (
    <div className="rounded-control border border-line bg-surface px-3 py-2 text-sm shadow-lg">
      <p className="font-display text-base font-semibold tabular-nums">{p.rating}</p>
      <p className="text-muted">{line}</p>
      <p className="text-faint">{f.date(p.date)}</p>
    </div>
  )
}
