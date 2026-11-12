import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { Trophy } from 'lucide-react'
import { awardsApi } from '../../api/endpoints'
import type { AwardRace, AwardStanding } from '../../api/types'
import { useI18n } from '../../i18n/I18nProvider'
import { cn } from '../../lib/cn'
import { Avatar } from '../chess/Avatar'
import { PlayerLink } from '../chess/PlayerLink'
import { Panel } from '../ui/Panel'
import { SkeletonRows } from '../ui/Skeleton'
import { previousMonth } from './month'

/** Home page: last month's winners (or who leads while it's being decided) and this month's top three. */
export function MonthlyAwardCard() {
  const { t } = useI18n()
  const a = t.awards
  const current = useQuery({ queryKey: ['awards', 'race', 'current'], queryFn: () => awardsApi.race() })
  const prev = current.data ? previousMonth(current.data.month) : null
  const previous = useQuery({ queryKey: ['awards', 'race', prev], queryFn: () => awardsApi.race(prev!), enabled: !!prev })

  // A nice extra: if it can't load, the home page goes without it.
  if (current.isError) return null
  return (
    <Panel
      title={a.title}
      action={
        <Link to="/leaderboard#awards" className="text-sm text-muted hover:text-accent">
          {a.seeStandings}
        </Link>
      }
    >
      {current.isPending ? (
        <SkeletonRows rows={3} />
      ) : (
        <div className="flex flex-col gap-5 px-5 pt-2 pb-5">
          <div className="grid gap-6 md:grid-cols-2">
            {previous.data && <LastMonth race={previous.data} />}
            <ThisMonth race={current.data} />
          </div>
          <p className="text-[0.8125rem] text-muted">{a.shortRule(current.data.minGames)}</p>
        </div>
      )}
    </Panel>
  )
}

function LastMonth({ race }: { race: AwardRace }) {
  const { t, f } = useI18n()
  const a = t.awards
  const pom = race.playerOfMonth
  if (!pom.winner && pom.standings.length === 0) return null
  const leader: AwardStanding | undefined = pom.standings[0]?.qualified && pom.standings[0].overExpected > 0 ? pom.standings[0] : undefined
  const active = race.mostActive.winner

  return (
    <section>
      <h3 className="text-[0.8125rem] text-muted">{a.inMonth(f.month(race.month))}</h3>
      {race.status === 'DECIDED' ? (
        pom.winner ? (
          <>
            <div className="mt-2 flex items-center gap-3 rounded-xl border border-gold/40 bg-gold/8 px-4 py-3">
              <Trophy aria-hidden className="size-6 shrink-0 text-gold" />
              <Avatar name={pom.winner.player.realName} seed={pom.winner.player.username} />
              <div className="min-w-0">
                <Link to={`/players/${pom.winner.player.username}`} className="block truncate font-display text-lg leading-tight font-semibold hover:text-accent">
                  {pom.winner.player.realName}
                </Link>
                <p className="text-sm text-muted">
                  {a.over(f.signedDecimal(pom.winner.overExpected))}, {t.common.games(pom.winner.games)}
                </p>
              </div>
            </div>
            {active && <p className="mt-2 text-sm text-muted">{a.mostActiveLine(active.player.realName, t.common.games(active.games))}</p>}
          </>
        ) : (
          <p className="mt-2 text-[0.9375rem] text-muted">{a.noWinner}</p>
        )
      ) : (
        <>
          {leader && (
            <div className="mt-2 flex items-center gap-3">
              <span className="rounded-full bg-gold/12 px-2 py-0.5 text-[0.8125rem] font-medium text-gold">{a.leading}</span>
              <PlayerLink player={leader.player} />
              <span className="ml-auto text-sm text-muted tabular-nums">{f.signedDecimal(leader.overExpected)}</span>
            </div>
          )}
          <p className="mt-2 text-sm text-muted">{a.decides(f.date(race.decidesAt))}</p>
        </>
      )}
    </section>
  )
}

function ThisMonth({ race }: { race: AwardRace }) {
  const { t, f } = useI18n()
  const a = t.awards
  const top = race.playerOfMonth.standings.slice(0, 3)
  return (
    <section>
      <h3 className="text-[0.8125rem] text-muted">{a.soFar(f.month(race.month))}</h3>
      {top.length === 0 ? (
        <p className="mt-2 text-[0.9375rem] text-muted">{a.noGamesYet}</p>
      ) : (
        <ol className="mt-1">
          {top.map((s, i) => (
            <li
              key={s.player.id}
              title={s.qualified ? undefined : a.notQualified(race.minGames)}
              className={cn('flex items-center gap-3 py-1.5', !s.qualified && 'text-muted')}
            >
              <span className={cn('w-4 text-right font-display font-semibold tabular-nums', i === 0 && s.qualified ? 'text-gold' : 'text-faint')}>{i + 1}</span>
              <PlayerLink player={s.player} className="flex-1" />
              {!s.qualified && <span className="text-[0.8125rem] text-faint">{a.gamesOf(s.games, race.minGames)}</span>}
              <span className="w-12 text-right font-medium tabular-nums">{f.signedDecimal(s.overExpected)}</span>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
