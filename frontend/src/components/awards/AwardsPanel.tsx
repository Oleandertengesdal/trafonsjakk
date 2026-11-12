import { useId, useState } from 'react'
import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Flame, Trophy } from 'lucide-react'
import { awardsApi } from '../../api/endpoints'
import type { Award, AwardRace } from '../../api/types'
import { points, signed } from '../../i18n/format'
import { useI18n } from '../../i18n/I18nProvider'
import { cn } from '../../lib/cn'
import { Avatar } from '../chess/Avatar'
import { ErrorNotice } from '../ui/ErrorNotice'
import { Panel } from '../ui/Panel'
import { SkeletonRows } from '../ui/Skeleton'
import { nextMonth, previousMonth } from './month'

/** Ratings page: the month's full standings (step back through earlier months) and every earlier winner. */
export function AwardsPanel() {
  const { t, f } = useI18n()
  const a = t.awards
  const [month, setMonth] = useState<string | null>(null) // null: this month
  const current = useQuery({ queryKey: ['awards', 'race', 'current'], queryFn: () => awardsApi.race() })
  const picked = useQuery({ queryKey: ['awards', 'race', month], queryFn: () => awardsApi.race(month!), enabled: !!month })
  const q = month ? picked : current
  const shown = month ?? current.data?.month
  const prev = shown ? previousMonth(shown) : null

  const step = (to: string) => setMonth(to === current.data?.month ? null : to)

  return (
    <Panel
      id="awards"
      title={a.title}
      className="scroll-mt-6"
      action={
        shown && (
          <div className="flex items-center gap-1">
            <button
              type="button"
              aria-label={t.awardsNav.previous}
              disabled={!prev}
              onClick={() => prev && step(prev)}
              className="grid size-8 place-items-center rounded-md text-muted hover:bg-raised hover:text-ink disabled:opacity-40"
            >
              <ChevronLeft aria-hidden className="size-4.5" />
            </button>
            <span className="min-w-[8.5rem] text-center text-sm font-medium tabular-nums">{f.monthYear(shown)}</span>
            <button
              type="button"
              aria-label={t.awardsNav.next}
              disabled={!month}
              onClick={() => step(nextMonth(shown))}
              className="grid size-8 place-items-center rounded-md text-muted hover:bg-raised hover:text-ink disabled:opacity-40"
            >
              <ChevronRight aria-hidden className="size-4.5" />
            </button>
          </div>
        )
      }
    >
      {q.isPending ? (
        <SkeletonRows rows={4} />
      ) : q.isError ? (
        <ErrorNotice error={q.error} onRetry={() => q.refetch()} />
      ) : (
        <div className="flex flex-col gap-6 pb-5">
          <p className="max-w-[65ch] px-5 pt-1 text-[0.9375rem] text-muted">{a.rule(q.data.minGames)}</p>
          <Standings race={q.data} live={!month} />
          <History />
        </div>
      )}
    </Panel>
  )
}

