import { lazy, Suspense, useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, CloudOff, Loader2, Sun } from 'lucide-react'
import { ApiError, isNotFound } from '../api/client'
import { liveApi } from '../api/endpoints'
import { invalidateResults } from '../api/queryClient'
import type { GameResult, LiveGame } from '../api/types'
import { useAuth } from '../auth/AuthProvider'
import { formatMoves, parseMoves } from '../chess/moves'
import { PageHeader } from '../components/layout/PageHeader'
import { Button, ButtonLink } from '../components/ui/Button'
import { Dialog } from '../components/ui/Dialog'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorNotice } from '../components/ui/ErrorNotice'
import { Panel } from '../components/ui/Panel'
import { Skeleton } from '../components/ui/Skeleton'
import { useToast } from '../components/ui/Toast'
import { LiveDot } from '../components/chess/Live'
import { useWakeLock } from '../hooks/useWakeLock'
import { useI18n } from '../i18n/I18nProvider'
import { useErrorText } from '../i18n/useErrorText'
import { NotFoundPage } from './NotFoundPage'

const MoveRecorder = lazy(() => import('../components/chess/MoveRecorder'))
const LiveBoard = lazy(() => import('../components/chess/LiveBoard'))

const boardFallback = <Skeleton className="aspect-square w-full" />

/**
 * One live game. The players (and admins) record it: each move is saved straight away, and the result is
 * logged at the end. Everyone else follows the board, which updates every few seconds.
 */
export function LiveGamePage() {
  const id = Number(useParams().id)
  const { t } = useI18n()
  const q = useQuery({
    queryKey: ['live', id],
    queryFn: () => liveApi.get(id),
    // Recorders poll for themselves (see Recorder), so their own saves and others' never race here
    refetchInterval: (query) => (query.state.data?.status === 'LIVE' && !query.state.data.canRecord ? 3_000 : false),
  })
  useWakeLock(q.data?.status === 'LIVE')

  if (q.isPending) return <Skeleton className="h-[32rem] w-full rounded-panel" />
  if (q.isError && isNotFound(q.error)) return <NotFoundPage what="live" />
  if (q.isError)
    return (
      <Panel>
        <ErrorNotice error={q.error} onRetry={() => void q.refetch()} />
      </Panel>
    )
  const g = q.data
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={
          <>
            {g.white.realName} <span className="font-normal text-muted">{t.live.vs}</span> {g.black.realName}
          </>
        }
        action={g.status === 'LIVE' ? <LiveDot label={t.live.liveBadge} /> : undefined}
      />
      {g.status !== 'LIVE' ? <Ended game={g} /> : g.canRecord ? <Recorder game={g} /> : <Follow game={g} />}
    </div>
  )
}

function Ended({ game }: { game: LiveGame }) {
  const l = useI18n().t.live
  const finished = game.status === 'FINISHED'
  return (
    <Panel>
      <div className="p-5">
        <EmptyState
          title={finished ? l.endedTitle : l.stoppedTitle}
          action={
            finished && game.gameId ? (
              <ButtonLink to={`/games/${game.gameId}`}>{l.openGame}</ButtonLink>
            ) : (
              <ButtonLink to="/live" variant="secondary">
                {l.backToLive}
              </ButtonLink>
            )
          }
        >
          {finished ? l.endedBody : l.stoppedBody}
        </EmptyState>
      </div>
    </Panel>
  )
}

function MoveList({ moves }: { moves: string | null }) {
  const sans = parseMoves(moves ?? '').sans
  const l = useI18n().t.live
  return (
    <p className="text-[0.9375rem] leading-relaxed text-muted">
      {sans.length === 0 ? l.moveCount(0) : formatMoves(sans)}
    </p>
  )
}

function Follow({ game }: { game: LiveGame }) {
  const l = useI18n().t.live
  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,34rem)_1fr]">
      <div className="overflow-hidden rounded-control border border-line">
        <Suspense fallback={boardFallback}>
          <LiveBoard id="live-follow" moves={game.moves} />
        </Suspense>
      </div>
      <Panel className="self-start" title={l.moveCount(Math.ceil(parseMoves(game.moves ?? '').sans.length / 2))}>
        <div className="flex flex-col gap-3 px-5 pt-1 pb-5">
          <MoveList moves={game.moves} />
          <p className="text-sm text-faint">{l.following}</p>
        </div>
      </Panel>
    </div>
  )
}

type SaveState = 'idle' | 'saving' | 'saved' | 'failed'

