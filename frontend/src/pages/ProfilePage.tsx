import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { Crown, Flame, Skull, Swords, Target, Trophy } from 'lucide-react'
import { gamesApi, usersApi } from '../api/endpoints'
import type { Stats, Tally } from '../api/types'
import { AwardBadges } from '../components/awards/AwardBadges'
import { reviewYear } from '../components/year/season'
import { ActivityBars } from '../components/chess/ActivityBars'
import { Avatar } from '../components/chess/Avatar'
import { GameRow } from '../components/chess/GameRow'
import { Rating } from '../components/chess/PlayerLink'
import { RatingChart } from '../components/chess/RatingChart'
import { FormStrip } from '../components/chess/ResultMark'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorNotice } from '../components/ui/ErrorNotice'
import { Panel } from '../components/ui/Panel'
import { Skeleton } from '../components/ui/Skeleton'
import { useI18n } from '../i18n/I18nProvider'
import { cn } from '../lib/cn'
import { isNotFound } from '../api/client'
import { NotFoundPage } from './NotFoundPage'

export function ProfilePage() {
  const username = useParams().username ?? ''
  const { t, f } = useI18n()
  const pr = t.profile
  const q = useQuery({ queryKey: ['stats', username], queryFn: () => usersApi.stats(username), enabled: !!username })
  const games = useQuery({ queryKey: ['games', { player: username, page: 0, size: 8 }], queryFn: () => gamesApi.list({ player: username, size: 8 }), enabled: !!username })

  if (q.isPending) return <ProfileSkeleton />
  if (q.isError) {
    if (isNotFound(q.error)) return <NotFoundPage what="player" />
    return (
      <Panel>
        <ErrorNotice error={q.error} onRetry={() => q.refetch()} />
      </Panel>
    )
  }
  const s = q.data
  const p = s.player
  const played = s.totals.games > 0

  return (
    <div className="flex flex-col gap-6">
      {/* Identity + rating */}
      <section className="flex flex-col gap-6 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex min-w-0 items-end gap-4">
          <Avatar name={p.realName} seed={p.username} size="lg" />
          <div className="min-w-0">
            <h1 className="font-display text-[1.875rem] leading-tight font-bold tracking-[-0.025em] break-words sm:text-[2.25rem]">{p.realName}</h1>
            <p className="text-[0.9375rem] text-muted">
              {pr.memberSince(p.username, f.date(p.memberSince))}
              {p.role === 'ADMIN' && <span className="ml-2 rounded-md bg-raised px-1.5 py-0.5 text-[0.75rem] text-muted">{t.common.admin}</span>}
            </p>
          </div>
        </div>
        <div className="flex items-end gap-6 sm:text-right">
          <div>
            <p className="text-[0.8125rem] text-muted">{p.rank ? pr.rank(p.rank) : pr.provisional}</p>
            <p className="font-display text-[3.25rem] leading-none font-bold tracking-[-0.04em]">
              <Rating value={p.rating} provisional={p.provisional} />
            </p>
            <p className="mt-1 text-[0.8125rem] text-faint">{pr.deviation(p.rd)}</p>
          </div>
          <div className="border-l border-line pl-6">
            <p className="text-[0.8125rem] text-muted">{pr.peak}</p>
            <p className="font-display text-2xl font-semibold text-accent tabular-nums">{s.peakRating}</p>
            <p className="mt-1 text-[0.8125rem] text-faint">{pr.low(s.lowestRating)}</p>
          </div>
        </div>
      </section>

      <div className="flex flex-wrap items-center gap-x-4 gap-y-3">
        <AwardBadges username={p.username} />
        {played && (
          <Link to={`/year/${reviewYear().year}/${p.username}`} className="text-[0.9375rem] font-medium text-accent hover:underline">
            {t.year.profileLink(reviewYear().year)}
          </Link>
        )}
      </div>

      {!played ? (
        <Panel>
          <EmptyState icon={<Swords />} title={pr.noGamesTitle(p.realName.split(' ')[0])}>
            {pr.noGamesBody}
          </EmptyState>
        </Panel>
      ) : (
        <>
          <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
            <Panel title={pr.ratingOverTime}>
              <div className="px-2 pt-2 pb-4 sm:px-4">
                <RatingChart history={s.ratingHistory} memberSince={p.memberSince} />
              </div>
            </Panel>
            <RecordPanel stats={s} />
          </div>

          <div className="grid gap-6 md:grid-cols-2">
            <ColoursPanel stats={s} />
            <StreaksPanel stats={s} />
          </div>

          <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
            <HeadToHeadPanel stats={s} />
            <HighlightsPanel stats={s} />
          </div>

          <Panel title={pr.gamesPerMonth}>
            <div className="px-3 pt-1 pb-3">
              <ActivityBars activity={s.activity} />
            </div>
          </Panel>
        </>
      )}

      <Panel
        title={pr.recentGames}
        action={
          <Link to={`/games?player=${encodeURIComponent(p.username)}`} className="text-sm text-muted hover:text-accent">
            {pr.allGames}
          </Link>
        }
      >
        {games.data && games.data.content.length > 0 ? (
          <ul className="divide-y divide-line pb-2">
            {games.data.content.map((g) => (
              <GameRow key={g.id} game={g} />
            ))}
          </ul>
        ) : games.isPending ? (
          <div className="p-5">
            <Skeleton className="h-10" />
          </div>
        ) : games.isError ? (
          <ErrorNotice error={games.error} onRetry={() => games.refetch()} />
        ) : (
          <p className="px-5 pb-5 text-[0.9375rem] text-muted">{pr.noRecentGames}</p>
        )}
      </Panel>
    </div>
  )
}

