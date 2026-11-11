import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { Clock, Swords, Trophy } from 'lucide-react'
import { gamesApi, leaderboardApi, tournamentsApi, usersApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthProvider'
import { TrafonFacade } from '../components/brand/TrafonFacade'
import { Avatar } from '../components/chess/Avatar'
import { GameActions } from '../components/chess/GameActions'
import { GameRow } from '../components/chess/GameRow'
import { PlayerLink, Rating } from '../components/chess/PlayerLink'
import { FormStrip, ResultMark } from '../components/chess/ResultMark'
import { ButtonLink } from '../components/ui/Button'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorNotice } from '../components/ui/ErrorNotice'
import { Panel } from '../components/ui/Panel'
import { Skeleton, SkeletonRows } from '../components/ui/Skeleton'
import { signed } from '../i18n/format'
import { useI18n } from '../i18n/I18nProvider'
import { roundName } from '../i18n/rounds'
import { cn } from '../lib/cn'

export function HomePage() {
  const { me } = useAuth()
  const { t, f } = useI18n()
  const stats = useQuery({ queryKey: ['stats', me!.username], queryFn: () => usersApi.stats(me!.username) })
  const pending = useQuery({ queryKey: ['pending'], queryFn: gamesApi.pending })
  const myMatches = useQuery({ queryKey: ['myMatches'], queryFn: tournamentsApi.myMatches })
  const board = useQuery({ queryKey: ['leaderboard'], queryFn: leaderboardApi.get })
  const recent = useQuery({ queryKey: ['games', 'recent'], queryFn: () => gamesApi.list({ size: 8 }) })

  const firstName = me!.realName.split(' ')[0]
  const s = stats.data

  return (
    <div className="flex flex-col gap-6">
      {/* Rating hero: the facade */}
      <section className="brick relative overflow-hidden rounded-t-[2.5rem] rounded-b-panel text-on-accent">
        <TrafonFacade variant="line" className="pointer-events-none absolute right-[-1.5rem] bottom-[-1.25rem] hidden w-[23rem] text-on-accent/25 sm:block" />
        <div className="relative flex flex-col gap-6 p-6 sm:p-9">
          <div>
            <p className="text-[0.9375rem] text-on-accent/80">{t.home.greeting(firstName)}</p>
            {s ? (
              <p className="rise mt-1 font-display text-[4.5rem] leading-none font-bold tracking-[-0.04em] tabular-nums sm:text-[5.75rem]">
                {s.player.rating}
                {s.player.provisional && <span className="text-on-accent/60">?</span>}
                <span className="ml-2 align-top text-2xl font-medium tracking-normal text-on-accent/60">±{s.player.rd}</span>
              </p>
            ) : stats.isError ? (
              <p className="mt-2 font-display text-[4.5rem] leading-none font-bold">{me!.rating}</p>
            ) : (
              <Skeleton className="mt-2 h-20 w-56 opacity-30" />
            )}
            {s && (
              <p className="mt-3 text-[0.9375rem] text-on-accent/85">
                {s.player.rank ? t.home.ranked(s.player.rank) : t.home.provisional}
                {s.totals.games > 0 && `. ${t.home.ratedGames(s.totals.games)}`}
              </p>
            )}
          </div>
          <div className="flex flex-wrap items-center gap-x-5 gap-y-3">
            {s && s.recentForm.length > 0 && (
              <span className="rounded-full bg-on-accent/95 px-2 py-1.5">
                <FormStrip form={[...s.recentForm].reverse()} />
              </span>
            )}
            <Link to={`/players/${me!.username}`} className="text-[0.9375rem] font-semibold text-on-accent underline decoration-on-accent/40 underline-offset-4 hover:decoration-on-accent">
              {t.home.seeStats}
            </Link>
          </div>
        </div>
      </section>

      {/* Things that need you */}
      {(pending.data?.length ?? 0) > 0 && (
        <Panel title={t.home.waitingTitle} className="border-gold/50">
          <ul className="divide-y divide-line">
            {pending.data!.map((g) => (
              <li key={g.id} className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <Link to={`/games/${g.id}`} className="flex flex-wrap items-center gap-x-2 font-medium hover:text-accent">
                    {g.white.realName} <ResultMark result={g.result} className="text-accent" /> {g.black.realName}
                  </Link>
                  <p className="mt-0.5 flex items-start gap-1.5 text-[0.8125rem] text-muted">
                    <Clock aria-hidden className="mt-0.5 size-3.5 shrink-0" />
                    <span>
                      {t.home.loggedBy(g.reportedBy.realName, f.relative(g.createdAt))}
                      {g.autoConfirmAt && `, ${t.home.countsAutomatically(f.relative(g.autoConfirmAt))}`}
                    </span>
                  </p>
                </div>
                <div className="shrink-0">
                  <GameActions game={g} size="sm" />
                </div>
              </li>
            ))}
          </ul>
        </Panel>
      )}

      {(myMatches.data?.length ?? 0) > 0 && (
        <Panel title={t.home.toPlayTitle}>
          <ul className="divide-y divide-line">
            {myMatches.data!.map((m) => {
              const opponent = m.white.id === me!.id ? m.black : m.white
              return (
                <li key={m.matchId} className="flex items-center justify-between gap-3 px-5 py-3.5">
                  <div className="flex min-w-0 items-center gap-3">
                    <Swords aria-hidden className="size-4.5 shrink-0 text-muted" />
                    <div className="min-w-0">
                      <p className="truncate font-medium">{t.home.versus(opponent.realName)}</p>
                      <p className="truncate text-[0.8125rem] text-muted">
                        {m.tournamentName}, {roundName(t, m.format, m.round, m.totalRounds)}
                        {m.needsReplay && <span className="text-gold">, {t.home.replayAfterDraw}</span>}
                      </p>
                    </div>
                  </div>
                  <ButtonLink to={`/log?match=${m.matchId}`} variant="secondary" size="sm" className="shrink-0">
                    {t.home.logResult}
                  </ButtonLink>
                </li>
              )
            })}
          </ul>
        </Panel>
      )}

      <div className="grid gap-6 lg:grid-cols-[1.5fr_1fr]">
        <Panel
          title={t.home.latestGames}
          action={
            <Link to="/games" className="text-sm text-muted hover:text-accent">
              {t.home.allGames}
            </Link>
          }
        >
          {recent.isPending ? (
            <SkeletonRows rows={5} />
          ) : recent.isError ? (
            <ErrorNotice error={recent.error} onRetry={() => recent.refetch()} />
          ) : recent.data.content.length === 0 ? (
            <EmptyState
              icon={<Swords />}
              title={t.home.noGamesTitle}
              action={<ButtonLink to="/log">{t.nav.logGame}</ButtonLink>}
            >
              {t.home.noGamesBody}
            </EmptyState>
          ) : (
            <ul className="divide-y divide-line pb-2">
              {recent.data.content.map((g) => (
                <GameRow key={g.id} game={g} />
              ))}
            </ul>
          )}
        </Panel>

        <Panel
          title={t.home.topOfTable}
          action={
            <Link to="/leaderboard" className="text-sm text-muted hover:text-accent">
              {t.home.fullTable}
            </Link>
          }
        >
          {board.isPending ? (
            <SkeletonRows rows={5} />
          ) : board.isError ? (
            <ErrorNotice error={board.error} onRetry={() => board.refetch()} />
          ) : (
            <ol className="px-2 pb-3">
              {board.data.slice(0, 6).map((row) => (
                <li key={row.player.id} className="flex items-center gap-3 rounded-control px-3 py-2.5">
                  <span className={cn('w-5 text-right font-display font-semibold tabular-nums', row.rank === 1 ? 'text-accent' : 'text-faint')}>
                    {row.rank ?? '–'}
                  </span>
                  <Avatar name={row.player.realName} seed={row.player.username} size="sm" />
                  <PlayerLink player={row.player} showAvatar={false} className="flex-1" />
                  <Rating value={row.player.rating} provisional={row.player.provisional} className="font-medium" />
                  <span className={cn('w-10 text-right text-[0.8125rem] tabular-nums', row.trend > 0 ? 'text-win' : row.trend < 0 ? 'text-loss' : 'text-faint')}>
                    {row.games > 0 ? signed(row.trend) : ''}
                  </span>
                </li>
              ))}
            </ol>
          )}
        </Panel>
      </div>

      <ActiveTournaments />
    </div>
  )
}

function ActiveTournaments() {
  const { t } = useI18n()
  const q = useQuery({ queryKey: ['tournaments'], queryFn: tournamentsApi.list })
  const active = q.data?.filter((x) => x.status === 'IN_PROGRESS' || x.status === 'REGISTRATION') ?? []
  if (q.isPending || active.length === 0) return null
  return (
    <Panel title={t.home.tournaments}>
      <ul className="grid gap-px bg-line sm:grid-cols-2">
        {active.map((x) => (
          <li key={x.id} className="bg-surface sm:[&:last-child:nth-child(odd)]:col-span-2">
            <Link to={`/tournaments/${x.id}`} className="flex items-center gap-3 px-5 py-4 hover:bg-raised/70">
              <Trophy aria-hidden className="size-5 shrink-0 text-accent" />
              <div className="min-w-0">
                <p className="truncate font-medium">{x.name}</p>
                <p className="text-[0.8125rem] text-muted">
                  {t.format[x.format]}, {t.common.players(x.participantCount)}. {t.tournamentStatus[x.status]}
                </p>
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </Panel>
  )
}