function Recorder({ game }: { game: LiveGame }) {
  const { t } = useI18n()
  const l = t.live
  const { me } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const errorText = useErrorText()
  const navigate = useNavigate()

  // What the recorder shows, what the server has, and the version that goes with it
  const latest = useRef(game.moves)
  const saved = useRef(game.moves)
  const version = useRef(game.version)
  const busy = useRef(false)
  const [saveState, setSaveState] = useState<SaveState>('idle')
  const [shown, setShown] = useState({ moves: game.moves, key: 0 })
  const [confirm, setConfirm] = useState<GameResult | 'ABANDON' | null>(null)
  const [ending, setEnding] = useState(false)

  const publish = useCallback((g: LiveGame) => qc.setQueryData(['live', g.id], g), [qc])

  /** Shows moves saved somewhere else (the other player's phone), replacing ours. */
  const adopt = useCallback(
    (fresh: LiveGame) => {
      version.current = fresh.version
      latest.current = fresh.moves
      saved.current = fresh.moves
      setShown((s) => ({ moves: fresh.moves, key: s.key + 1 }))
      publish(fresh)
    },
    [publish],
  )

  /** Saves until the server has what the board shows. One request at a time, newest moves win. */
  const flush = useCallback(async () => {
    if (busy.current) return
    busy.current = true
    try {
      while (latest.current !== saved.current) {
        const sending = latest.current
        setSaveState('saving')
        const res = await liveApi.saveMoves(game.id, sending, version.current)
        version.current = res.version
        saved.current = sending
        publish(res)
      }
      setSaveState('saved')
    } catch (e) {
      if (e instanceof ApiError && e.code === 'live.out_of_date') {
        adopt(await liveApi.get(game.id))
        setSaveState('saved')
        toast(l.updatedElsewhere)
      } else if (e instanceof ApiError && e.code === 'live.not_running') {
        void qc.invalidateQueries({ queryKey: ['live', game.id] })
      } else {
        setSaveState('failed')
      }
    } finally {
      busy.current = false
    }
  }, [adopt, game.id, l.updatedElsewhere, publish, qc, toast])

  // Pick up moves recorded on the other phone, and notice if the game was ended elsewhere
  useEffect(() => {
    const timer = window.setInterval(async () => {
      if (busy.current || latest.current !== saved.current || document.visibilityState !== 'visible') return
      try {
        const fresh = await liveApi.get(game.id)
        if (fresh.status !== 'LIVE') publish(fresh)
        else if (fresh.version > version.current && !busy.current && latest.current === saved.current) adopt(fresh)
      } catch {
        /* try again on the next tick */
      }
    }, 4_000)
    return () => window.clearInterval(timer)
  }, [adopt, game.id, publish])

  async function end(choice: GameResult | 'ABANDON') {
    setEnding(true)
    try {
      await flush()
      if (choice === 'ABANDON') {
        await liveApi.abandon(game.id)
        toast(l.abandoned)
        navigate('/live')
      } else {
        const logged = await liveApi.finish(game.id, choice)
        await invalidateResults()
        const opponent = me!.id === game.white.id ? game.black : me!.id === game.black.id ? game.white : null
        toast(opponent ? l.finished(opponent.realName) : l.finishedByAdmin)
        navigate(`/games/${logged.id}`)
      }
    } catch (e) {
      toast(errorText(e), 'error')
      setConfirm(null)
    } finally {
      setEnding(false)
    }
  }

  const resultLabel = (r: GameResult) => t.result[r]
  const status =
    saveState === 'saving' ? (
      <>
        <Loader2 aria-hidden className="size-4 motion-safe:animate-spin" /> {l.saving}
      </>
    ) : saveState === 'saved' ? (
      <>
        <Check aria-hidden className="size-4 text-win" /> {l.saved}
      </>
    ) : saveState === 'failed' ? (
      <>
        <CloudOff aria-hidden className="size-4 text-accent" /> {l.saveFailed}
      </>
    ) : null

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,34rem)_1fr]">
      <Panel>
        <div className="flex flex-col gap-3 p-4 sm:p-5">
          <p className="text-[0.9375rem] text-muted">{l.youRecord}</p>
          <Suspense fallback={boardFallback}>
            <MoveRecorder
              key={shown.key}
              value={shown.moves}
              orientation={me!.id === game.black.id ? 'black' : 'white'}
              onChange={(moves) => {
                latest.current = moves
                void flush()
              }}
            />
          </Suspense>
          <p aria-live="polite" className="flex min-h-5 items-center gap-1.5 text-sm text-muted">
            {status}
          </p>
        </div>
      </Panel>

      <div className="flex flex-col gap-6">
        <Panel title={l.finishTitle}>
          <div className="flex flex-col gap-4 px-5 pt-1 pb-5">
            <p className="text-[0.9375rem] text-muted">{l.finishHint}</p>
            <div className="grid grid-cols-3 gap-2">
              {(['WHITE_WIN', 'DRAW', 'BLACK_WIN'] as const).map((r) => (
                <Button key={r} variant="secondary" size="lg" className="h-auto min-h-12 flex-col gap-0.5 py-2" onClick={() => setConfirm(r)}>
                  <span className="font-display text-lg font-semibold tabular-nums">{r === 'WHITE_WIN' ? '1–0' : r === 'DRAW' ? '½–½' : '0–1'}</span>
                  <span className="text-[0.8125rem] font-normal text-muted">{resultLabel(r)}</span>
                </Button>
              ))}
            </div>
            <div>
              <Button variant="ghost" size="sm" onClick={() => setConfirm('ABANDON')}>
                {l.abandon}
              </Button>
            </div>
          </div>
        </Panel>
        <p className="flex items-center gap-2 text-sm text-faint">
          <Sun aria-hidden className="size-4" />
          {l.awake}
        </p>
      </div>

      <Dialog open={confirm !== null} onClose={() => !ending && setConfirm(null)} title={confirm === 'ABANDON' ? l.abandonConfirm : l.finishTitle}>
        {confirm && confirm !== 'ABANDON' && <p className="text-[0.9375rem]">{l.finishConfirm(resultLabel(confirm))}</p>}
        <div className="mt-6 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setConfirm(null)} disabled={ending}>
            {l.cancel}
          </Button>
          <Button variant={confirm === 'ABANDON' ? 'danger' : 'primary'} loading={ending} onClick={() => confirm && void end(confirm)}>
            {confirm === 'ABANDON' ? l.abandon : l.finish}
          </Button>
        </div>
      </Dialog>
    </div>
  )
}
