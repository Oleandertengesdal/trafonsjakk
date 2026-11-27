import { lazy, Suspense, useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Flag, Handshake, Loader2, RotateCcw, X } from 'lucide-react'
import { ApiError, isNotFound } from '../api/client'
import { onlineApi } from '../api/endpoints'
import { invalidateResults } from '../api/queryClient'
import type { Colour, OnlineGame, PlayerRef } from '../api/types'
import { Avatar } from '../components/chess/Avatar'
import { Rating } from '../components/chess/PlayerLink'
import { RatingDelta } from '../components/chess/ResultMark'
import { PageHeader } from '../components/layout/PageHeader'
import { Button, ButtonLink } from '../components/ui/Button'
import { Dialog } from '../components/ui/Dialog'
import { ErrorNotice } from '../components/ui/ErrorNotice'
import { Panel } from '../components/ui/Panel'
import { Skeleton } from '../components/ui/Skeleton'
import { useToast } from '../components/ui/Toast'
import { useWakeLock } from '../hooks/useWakeLock'
import { useI18n } from '../i18n/I18nProvider'
import { useErrorText } from '../i18n/useErrorText'
import { cn } from '../lib/cn'
import { formatClock, isLow, remainingMs } from '../lib/onlineClock'
import { NotFoundPage } from './NotFoundPage'

const PlayBoard = lazy(() => import('../components/chess/PlayBoard'))

/** A game as fetched, with when the answer arrived: the page counts the clock down from there. */
type Received = OnlineGame & { receivedAt: number }

const stamp = (g: OnlineGame): Received => ({ ...g, receivedAt: Date.now() })

/** How often to ask the server: often while a clock game is on (moves show up quickly), rarely for daily games. */
function pollEvery(g: OnlineGame | undefined): number | false {
  if (!g || g.status !== 'ACTIVE') return false
  if (g.timeControl.kind === 'DAILY') return 15_000
  return g.myColour ? 1_000 : 2_000
}

/** The time, updated every {@code ms} while {@code on} (for the clocks). */
function useNow(on: boolean, ms: number) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!on) return
    const timer = window.setInterval(() => setNow(Date.now()), ms)
    return () => window.clearInterval(timer)
  }, [on, ms])
  return now
}

/**
 * An online game. The players move on the board (the server checks each move and keeps the clock);
 * anyone else watches. Nobody gets an engine while it's being played.
 */
export function OnlineGamePage() {
  const id = Number(useParams().id)
  const qc = useQueryClient()
  const q = useQuery({
    queryKey: ['online', id],
    queryFn: async () => {
      const fresh = stamp(await onlineApi.get(id))
      // A slow poll mustn't undo a newer answer we already have (our own move, say)
      const cached = qc.getQueryData<Received>(['online', id])
      return cached && cached.version > fresh.version ? cached : fresh
    },
    refetchInterval: (query) => pollEvery(query.state.data),
  })
  const g = q.data
  useWakeLock(!!g && g.status === 'ACTIVE' && g.myColour !== null && g.timeControl.kind === 'LIVE')

  if (q.isPending) return <Skeleton className="h-[32rem] w-full rounded-panel" />
  if (q.isError && isNotFound(q.error)) return <NotFoundPage what="online" />
  if (q.isError || !g)
    return (
      <Panel>
        <ErrorNotice error={q.error} onRetry={() => void q.refetch()} />
      </Panel>
    )
  return <Game game={g} offline={q.isRefetchError} />
}