function Standings({ race, live }: { race: AwardRace; live: boolean }) {
  const { t, f } = useI18n()
  const a = t.awards
  const rows = race.playerOfMonth.standings
  const winnerId = race.playerOfMonth.winner?.player.id
  const active = race.mostActive.winner
  let place = 0

  if (rows.length === 0) return <p className="px-5 text-[0.9375rem] text-muted">{live ? a.noGamesYet : a.noGamesThen}</p>
  return (
    <div>
      {race.status === 'DECIDING' && <p className="px-5 pb-2 text-sm text-muted">{a.decides(f.date(race.decidesAt))}</p>}
      <table className="w-full text-[0.9375rem]">
        <caption className="sr-only">{a.standingsCaption(f.monthYear(race.month))}</caption>
        <thead>
          <tr className="text-left text-[0.8125rem] text-muted">
            <th scope="col" className="w-12 px-5 py-2 font-medium">#</th>
            <th scope="col" className="py-2 font-medium">{a.player}</th>
            <th scope="col" className="px-3 py-2 text-right font-medium">{a.games}</th>
            <th scope="col" className="hidden px-3 py-2 text-right font-medium sm:table-cell">{a.points}</th>
            <th scope="col" className="hidden px-3 py-2 text-right font-medium sm:table-cell">{a.expected}</th>
            <th scope="col" className="px-3 py-2 text-right font-medium">
              <span className="hidden sm:inline">{a.overColumn}</span>
              <span className="sm:hidden" aria-label={a.overColumn}>
                {a.overColumnShort}
              </span>
            </th>
            <th scope="col" className="hidden px-5 py-2 text-right font-medium md:table-cell">{a.rating}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((s) => {
            if (s.qualified) place++
            return (
              <tr key={s.player.id} className={cn('border-t border-line', !s.qualified && 'text-muted')}>
                <td className={cn('px-5 py-2.5 font-display font-semibold tabular-nums', place === 1 && s.qualified ? 'text-gold' : 'text-faint')}>
                  {s.qualified ? place : ''}
                </td>
                <td className="py-2.5">
                  <Link to={`/players/${s.player.username}`} className="flex items-center gap-3 hover:text-accent">
                    <Avatar name={s.player.realName} seed={s.player.username} size="sm" />
                    <span className="min-w-0">
                      <span className="flex items-center gap-1.5 font-medium">
                        <span className="truncate">{s.player.realName}</span>
                        {s.player.id === winnerId && <Trophy role="img" aria-label={a.kind.PLAYER_OF_MONTH} className="size-4 shrink-0 text-gold" />}
                        {s.player.id === active?.player.id && <Flame role="img" aria-label={a.kind.MOST_ACTIVE} className="size-4 shrink-0 text-gold" />}
                      </span>
                      {!s.qualified && <span className="block text-[0.8125rem] text-faint">{a.gamesOf(s.games, race.minGames)}</span>}
                    </span>
                  </Link>
                </td>
                <td className="px-3 py-2.5 text-right tabular-nums">{s.games}</td>
                <td className="hidden px-3 py-2.5 text-right tabular-nums sm:table-cell">{points(s.points)}</td>
                <td className="hidden px-3 py-2.5 text-right text-muted tabular-nums sm:table-cell">{f.decimal1(s.expected)}</td>
                <td className={cn('px-3 py-2.5 text-right font-medium tabular-nums', s.qualified && s.overExpected > 0.05 && 'text-win')}>
                  {f.signedDecimal(s.overExpected)}
                </td>
                <td className="hidden px-5 py-2.5 text-right text-muted tabular-nums md:table-cell">{signed(s.ratingChange)}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function History() {
  const { t, f } = useI18n()
  const a = t.awards
  const baseId = useId()
  const q = useQuery({ queryKey: ['awards', 'list', null], queryFn: () => awardsApi.list() })
  if (q.isPending || q.isError) return null

  const months: { month: string; awards: Award[] }[] = []
  for (const w of q.data) {
    const last = months[months.length - 1]
    if (last?.month === w.month) last.awards.push(w)
    else months.push({ month: w.month, awards: [w] })
  }

  return (
    <section className="border-t border-line px-5 pt-4">
      <h3 className="font-display text-[0.9375rem] font-semibold">{a.history}</h3>
      {months.length === 0 ? (
        <p className="mt-1 text-[0.9375rem] text-muted">{a.noHistory}</p>
      ) : (
        <ul aria-label={a.history} className="mt-2 divide-y divide-line">
          {months.map((m) => (
            <li key={m.month} aria-labelledby={`${baseId}-${m.month}`} className="flex flex-col gap-1 py-2.5 sm:flex-row sm:items-baseline sm:gap-4">
              <h4 id={`${baseId}-${m.month}`} className="w-36 shrink-0 text-sm text-muted">
                {f.monthYear(m.month)}
              </h4>
              <ul className="flex flex-wrap gap-x-5 gap-y-1">
                {m.awards.map((w) => (
                  <li key={w.kind} className="flex items-center gap-1.5">
                    {w.kind === 'PLAYER_OF_MONTH' ? <Trophy aria-hidden className="size-4 text-gold" /> : <Flame aria-hidden className="size-4 text-gold" />}
                    <Link to={`/players/${w.player.username}`} className="font-medium hover:text-accent">
                      {w.player.realName}
                    </Link>
                    <span className="text-sm text-muted">
                      {w.kind === 'PLAYER_OF_MONTH' ? f.signedDecimal(w.overExpected) : t.common.games(w.games)}
                    </span>
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