function RecordPanel({ stats: s }: { stats: Stats }) {
  const { t, f } = useI18n()
  const pr = t.profile
  const tt = s.totals
  const seg = (n: number) => `${(n / tt.games) * 100}%`
  return (
    <Panel title={pr.record} className="flex flex-col">
      <div className="flex flex-1 flex-col gap-5 px-5 pt-3 pb-5">
        <div className="flex items-baseline justify-between">
          <p className="font-display text-[2.5rem] leading-none font-bold tabular-nums">{f.pct(tt.winRate)}</p>
          <p className="text-[0.9375rem] text-muted">{pr.winRate}</p>
        </div>
        <div className="flex h-3 gap-[2px] overflow-hidden rounded-full" role="img" aria-label={pr.recordBar(tt.wins, tt.draws, tt.losses)}>
          {tt.wins > 0 && <span className="bg-win" style={{ width: seg(tt.wins) }} />}
          {tt.draws > 0 && <span className="bg-draw" style={{ width: seg(tt.draws) }} />}
          {tt.losses > 0 && <span className="bg-loss" style={{ width: seg(tt.losses) }} />}
        </div>
        <dl className="grid grid-cols-3 gap-2 text-center">
          <Count label={pr.wins} value={tt.wins} swatch="bg-win" />
          <Count label={pr.draws} value={tt.draws} swatch="bg-draw" />
          <Count label={pr.losses} value={tt.losses} swatch="bg-loss" />
        </dl>
        <div className="mt-auto flex items-center justify-between gap-3 border-t border-line pt-4 text-[0.9375rem]">
          <span className="text-muted">{pr.score}</span>
          <span className="text-right font-medium tabular-nums">{pr.scoreOf(f.pct(tt.scorePct), tt.games)}</span>
        </div>
        <div className="flex flex-col gap-2 text-[0.9375rem]">
          <span className="text-muted">{pr.form}</span>
          <FormStrip form={[...s.recentForm].reverse()} />
        </div>
      </div>
    </Panel>
  )
}

function Count({ label, value, swatch }: { label: string; value: number; swatch: string }) {
  return (
    <div className="flex flex-col-reverse rounded-control bg-raised/70 py-2.5">
      <dt className="flex items-center justify-center gap-1.5 text-[0.8125rem] text-muted">
        <span aria-hidden className={cn('size-2 rounded-full', swatch)} />
        {label}
      </dt>
      <dd className="font-display text-xl font-semibold tabular-nums">{value}</dd>
    </div>
  )
}

