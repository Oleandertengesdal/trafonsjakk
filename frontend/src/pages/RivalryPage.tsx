// Two players against each other: their record, streaks, the biggest upset, their ratings side by side,
// their best-played games against each other and every game between them.
import type { ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { Swords } from 'lucide-react'
import { isNotFound } from '../api/client'
import { rivalryApi, usersApi } from '../api/endpoints'
import type { PlayerRef, RivalryRecord } from '../api/types'
import { Avatar } from '../components/chess/Avatar'
import { GameRow } from '../components/chess/GameRow'
import { Rating } from '../components/chess/PlayerLink'
import { RivalryChart } from '../components/chess/RivalryChart'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorNotice } from '../components/ui/ErrorNotice'
import { Select } from '../components/ui/Field'
import { Panel } from '../components/ui/Panel'
import { Skeleton } from '../components/ui/Skeleton'
import { useI18n } from '../i18n/I18nProvider'
import { NotFoundPage } from './NotFoundPage'

export function RivalryPage() {
  const { a = '', b = '' } = useParams()
  const { t, f } = useI18n()
  const r = t.rivalry
  const navigate = useNavigate()
  const q = useQuery({ queryKey: ['rivalry', a.toLowerCase(), b.toLowerCase()], queryFn: () => rivalryApi.get(a, b), enabled: !!a && !!b })
  const sa = useQuery({ queryKey: ['stats', a], queryFn: () => usersApi.stats(a), enabled: !!a })
  const sb = useQuery({ queryKey: ['stats', b], queryFn: () => usersApi.stats(b), enabled: !!b })
  const users = useQuery({ queryKey: ['users'], queryFn: usersApi.list })

  if (q.isPending) return <Skeleton className="h-96 w-full rounded-panel" />
  if (q.isError) {
    if (isNotFound(q.error)) return <NotFoundPage what="player" />
    return (
      <Panel>
        <ErrorNotice error={q.error} onRetry={() => q.refetch()} />
      </Panel>
    )
  }
  const d = q.data
  const first = (p: PlayerRef) => p.realName.split(' ')[0]
  const side = (s: 'A' | 'B' | null) => (s === 'A' ? first(d.a) : s === 'B' ? first(d.b) : null)
  const o = d.overall

  return (
    <div className="flex flex-col gap-6">
      <section aria-label={r.title(d.a.realName, d.b.realName)} className="flex flex-col gap-5">
        <h1 className="sr-only">{r.title(d.a.realName, d.b.realName)}</h1>
        <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-3 sm:gap-6">
          <PlayerSide player={d.a} />
          <div className="text-center">
            <p className="font-display text-[2.5rem] leading-none font-bold tracking-[-0.03em] tabular-nums sm:text-[3.25rem]">
              {f.decimal1(o.aPoints).replace(/[.,]0$/, '')}
              <span className="px-1.5 text-faint">–</span>
              {f.decimal1(o.games - o.aPoints).replace(/[.,]0$/, '')}
            </p>
            <p className="mt-1.5 text-[0.8125rem] text-muted">{r.gamesPlayed(o.games)}</p>
          </div>
          <PlayerSide player={d.b} end />
        </div>
        {o.games > 0 && <SplitBar record={o} a={first(d.a)} b={first(d.b)} />}
        <label className="flex flex-wrap items-center gap-2 text-sm text-muted">
          {r.compareWith(first(d.a))}
          <Select
            className="w-auto"
            value={d.b.username}
            onChange={(e) => navigate(`/rivalry/${encodeURIComponent(d.a.username)}/${encodeURIComponent(e.target.value)}`)}
          >
            {users.data
              ?.filter((u) => u.id !== d.a.id)
              .map((u) => (
                <option key={u.id} value={u.username}>
                  {u.realName}
                </option>
              ))}
          </Select>
        </label>
      </section>

      {o.games === 0 ? (
        <Panel>
          <EmptyState icon={<Swords />} title={r.noGamesTitle}>
            {r.noGamesBody(first(d.a), first(d.b))}
          </EmptyState>
        </Panel>
      ) : (
        <>
          <dl className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Fact label={r.currentStreak} value={d.currentStreak.side ? r.streak(side(d.currentStreak.side)!, d.currentStreak.length) : r.noStreak} />
            <Fact label={r.longestStreak} value={d.longestStreak.side ? r.streak(side(d.longestStreak.side)!, d.longestStreak.length) : '–'} />
            <Fact
              label={r.biggestUpset}
              value={
                d.biggestUpset ? (
                  <Link to={`/games/${d.biggestUpset.gameId}`} className="hover:text-accent">
                    {r.upset(side(d.biggestUpset.winner)!, d.biggestUpset.gap)}
                  </Link>
                ) : (
                  r.noUpset
                )
              }
            />
            <Fact label={r.byColour(first(d.a))} value={r.colours(score(d.aAsWhite), score(d.aAsBlack), f.pct)} />
          </dl>

          <Panel title={r.ratings}>
            <div className="px-2 pt-2 pb-4 sm:px-4">
              {sa.data && sb.data ? (
                <RivalryChart
                  a={{ name: d.a.realName, since: sa.data.player.memberSince, history: sa.data.ratingHistory }}
                  b={{ name: d.b.realName, since: sb.data.player.memberSince, history: sb.data.ratingHistory }}
                />
              ) : sa.isError || sb.isError ? (
                <ErrorNotice error={sa.error ?? sb.error} onRetry={() => void Promise.all([sa.refetch(), sb.refetch()])} />
              ) : (
                <Skeleton className="h-64" />
              )}
            </div>
          </Panel>

          <Panel title={r.bestGames}>
            {d.bestGames.length === 0 ? (
              <p className="max-w-[65ch] px-5 pb-5 text-[0.9375rem] text-muted">{r.noBestGames}</p>
            ) : (
              <ul className="divide-y divide-line pb-2">
                {d.bestGames.map((g) => (
                  <li key={g.gameId}>
                    <Link to={`/games/${g.gameId}`} className="flex items-baseline justify-between gap-3 px-5 py-3 text-[0.9375rem] hover:bg-raised/60">
                      <span className="min-w-0 truncate">{f.date(g.playedAt)}</span>
                      <span className="shrink-0 text-muted tabular-nums">
                        {r.accuracyPair(first(d.a), f.pct(g.aAccuracy / 100), first(d.b), f.pct(g.bAccuracy / 100))}
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Panel>

          <Panel title={r.allGames(o.games)}>
            <ul className="divide-y divide-line pb-2">
              {d.games.map((g) => (
                <GameRow key={g.id} game={g} />
              ))}
            </ul>
          </Panel>
        </>
      )}
    </div>
  )
}

function score(rec: RivalryRecord) {
  return rec.games ? rec.aPoints / rec.games : null
}

function PlayerSide({ player, end }: { player: PlayerRef; end?: boolean }) {
  return (
    <Link
      to={`/players/${player.username}`}
      className={`flex min-w-0 flex-col items-center gap-2 hover:text-accent sm:flex-row ${end ? 'sm:flex-row-reverse sm:text-right' : ''}`}
    >
      <Avatar name={player.realName} seed={player.username} size="lg" />
      <span className="min-w-0 text-center sm:text-inherit">
        <span className="block font-display text-lg leading-tight font-semibold break-words sm:text-xl">{player.realName}</span>
        <span className="text-sm text-muted">
          <Rating value={player.rating} provisional={player.provisional} />
        </span>
      </span>
    </Link>
  )
}

/** A's wins, draws, B's wins as one bar, with the counts underneath. */
function SplitBar({ record, a, b }: { record: RivalryRecord; a: string; b: string }) {
  const r = useI18n().t.rivalry
  const w = (n: number) => `${(n / record.games) * 100}%`
  return (
    <div>
      <div role="img" aria-label={r.split(a, record.aWins, record.draws, b, record.bWins)} className="flex h-3 gap-[2px] overflow-hidden rounded-full">
        {record.aWins > 0 && <span className="bg-accent" style={{ width: w(record.aWins) }} />}
        {record.draws > 0 && <span className="bg-draw" style={{ width: w(record.draws) }} />}
        {record.bWins > 0 && <span className="bg-ink" style={{ width: w(record.bWins) }} />}
      </div>
      <p className="mt-2 flex justify-between gap-3 text-[0.8125rem] text-muted tabular-nums">
        <span>{r.wins(a, record.aWins)}</span>
        <span>{r.draws(record.draws)}</span>
        <span>{r.wins(b, record.bWins)}</span>
      </p>
    </div>
  )
}

function Fact({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex flex-col-reverse rounded-panel border border-line bg-surface px-4 py-3.5">
      <dt className="mt-1 text-[0.8125rem] text-muted">{label}</dt>
      <dd className="font-display text-[1.0625rem] leading-snug font-semibold">{value}</dd>
    </div>
  )
}
