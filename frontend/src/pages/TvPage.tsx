import { lazy, Suspense, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { Maximize, X } from 'lucide-react'
import { gamesApi, leaderboardApi, liveApi } from '../api/endpoints'
import type { Game, LiveGame } from '../api/types'
import { finalState, parseMoves } from '../chess/moves'
import { LogoMark, Wordmark } from '../components/brand/LogoMark'
import { Avatar } from '../components/chess/Avatar'
import { Rating } from '../components/chess/PlayerLink'
import { LiveDot } from '../components/chess/Live'
import { useWakeLock } from '../hooks/useWakeLock'
import { useI18n } from '../i18n/I18nProvider'
import { cn } from '../lib/cn'
import { applyTheme } from '../lib/theme'

const LiveBoard = lazy(() => import('../components/chess/LiveBoard'))

/**
 * A read-only scoreboard for a big screen at the venue: the games being played (boards update every few
 * seconds), the rating list and the latest results. Always dark, sized to be read from across the room,
 * and keeps the screen on. Someone has to be logged in on the TV's browser, like anywhere else in the app.
 */
export function TvPage() {
  const { t } = useI18n()
  const tv = t.tv
  const live = useQuery({ queryKey: ['live'], queryFn: liveApi.running, refetchInterval: 4_000 })
  const board = useQuery({ queryKey: ['leaderboard'], queryFn: leaderboardApi.get, refetchInterval: 60_000 })
  const recent = useQuery({
    queryKey: ['games', 'tv'],
    queryFn: () => gamesApi.list({ status: 'CONFIRMED', size: 6 }),
    refetchInterval: 60_000,
  })
  useWakeLock(true)

  // The TV is always dark; the viewer's own choice comes back when they leave
  useEffect(() => {
    applyTheme('dark')
    return () => applyTheme()
  }, [])

  const games = live.data ?? []
  // Everyone who has played, best first; the rank shows once a rating has settled
  const ranked = (board.data ?? [])
    .filter((r) => r.games > 0)
    .sort((a, b) => b.player.rating - a.player.rating)
    .slice(0, 10)

  return (
    <div className="flex min-h-dvh flex-col bg-page px-[3vw] py-[2.5vh] text-ink">
      <header className="flex items-center justify-between gap-6">
        <div className="flex items-center gap-3">
          <LogoMark className="size-[clamp(2rem,3vw,3.5rem)]" />
          <Wordmark className="text-[clamp(1.5rem,2.4vw,2.75rem)]" />
        </div>
        <div className="flex items-center gap-4">
          <Clock />
          <TvControls fullscreenLabel={tv.fullscreen} exitLabel={tv.exit} />
        </div>
      </header>

      <main className="mt-[3vh] grid flex-1 gap-[3vw] lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <section aria-labelledby="tv-live" className="flex min-h-0 flex-col">
          <h2 id="tv-live" className="flex items-center gap-4 font-display text-[clamp(1.25rem,1.8vw,2.25rem)] font-semibold">
            {tv.liveNow}
            {games.length > 0 && <LiveDot label={t.live.liveBadge} />}
          </h2>
          {games.length === 0 ? (
            <p className="mt-[3vh] text-[clamp(1.1rem,1.5vw,1.75rem)] text-muted">{tv.noLive}</p>
          ) : (
            <ul className={cn('mt-[2.5vh] grid flex-1 gap-[2vw]', games.length === 1 ? 'grid-cols-1' : 'grid-cols-2')}>
              {games.slice(0, 4).map((g) => (
                <TvGame key={g.id} game={g} size={games.length === 1 ? 'max-w-[min(100%,64vh)]' : games.length === 2 ? 'max-w-[min(100%,56vh)]' : 'max-w-[min(100%,27vh)]'} />
              ))}
            </ul>
          )}
        </section>

        <aside className="flex min-w-0 flex-col gap-[4vh]">
          <section aria-labelledby="tv-ratings">
            <h2 id="tv-ratings" className="font-display text-[clamp(1.25rem,1.8vw,2.25rem)] font-semibold">
              {tv.ratings}
            </h2>
            <ol className="mt-[1.5vh] divide-y divide-line border-y border-line">
              {ranked.map((r) => (
                <li key={r.player.id} className="flex items-center gap-4 py-[0.9vh] text-[clamp(1rem,1.3vw,1.6rem)]">
                  <span className="w-[2ch] text-right font-display font-semibold text-muted tabular-nums">{r.rank}</span>
                  <span className="min-w-0 flex-1 truncate">{r.player.realName}</span>
                  <Rating value={r.player.rating} provisional={r.player.provisional} className="font-display font-semibold tabular-nums" />
                </li>
              ))}
            </ol>
          </section>

          <section aria-labelledby="tv-recent">
            <h2 id="tv-recent" className="font-display text-[clamp(1.25rem,1.8vw,2.25rem)] font-semibold">
              {tv.recent}
            </h2>
            {recent.data && recent.data.content.length === 0 ? (
              <p className="mt-[1.5vh] text-[clamp(1rem,1.3vw,1.6rem)] text-muted">{tv.noRecent}</p>
            ) : (
              <ul className="mt-[1.5vh] flex flex-col gap-[1.2vh] text-[clamp(1rem,1.3vw,1.6rem)]">
                {(recent.data?.content ?? []).map((g) => (
                  <RecentResult key={g.id} game={g} />
                ))}
              </ul>
            )}
          </section>
        </aside>
      </main>
    </div>
  )
}

/** `size`: how big the board may get; it shrinks to fit when there are more games. */
function TvGame({ game, size }: { game: LiveGame; size: string }) {
  const { t } = useI18n()
  const sans = parseMoves(game.moves ?? '').sans
  const state = finalState(sans)
  const toMove = state.kind === 'ongoing' ? t.tv.toMove(state.turn === 'w' ? t.common.white : t.common.black) : null
  const name = (p: LiveGame['white']) => (
    <span className="flex min-w-0 items-center gap-3">
      <Avatar name={p.realName} seed={p.username} size="sm" />
      <span className="truncate font-medium">{p.realName}</span>
    </span>
  )
  return (
    <li className="flex min-h-0 flex-col gap-[1vh] text-[clamp(1rem,1.4vw,1.75rem)]">
      {name(game.black)}
      <div className={cn('aspect-square w-full overflow-hidden rounded-control', size)}>
        <Suspense fallback={<div className="size-full bg-raised" />}>
          <LiveBoard id={`tv-${game.id}`} moves={game.moves} />
        </Suspense>
      </div>
      {name(game.white)}
      <p className="text-muted">
        {t.tv.moves(Math.ceil(sans.length / 2))}
        {toMove && `, ${toMove.toLowerCase()}`}
      </p>
    </li>
  )
}

function RecentResult({ game }: { game: Game }) {
  const tv = useI18n().t.tv
  const { white, black, result } = game
  const text =
    result === 'DRAW'
      ? tv.drew(white.realName, black.realName)
      : result === 'WHITE_WIN'
        ? tv.beat(white.realName, black.realName)
        : tv.beat(black.realName, white.realName)
  return <li className="truncate">{text}</li>
}

function Clock() {
  const { langCode } = useI18n().t
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 15_000)
    return () => window.clearInterval(timer)
  }, [])
  return (
    <time dateTime={now.toISOString()} className="font-display text-[clamp(1.25rem,2vw,2.5rem)] font-semibold tabular-nums">
      {now.toLocaleTimeString(langCode === 'nb' ? 'nb-NO' : 'en-GB', { hour: '2-digit', minute: '2-digit' })}
    </time>
  )
}

function TvControls({ fullscreenLabel, exitLabel }: { fullscreenLabel: string; exitLabel: string }) {
  const canFullscreen = typeof document !== 'undefined' && document.fullscreenEnabled
  const button = 'rounded-control p-2 text-muted transition-colors hover:bg-raised hover:text-ink'
  return (
    <div className="flex items-center gap-1">
      {canFullscreen && (
        <button
          type="button"
          className={button}
          aria-label={fullscreenLabel}
          title={fullscreenLabel}
          onClick={() => void document.documentElement.requestFullscreen().catch(() => {})}
        >
          <Maximize aria-hidden className="size-5" />
        </button>
      )}
      <Link
        to="/live"
        className={button}
        aria-label={exitLabel}
        title={exitLabel}
        onClick={() => document.fullscreenElement && void document.exitFullscreen().catch(() => {})}
      >
        <X aria-hidden className="size-5" />
      </Link>
    </div>
  )
}
