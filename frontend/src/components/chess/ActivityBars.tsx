import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis } from 'recharts'
import type { Dict } from '../../i18n/en'
import type { Formatters } from '../../i18n/format'
import { useI18n } from '../../i18n/I18nProvider'

type TipPayload = readonly { payload?: unknown }[] | undefined

/** Games per month, last 12 months. Single series, no legend. */
export function ActivityBars({ activity }: { activity: { month: string; games: number }[] }) {
  const { t, f } = useI18n()
  const total = activity.reduce((s, m) => s + m.games, 0)
  return (
    <figure className="m-0">
      <figcaption className="sr-only">{t.chart.activityCaption(total)}</figcaption>
      <div className="h-36 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={activity} margin={{ top: 4, right: 0, bottom: 0, left: 0 }} barCategoryGap={3}>
            <XAxis dataKey="month" tickLine={false} axisLine={false} interval={0} tick={{ fill: 'var(--c-faint)', fontSize: 11 }} tickFormatter={(m: string) => f.monthInitial(m)} />
            <Tooltip cursor={{ fill: 'var(--c-raised)' }} content={(p) => <BarTip active={p.active} payload={p.payload as TipPayload} t={t} f={f} />} />
            <Bar dataKey="games" fill="var(--c-draw)" radius={[4, 4, 0, 0]} minPointSize={2} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </figure>
  )
}

function BarTip({ active, payload, t, f }: { active?: boolean; payload?: TipPayload; t: Dict; f: Formatters }) {
  if (!active || !payload?.length) return null
  const p = payload[0].payload as { month: string; games: number }
  return <div className="rounded-control border border-line bg-surface px-3 py-1.5 text-sm">{t.chart.monthGames(f.month(p.month), p.games)}</div>
}