function ColoursPanel({ stats: s }: { stats: Stats }) {
  const { t } = useI18n()
  return (
    <Panel title={t.profile.colours}>
      <div className="grid grid-cols-2 gap-px bg-line">
        <ColourColumn label={t.common.white} tally={s.white} favourite={s.favoriteColor === 'WHITE'} light />
        <ColourColumn label={t.common.black} tally={s.black} favourite={s.favoriteColor === 'BLACK'} />
      </div>
    </Panel>
  )
}

function ColourColumn({ label, tally, favourite, light }: { label: string; tally: Tally; favourite: boolean; light?: boolean }) {
  const { t, f } = useI18n()
  const pr = t.profile
  return (
    <div className="bg-surface px-5 py-5">
      <div className="flex flex-wrap items-center gap-2">
        <span aria-hidden className={cn('size-4 rounded-[4px] border border-faint', light ? 'bg-trim' : 'bg-roof')} />
        <span className="font-medium">{label}</span>
        {favourite && <span className="text-[0.8125rem] text-accent sm:ml-auto">{pr.strongerSide}</span>}
      </div>
      <p className="mt-4 font-display text-[2rem] leading-none font-bold tabular-nums">{tally.games ? f.pct(tally.scorePct) : '–'}</p>
      <p className="mt-1 text-[0.8125rem] text-muted">{pr.scoreIn(tally.games)}</p>
      <p className="mt-3 text-[0.9375rem] text-muted tabular-nums">
        <span className="text-ink">{tally.wins}</span> {pr.colourWords.won}, <span className="text-ink">{tally.draws}</span> {pr.colourWords.drawn},{' '}
        <span className="text-ink">{tally.losses}</span> {pr.colourWords.lost}
      </p>
    </div>
  )
}

function StreaksPanel({ stats: s }: { stats: Stats }) {
  const { t } = useI18n()
  const pr = t.profile
  const c = s.streaks.current
  const currentText = c.type === null ? t.outcome.noGames : pr.currentStreak(c.type, c.length)
  return (
    <Panel title={pr.streaks}>
      <div className="px-5 pt-2 pb-5">
        <p className={cn('flex items-center gap-2 text-[0.9375rem]', c.type === 'WIN' ? 'text-win' : c.type === 'LOSS' ? 'text-loss' : 'text-muted')}>
          <Flame aria-hidden className="size-4.5 shrink-0" /> {pr.rightNow(currentText)}
        </p>
        <dl className="mt-4 grid grid-cols-3 gap-4">
          <Streak label={pr.longestWin} value={s.streaks.longestWin} tone="text-win" />
          <Streak label={pr.longestUnbeaten} value={s.streaks.longestUnbeaten} tone="text-ink" />
          <Streak label={pr.longestLoss} value={s.streaks.longestLoss} tone="text-loss" />
        </dl>
      </div>
    </Panel>
  )
}

function Streak({ label, value, tone }: { label: string; value: number; tone: string }) {
  return (
    <div className="flex flex-col-reverse">
      <dt className="mt-1.5 text-[0.8125rem] leading-snug text-muted">{label}</dt>
      <dd className={cn('font-display text-[2rem] leading-none font-bold tabular-nums', tone)}>{value}</dd>
    </div>
  )
}