function Game({ game: g, offline }: { game: Received; offline: boolean }) {
  const { t } = useI18n()
  const p = t.play
  const qc = useQueryClient()
  const toast = useToast()
  const errorText = useErrorText()
  const navigate = useNavigate()
  const [pending, setPending] = useState<{ fen: string; uci: string; ply: number } | null>(null)
  const [busy, setBusy] = useState(false)
  const [confirmResign, setConfirmResign] = useState(false)

  const active = g.status === 'ACTIVE'
  const live = g.timeControl.kind === 'LIVE'
  const mine = g.myColour
  const opponentColour: Colour = mine === 'BLACK' ? 'WHITE' : 'BLACK'
  const bottom: Colour = mine ?? 'WHITE'
  const top: Colour = bottom === 'WHITE' ? 'BLACK' : 'WHITE'
  const player = (c: Colour): PlayerRef => (c === 'WHITE' ? g.white : g.black)
  const myTurn = active && mine !== null && g.turn === mine && pending === null
  const now = useNow(active && live && g.clock.running, 100)

  // An answer that includes our move clears the move we showed early
  const shownPending = pending && g.plies <= pending.ply && active ? pending : null

  // When the position last changed, by this phone's clock: the server is told how long we really thought
  const turnShownAt = useRef(0)
  useEffect(() => {
    turnShownAt.current = Date.now()
  }, [g.plies])

  const publish = useCallback((res: OnlineGame) => qc.setQueryData(['online', g.id], stamp(res)), [qc, g.id])

  const onEnded = useCallback(
    (res: OnlineGame) => {
      if (res.status !== 'ACTIVE' && res.gameId) void invalidateResults()
    },
    [],
  )

  async function onMove(uci: string, fenAfter: string) {
    const ply = g.plies
    setPending({ fen: fenAfter, uci, ply })
    try {
      const res = await onlineApi.move(g.id, uci, ply, Math.max(0, Date.now() - turnShownAt.current))
      publish(res)
      onEnded(res)
    } catch (e) {
      // Out of date, too late, or the connection dropped: show what the server has
      if (!(e instanceof ApiError && e.code === 'online.game_over')) toast(errorText(e), 'error')
      void qc.invalidateQueries({ queryKey: ['online', g.id] })
    } finally {
      setPending(null)
    }
  }

  async function act(call: () => Promise<OnlineGame>) {
    setBusy(true)
    try {
      const res = await call()
      publish(res)
      onEnded(res)
    } catch (e) {
      toast(errorText(e), 'error')
      void qc.invalidateQueries({ queryKey: ['online', g.id] })
    } finally {
      setBusy(false)
      setConfirmResign(false)
    }
  }

  async function rematch() {
    setBusy(true)
    try {
      await onlineApi.rematch(g.id)
      toast(p.rematchSent)
      void qc.invalidateQueries({ queryKey: ['online', 'lobby'] })
      navigate('/play')
    } catch (e) {
      toast(errorText(e), 'error')
    } finally {
      setBusy(false)
    }
  }

  const clockMs = (c: Colour) => remainingMs(g.clock, c, g.receivedAt, now)
  const drawFromOpponent = mine !== null && g.drawOfferBy === player(opponentColour).id
  const drawFromMe = mine !== null && g.drawOfferBy === player(mine).id

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={
          <>
            {g.white.realName} <span className="font-normal text-muted">{p.vs}</span> {g.black.realName}
          </>
        }
        description={`${g.timeControl.kind === 'DAILY' ? p.dailySummary(g.timeControl.daysPerMove, g.rated) : p.challengeSummary(g.timeControl.label, g.rated)}`}
      />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,36rem)_1fr]">
        <div className="flex flex-col gap-2">
          <PlayerBar player={player(top)} colour={top} ms={clockMs(top)} game={g} />
          <Suspense fallback={<Skeleton className="aspect-square w-full" />}>
            <PlayBoard
              fen={shownPending?.fen ?? g.fen}
              orientation={bottom}
              movable={myTurn ? mine : null}
              lastMove={shownPending?.uci ?? g.lastMove}
              onMove={(uci, fen) => void onMove(uci, fen)}
            />
          </Suspense>
          <PlayerBar player={player(bottom)} colour={bottom} ms={clockMs(bottom)} game={g} />
          {offline && active && (
            <p role="status" className="flex items-center gap-2 text-sm text-muted">
              <Loader2 aria-hidden className="size-4 motion-safe:animate-spin" /> {p.connection}
            </p>
          )}
        </div>

        <div className="flex flex-col gap-6">
          <Panel>
            <div className="flex flex-col gap-4 p-5">
              {active ? (
                <Status game={g} now={now} myTurn={myTurn} waiting={shownPending !== null} />
              ) : (
                <Result game={g} />
              )}

              {active && drawFromOpponent && (
                <div className="flex flex-wrap items-center gap-3 rounded-control bg-accent-soft px-3 py-2.5">
                  <span className="text-[0.9375rem]">{p.drawOfferedToYou(player(opponentColour).realName)}</span>
                  <span className="ml-auto flex gap-2">
                    <Button size="sm" onClick={() => void act(() => onlineApi.acceptDraw(g.id))} disabled={busy}>
                      {p.acceptDraw}
                    </Button>
                    <Button size="sm" variant="secondary" onClick={() => void act(() => onlineApi.declineDraw(g.id))} disabled={busy}>
                      {p.declineDraw}
                    </Button>
                  </span>
                </div>
              )}
              {active && drawFromMe && <p className="text-sm text-muted">{p.drawOffered}</p>}

              {active && mine !== null && (
                <div className="flex flex-wrap gap-2">
                  {g.canAbort ? (
                    <Button variant="secondary" size="sm" onClick={() => void act(() => onlineApi.abort(g.id))} disabled={busy}>
                      <X aria-hidden className="size-4" />
                      {p.abort}
                    </Button>
                  ) : (
                    <>
                      <Button variant="secondary" size="sm" onClick={() => void act(() => onlineApi.offerDraw(g.id))} disabled={busy || drawFromMe}>
                        <Handshake aria-hidden className="size-4" />
                        {p.offerDraw}
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => setConfirmResign(true)} disabled={busy}>
                        <Flag aria-hidden className="size-4" />
                        {p.resign}
                      </Button>
                    </>
                  )}
                </div>
              )}
              {active && g.canAbort && <p className="text-sm text-faint">{p.abortHint}</p>}

              {!active && (
                <div className="flex flex-wrap gap-2">
                  {mine !== null && (
                    <Button onClick={() => void rematch()} loading={busy}>
                      <RotateCcw aria-hidden className="size-4" />
                      {p.rematch}
                    </Button>
                  )}
                  {g.gameId && (
                    <ButtonLink to={`/games/${g.gameId}`} variant="secondary">
                      {p.openGame}
                    </ButtonLink>
                  )}
                  <ButtonLink to="/play" variant="ghost">
                    {p.back}
                  </ButtonLink>
                </div>
              )}
            </div>
          </Panel>

          <Panel title={t.moves.list}>
            <MoveList moves={g.moves} />
          </Panel>

          <p className="text-sm text-faint">{mine === null ? p.spectating : active ? p.fairPlay : ''}</p>
        </div>
      </div>

      <Dialog open={confirmResign} onClose={() => !busy && setConfirmResign(false)} title={p.resignConfirm}>
        <p className="text-[0.9375rem]">{p.resignBody}</p>
        <div className="mt-6 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setConfirmResign(false)} disabled={busy}>
            {t.live.cancel}
          </Button>
          <Button variant="danger" loading={busy} onClick={() => void act(() => onlineApi.resign(g.id))}>
            {p.resign}
          </Button>
        </div>
      </Dialog>
    </div>
  )
}

