import { Link } from 'react-router'
import type { Match, Standing } from '../../api/types'
import { points } from '../../i18n/format'
import { useI18n } from '../../i18n/I18nProvider'
import { cn } from '../../lib/cn'
import { Avatar } from './Avatar'

/** Classic tournament crosstable: each row's score against each column's player. */
export function Crosstable({ standings, matches }: { standings: Standing[]; matches: Match[] }) {
  const { t, f } = useI18n()
  const scores = new Map<string, number[]>()
  for (const m of matches) {
    if (m.status !== 'COMPLETED' || !m.result || !m.white || !m.black) continue
    const ws = m.result === 'WHITE_WIN' ? 1 : m.result === 'DRAW' ? 0.5 : 0
    push(scores, m.white.id, m.black.id, ws)
    push(scores, m.black.id, m.white.id, 1 - ws)
  }
  const leaderPoints = standings[0]?.points ?? 0
  const anyPlayed = standings.some((s) => s.played > 0)

  return (
    <div className="overflow-x-auto">
      <table className="w-full border-separate border-spacing-0 text-[0.9375rem]">
        <caption className="sr-only">{t.crosstable.caption}</caption>
        <thead>
          <tr className="text-left text-[0.8125rem] text-muted">
            <th scope="col" className="w-8 px-3 py-2 font-medium">#</th>
            <th scope="col" className="px-3 py-2 font-medium">{t.crosstable.player}</th>
            {standings.map((s, i) => (
              <th key={s.player.id} scope="col" className="hidden w-10 px-1 py-2 text-center font-medium md:table-cell" title={s.player.realName}>
                {i + 1}
              </th>
            ))}
            <th scope="col" className="px-3 py-2 text-right font-medium">{t.crosstable.games}</th>
            <th scope="col" className="px-3 py-2 text-right font-medium">{t.crosstable.points}</th>
            <th scope="col" className="hidden px-3 py-2 text-right font-medium sm:table-cell" title={t.crosstable.sbTitle}>
              SB
            </th>
          </tr>
        </thead>
        <tbody>
          {standings.map((row, ri) => {
            const leads = anyPlayed && row.points === leaderPoints
            return (
              <tr key={row.player.id}>
                <td className="border-t border-line px-3 py-2.5 text-muted tabular-nums">{row.rank}</td>
                <td className="border-t border-line px-3 py-2.5">
                  <Link to={`/players/${row.player.username}`} className="flex items-center gap-2.5 hover:text-accent">
                    <Avatar name={row.player.realName} seed={row.player.username} size="sm" />
                    <span className="truncate font-medium">{row.player.realName}</span>
                  </Link>
                </td>
                {standings.map((col, ci) => {
                  const cell = scores.get(`${row.player.id}:${col.player.id}`)
                  const avg = cell ? sum(cell) / cell.length : 0
                  return (
                    <td key={col.player.id} className={cn('hidden border-t border-l border-line px-1 py-2.5 text-center tabular-nums md:table-cell', ri === ci && 'bg-raised')}>
                      {ri === ci ? null : cell ? (
                        <span className={cn(avg >= 0.75 && 'font-semibold text-win', avg <= 0.25 && 'text-loss')}>{points(sum(cell))}</span>
                      ) : (
                        <span className="text-faint">·</span>
                      )}
                    </td>
                  )
                })}
                <td className="border-t border-line px-3 py-2.5 text-right text-muted tabular-nums">{row.played}</td>
                <td className={cn('border-t border-line px-3 py-2.5 text-right font-display text-lg font-semibold tabular-nums', leads && 'text-accent')}>
                  {points(row.points)}
                </td>
                <td className="hidden border-t border-line px-3 py-2.5 text-right text-muted tabular-nums sm:table-cell">
                  {f.decimal(row.sonnebornBerger)}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function push(map: Map<string, number[]>, a: number, b: number, s: number) {
  const k = `${a}:${b}`
  map.set(k, [...(map.get(k) ?? []), s])
}

const sum = (xs: number[]) => xs.reduce((a, b) => a + b, 0)