function HeadToHeadPanel({ stats: s }: { stats: Stats }) {
  const { t, f } = useI18n()
  const pr = t.profile
  return (
    <Panel title={pr.headToHead}>
      <table className="w-full text-[0.9375rem]">
        <caption className="sr-only">{pr.h2hCaption}</caption>
        <thead>
          <tr className="text-left text-[0.8125rem] text-muted">
            <th scope="col" className="px-5 py-2 font-medium">{pr.opponent}</th>
            <th scope="col" className="px-3 py-2 text-right font-medium">{pr.games}</th>
            <th scope="col" className="hidden px-3 py-2 text-right font-medium sm:table-cell">{t.leaderboard.wdl}</th>
            <th scope="col" className="px-5 py-2 text-right font-medium sm:text-left">{pr.score}</th>
          </tr>
        </thead>
        <tbody>
          {s.headToHead.map((h) => (
            <tr key={h.opponent.id} className="border-t border-line">
              <td className="px-5 py-2.5">
                <Link to={`/players/${h.opponent.username}`} className="flex items-center gap-2.5 hover:text-accent">
                  <Avatar name={h.opponent.realName} seed={h.opponent.username} size="sm" />
                  <span className="truncate">{h.opponent.realName}</span>
                </Link>
              </td>
              <td className="px-3 py-2.5 text-right tabular-nums">{h.games}</td>
              <td className="hidden px-3 py-2.5 text-right text-muted tabular-nums sm:table-cell">
                {h.wins} / {h.draws} / {h.losses}
              </td>
              <td className="px-5 py-2.5 sm:w-44">
                <div className="flex items-center gap-2">
                  <div className="relative hidden h-1.5 flex-1 overflow-hidden rounded-full bg-raised sm:block">
                    <span
                      className={cn('absolute inset-y-0 left-0 rounded-full', h.scorePct > 0.5 ? 'bg-win' : h.scorePct < 0.5 ? 'bg-loss' : 'bg-draw')}
                      style={{ width: `${Math.max(h.scorePct * 100, 3)}%` }}
                    />
                  </div>
                  <span className="ml-auto w-12 text-right text-[0.8125rem] tabular-nums">{f.pct(h.scorePct)}</span>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </Panel>
  )
}

function HighlightsPanel({ stats: s }: { stats: Stats }) {
  const { t, f } = useI18n()
  const pr = t.profile
  const items: { icon: ReactNode; label: string; value: ReactNode }[] = []
  if (s.bestWin)
    items.push({
      icon: <Target className="text-win" />,
      label: pr.bestWin,
      value: (
        <Link to={`/games/${s.bestWin.gameId}`} className="hover:text-accent">
          {pr.bestWinValue(s.bestWin.opponent.realName, s.bestWin.opponentRating)}
        </Link>
      ),
    })
  if (s.nemesis) items.push({ icon: <Skull className="text-loss" />, label: pr.nemesis, value: pr.nemesisValue(s.nemesis.opponent.realName, s.nemesis.losses) })
  if (s.favoriteVictim)
    items.push({
      icon: <Crown className="text-gold" />,
      label: pr.favouriteOpponent,
      value: pr.favouriteOpponentValue(s.favoriteVictim.opponent.realName, s.favoriteVictim.wins),
    })
  if (s.favoriteOpening)
    items.push({
      icon: <Swords className="text-muted" />,
      label: pr.mostPlayedOpening,
      value: pr.openingValue(s.favoriteOpening.name, s.favoriteOpening.games, f.pct(s.favoriteOpening.scorePct)),
    })
  items.push({ icon: <Trophy className="text-accent" />, label: pr.tournamentsWon, value: s.tournamentWins === 0 ? pr.noneYet : String(s.tournamentWins) })
  return (
    <Panel title={pr.highlights}>
      <ul className="flex flex-col gap-4 px-5 pt-2 pb-5">
        {items.map((it) => (
          <li key={it.label} className="flex gap-3">
            <span aria-hidden className="mt-0.5 [&_svg]:size-4.5">
              {it.icon}
            </span>
            <div className="min-w-0">
              <p className="text-[0.8125rem] text-muted">{it.label}</p>
              <p className="text-[0.9375rem]">{it.value}</p>
            </div>
          </li>
        ))}
      </ul>
    </Panel>
  )
}

function ProfileSkeleton() {
  const { t } = useI18n()
  return (
    <div className="flex flex-col gap-6" role="status" aria-label={t.profile.loadingProfile}>
      <div className="flex items-center gap-4">
        <Skeleton className="h-[4.75rem] w-16 rounded-t-full rounded-b-[8px]" />
        <div className="flex flex-col gap-2">
          <Skeleton className="h-8 w-56" />
          <Skeleton className="h-4 w-40" />
        </div>
      </div>
      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <Skeleton className="h-80 rounded-panel" />
        <Skeleton className="h-80 rounded-panel" />
      </div>
    </div>
  )
}