function PlayerBar({ player, colour, ms, game }: { player: PlayerRef; colour: Colour; ms: number; game: Received }) {
  const p = useI18n().t.play
  const toMove = game.status === 'ACTIVE' && game.turn === colour
  const live = game.timeControl.kind === 'LIVE'
  const low = live && toMove && isLow(ms, game.timeControl.initialSeconds)
  return (
    <div className={cn('flex items-center gap-3 rounded-control border px-3 py-2', toMove ? 'border-accent/50 bg-surface' : 'border-line bg-raised/50')}>
      <Avatar name={player.realName} seed={player.username} size="sm" />
      <span className="min-w-0 flex-1">
        <span className="block font-medium break-words">{player.realName}</span>
        <span className="text-[0.8125rem] text-muted">
          <Rating value={player.rating} provisional={player.provisional} />
        </span>
      </span>
      {live && (
        <span
          role="timer"
          aria-label={p.clockOf(player.realName)}
          className={cn(
            'rounded-md px-2.5 py-1 font-display text-2xl font-semibold tabular-nums',
            toMove ? 'bg-ink text-page' : 'text-muted',
            low && 'bg-loss text-on-accent',
          )}
        >
          {formatClock(ms)}
        </span>
      )}
    </div>
  )
}

function Status({ game: g, now, myTurn, waiting }: { game: Received; now: number; myTurn: boolean; waiting: boolean }) {
  const { t, f } = useI18n()
  const p = t.play
  const toMove = g.turn === 'WHITE' ? g.white : g.black
  const live = g.timeControl.kind === 'LIVE'
  const firstMoveLeft = live && g.clock.firstMoveBy ? Math.max(0, Math.ceil((Date.parse(g.clock.firstMoveBy) - Date.parse(g.clock.serverTime) - (now - g.receivedAt)) / 1000)) : null
  return (
    <div className="flex flex-col gap-1">
      <p className="font-display text-xl font-semibold" aria-live="polite">
        {waiting ? <Loader2 aria-hidden className="inline size-5 motion-safe:animate-spin" /> : myTurn ? p.yourMove : g.myColour ? p.waitingForMove(toMove.realName) : p.moveTo(toMove.realName)}
      </p>
      {firstMoveLeft !== null && (
        <p className="text-[0.9375rem] text-muted">{myTurn ? p.firstMove(firstMoveLeft) : p.firstMoveOpponent(toMove.realName, firstMoveLeft)}</p>
      )}
      {!live && g.clock.deadline && <p className="text-[0.9375rem] text-muted">{p.moveBy(f.relative(g.clock.deadline))}</p>}
    </div>
  )
}

function Result({ game: g }: { game: Received }) {
  const p = useI18n().t.play
  if (g.status === 'ABORTED' || !g.result) {
    return (
      <div className="flex flex-col gap-1">
        <p className="font-display text-xl font-semibold">{p.aborted}</p>
        <p className="text-[0.9375rem] text-muted">{p.abortedBody}</p>
      </div>
    )
  }
  const headline = g.result === 'DRAW' ? p.outcome.DRAW : g.result === 'WHITE_WIN' ? p.outcome.WHITE_WIN(g.white.realName) : p.outcome.BLACK_WIN(g.black.realName)
  return (
    <div className="flex flex-col gap-2">
      <p className="font-display text-xl font-semibold">
        <span className="mr-2 tabular-nums">{g.result === 'WHITE_WIN' ? '1–0' : g.result === 'BLACK_WIN' ? '0–1' : '½–½'}</span>
        {headline}
      </p>
      {g.endReason && <p className="text-[0.9375rem] text-muted">{p.endReasons[g.endReason]}</p>}
      {g.ratingChange && (
        <ul className="flex flex-col gap-1 text-[0.9375rem]">
          {([['WHITE', g.white, g.ratingChange.white], ['BLACK', g.black, g.ratingChange.black]] as const).map(([c, who, d]) => (
            <li key={c} className="flex items-center gap-2">
              <span className="min-w-0 flex-1 break-words">{who.realName}</span>
              <span className="tabular-nums">{d.after}</span>
              <RatingDelta before={d.before} after={d.after} />
            </li>
          ))}
        </ul>
      )}
      <p className="text-sm text-faint">{g.rated ? p.countsOnline : p.casualBody}</p>
    </div>
  )
}

function MoveList({ moves }: { moves: string | null }) {
  const m = useI18n().t.moves
  const sans = (moves ?? '').split(/\s+/).filter((s) => s !== '' && !/^\d+\.+$/.test(s))
  if (sans.length === 0) return <p className="px-5 pt-1 pb-5 text-sm text-muted">{m.none}</p>
  const rows: { n: number; white: string; black: string | null }[] = []
  for (let i = 0; i < sans.length; i += 2) rows.push({ n: i / 2 + 1, white: sans[i], black: sans[i + 1] ?? null })
  return (
    <ol className="grid max-h-72 grid-cols-[auto_1fr_1fr] content-start gap-x-3 gap-y-0.5 overflow-y-auto px-5 pt-1 pb-5 font-mono text-sm tabular-nums">
      {rows.map((r) => (
        <li key={r.n} className="col-span-3 grid grid-cols-subgrid">
          <span className="text-faint">{r.n}.</span>
          <span>{r.white}</span>
          <span>{r.black ?? ''}</span>
        </li>
      ))}
    </ol>
  )
}
