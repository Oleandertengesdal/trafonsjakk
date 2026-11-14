import { lazy, Suspense, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ArrowLeftRight, ListOrdered, Radio, Trophy } from 'lucide-react'
import { ApiError } from '../api/client'
import { gamesApi, tournamentsApi, usersApi } from '../api/endpoints'
import { invalidateResults } from '../api/queryClient'
import type { GameResult, PlayerRef } from '../api/types'
import { useAuth } from '../auth/AuthProvider'
import { PlayerSelect } from '../components/chess/PlayerSelect'
import { PageHeader } from '../components/layout/PageHeader'
import { Button } from '../components/ui/Button'
import { Field, Input, Textarea } from '../components/ui/Field'
import { Panel } from '../components/ui/Panel'
import { Segmented } from '../components/ui/Segmented'
import { Skeleton } from '../components/ui/Skeleton'
import { useToast } from '../components/ui/Toast'
import { notation, toLocalInput } from '../i18n/format'
import { useI18n } from '../i18n/I18nProvider'
import { roundName } from '../i18n/rounds'
import { useErrorText, useFieldErrors } from '../i18n/useErrorText'
import { cn } from '../lib/cn'
import { toResult, type Outcome } from '../lib/gameResult'

// The board and chess.js load only if someone records the moves
const MoveRecorder = lazy(() => import('../components/chess/MoveRecorder'))

type Colour = 'WHITE' | 'BLACK'
/** Members can date a game this far back (the server's app.games.max-backdate); admins have no limit. */
const MEMBER_BACKDATE_DAYS = 30

function earliestForMembers(): string {
  const d = new Date()
  d.setDate(d.getDate() - MEMBER_BACKDATE_DAYS)
  return toLocalInput(d)
}

type Errors = Partial<Record<'players' | 'playedAt' | 'eco' | 'opening' | 'notes' | 'moves', string>>

export function LogGamePage() {
  const { me } = useAuth()
  const { t } = useI18n()
  const errorText = useErrorText()
  const fieldErrors = useFieldErrors()
  const navigate = useNavigate()
  const toast = useToast()
  const [params] = useSearchParams()
  const matchParam = params.get('match')
  const matchId = matchParam && /^\d+$/.test(matchParam) ? Number(matchParam) : null

  const players = useQuery({ queryKey: ['users'], queryFn: usersApi.list })
  const myMatches = useQuery({ queryKey: ['myMatches'], queryFn: tournamentsApi.myMatches })
  const match = matchId === null ? null : (myMatches.data?.find((m) => m.matchId === matchId) ?? null)

  const isAdmin = me!.role === 'ADMIN'
  const [forOthers, setForOthers] = useState(false)
  const [opponentId, setOpponentId] = useState<number | ''>('')
  const [colour, setColour] = useState<Colour>('WHITE')
  const [outcome, setOutcome] = useState<Outcome>('WIN')
  const [whiteId, setWhiteId] = useState<number | ''>('')
  const [blackId, setBlackId] = useState<number | ''>('')
  const [result, setResult] = useState<GameResult>('WHITE_WIN')
  const [playedAt, setPlayedAt] = useState(() => toLocalInput(new Date()))
  const [opening, setOpening] = useState('')
  const [eco, setEco] = useState('')
  const [notes, setNotes] = useState('')
  const [moves, setMoves] = useState<string | null>(null)
  const [recordMoves, setRecordMoves] = useState(false)
  const [swapped, setSwapped] = useState(false)
  const [errors, setErrors] = useState<Errors>({})
  const l = t.logGame

  // Tournament game: the players are fixed; colours can be swapped if you played the other way round.
  const matchPair = useMemo(() => {
    if (!match) return null
    return swapped ? { white: match.black, black: match.white } : { white: match.white, black: match.black }
  }, [match, swapped])

  const derived = useMemo(() => {
    if (matchPair) {
      const iAmWhite = matchPair.white.id === me!.id
      const iAmPlaying = iAmWhite || matchPair.black.id === me!.id
      // An admin may log a tournament game they didn't play in: then the outcome is from White's view.
      return { white: matchPair.white.id, black: matchPair.black.id, result: toResult(outcome, iAmPlaying ? iAmWhite : true) }
    }
    if (forOthers) return { white: whiteId, black: blackId, result }
    return colour === 'WHITE'
      ? { white: me!.id, black: opponentId, result: toResult(outcome, true) }
      : { white: opponentId, black: me!.id, result: toResult(outcome, false) }
  }, [matchPair, forOthers, whiteId, blackId, result, colour, opponentId, outcome, me])

  const create = useMutation({
    mutationFn: gamesApi.create,
    onSuccess: async (g) => {
      await invalidateResults()
      const involved = g.white.id === me!.id || g.black.id === me!.id
      const other = g.white.id === me!.id ? g.black : g.white
      toast(involved ? l.loggedWaiting(other.realName.split(' ')[0]) : l.loggedWaitingOthers)
      navigate(`/games/${g.id}`)
    },
    onError: (e) => {
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length) setErrors(fieldErrors(e))
      else toast(errorText(e), 'error')
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    const local: Errors = {}
    if (!derived.white || !derived.black) local.players = forOthers ? l.errBothPlayers : l.errOpponent
    else if (derived.white === derived.black) local.players = l.errSamePlayer
    const when = playedAt ? new Date(playedAt) : null
    if (!when || Number.isNaN(when.getTime())) local.playedAt = l.errWhen
    else if (when.getTime() > Date.now() + 10 * 60_000) local.playedAt = l.errFuture
    if (eco && !/^[A-Ea-e]\d{2}$/.test(eco)) local.eco = l.errEco
    setErrors(local)
    if (Object.keys(local).length || !when) return
    create.mutate({
      whiteId: Number(derived.white),
      blackId: Number(derived.black),
      result: derived.result,
      playedAt: when.toISOString(),
      opening: opening.trim() || undefined,
      eco: eco.trim() || undefined,
      notes: notes.trim() || undefined,
      moves: moves ?? undefined,
      matchId: match?.matchId,
    })
  }

  const loading = players.isPending || (matchId !== null && myMatches.isPending)
  const showAbsoluteResult = forOthers && !matchPair
  const adminLoggingOthersMatch = !!matchPair && matchPair.white.id !== me!.id && matchPair.black.id !== me!.id

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader title={l.title} description={l.description} />

      {matchId === null && (
        <p className="mb-5 flex flex-wrap items-center gap-x-2 gap-y-1 text-[0.9375rem] text-muted">
          <Radio aria-hidden className="size-4 text-accent" />
          {l.liveHint}
          <Link to="/live" className="font-medium text-accent underline-offset-4 hover:underline">
            {l.liveLink}
          </Link>
        </p>
      )}

      {matchId !== null && !myMatches.isPending && !match && (
        <p role="alert" className="mb-5 rounded-control border border-line bg-surface px-4 py-3 text-[0.9375rem] text-muted">
          {l.matchUnavailable}
        </p>
      )}

      <form onSubmit={submit} noValidate>
        <Panel className="flex flex-col gap-6 p-5 sm:p-6">
          {loading ? (
            <div className="flex flex-col gap-4" role="status" aria-label={t.common.loading}>
              <Skeleton className="h-11" />
              <Skeleton className="h-14" />
              <Skeleton className="h-14" />
            </div>
          ) : matchPair ? (
            <div className="flex flex-col gap-4">
              <div className="flex items-center gap-2 text-[0.9375rem] text-accent">
                <Trophy aria-hidden className="size-4.5" />
                <Link to={`/tournaments/${match!.tournamentId}`} className="hover:underline">
                  {match!.tournamentName}, {roundName(t, match!.format, match!.round, match!.totalRounds)}
                </Link>
              </div>
              <div className="flex flex-wrap items-center gap-3 rounded-control border border-line bg-page px-4 py-3">
                <ColourChip colour="WHITE" player={matchPair.white} label={t.common.white} />
                <span className="text-faint">{t.common.vs}</span>
                <ColourChip colour="BLACK" player={matchPair.black} label={t.common.black} />
                <Button type="button" variant="ghost" size="sm" className="ml-auto" onClick={() => setSwapped((v) => !v)}>
                  <ArrowLeftRight aria-hidden className="size-4" />
                  {l.swapColours}
                </Button>
              </div>
              {errors.players && <p className="text-[0.8125rem] text-accent">{errors.players}</p>}
            </div>
          ) : forOthers ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label={t.common.white} error={errors.players}>
                {(p) => <PlayerSelect {...p} players={players.data ?? []} value={whiteId} onChange={setWhiteId} exclude={blackId} placeholder={l.choosePlayer} />}
              </Field>
              <Field label={t.common.black}>
                {(p) => <PlayerSelect {...p} players={players.data ?? []} value={blackId} onChange={setBlackId} exclude={whiteId} placeholder={l.choosePlayer} />}
              </Field>
            </div>
          ) : (
            <>
              <Field label={l.opponent} error={errors.players}>
                {(p) => <PlayerSelect {...p} players={players.data ?? []} value={opponentId} onChange={setOpponentId} exclude={me!.id} placeholder={l.choosePlayer} />}
              </Field>
              <Segmented<Colour>
                label={l.youPlayed}
                value={colour}
                onChange={setColour}
                options={[
                  { value: 'WHITE', label: <ColourLabel colour="WHITE" label={t.common.white} /> },
                  { value: 'BLACK', label: <ColourLabel colour="BLACK" label={t.common.black} /> },
                ]}
              />
            </>
          )}

          {showAbsoluteResult || adminLoggingOthersMatch ? (
            <Segmented<GameResult>
              label={l.result}
              value={adminLoggingOthersMatch ? derived.result : result}
              onChange={(v) => (adminLoggingOthersMatch ? setOutcome(v === 'DRAW' ? 'DRAW' : v === 'WHITE_WIN' ? 'WIN' : 'LOSS') : setResult(v))}
              options={(['WHITE_WIN', 'DRAW', 'BLACK_WIN'] as const).map((r) => ({ value: r, label: notation[r], hint: t.result[r] }))}
            />
          ) : (
            <Segmented<Outcome>
              label={l.result}
              value={outcome}
              onChange={setOutcome}
              options={[
                { value: 'WIN', label: l.iWon },
                { value: 'DRAW', label: l.draw },
                { value: 'LOSS', label: l.iLost },
              ]}
            />
          )}

          <Field label={l.when} error={errors.playedAt}>
            {(p) => <Input {...p} type="datetime-local" value={playedAt} min={isAdmin ? undefined : earliestForMembers()} max={toLocalInput(new Date())} onChange={(e) => setPlayedAt(e.target.value)} />}
          </Field>

          <details className="rounded-control border border-line [&_summary::-webkit-details-marker]:hidden" open={!!(errors.eco || errors.opening || errors.notes)}>
            <summary className="cursor-pointer list-none px-4 py-3 text-[0.9375rem] font-medium text-muted hover:text-ink">
              {l.details} <span className="text-faint">({t.common.optional})</span>
            </summary>
            <div className="flex flex-col gap-4 border-t border-line p-4">
              <div className="grid gap-4 sm:grid-cols-[1fr_7rem]">
                <Field label={l.opening} error={errors.opening}>
                  {(p) => <Input {...p} value={opening} maxLength={80} onChange={(e) => setOpening(e.target.value)} placeholder={l.openingPlaceholder} />}
                </Field>
                <Field label={l.eco} error={errors.eco}>
                  {(p) => <Input {...p} value={eco} maxLength={3} onChange={(e) => setEco(e.target.value.toUpperCase())} placeholder="C50" spellCheck={false} />}
                </Field>
              </div>
              <Field label={l.notes} error={errors.notes}>
                {(p) => <Textarea {...p} value={notes} maxLength={1000} onChange={(e) => setNotes(e.target.value)} placeholder={l.notesPlaceholder} />}
              </Field>
            </div>
          </details>

          {recordMoves ? (
            <div className="rounded-control border border-line p-4">
              <Suspense fallback={<p className="text-sm text-muted">{t.moves.loading}</p>}>
                <MoveRecorder
                  value={moves}
                  onChange={setMoves}
                  orientation={derived.black === me!.id ? 'black' : 'white'}
                  result={derived.result}
                />
              </Suspense>
              {errors.moves && <p className="mt-2 text-[0.8125rem] text-accent">{errors.moves}</p>}
            </div>
          ) : (
            <Button type="button" variant="secondary" className="self-start" onClick={() => setRecordMoves(true)}>
              <ListOrdered aria-hidden className="size-4.5" />
              {t.moves.add} <span className="font-normal text-faint">({t.common.optional})</span>
            </Button>
          )}

          <div className="flex flex-col-reverse gap-3 sm:flex-row sm:items-center sm:justify-between">
            {isAdmin && !matchPair ? (
              <label className="flex items-center gap-2 text-[0.9375rem] text-muted">
                <input type="checkbox" checked={forOthers} onChange={(e) => setForOthers(e.target.checked)} className="size-4 accent-[var(--c-brand)]" />
                {l.forOthers}
              </label>
            ) : (
              <span />
            )}
            <Button type="submit" size="lg" loading={create.isPending} disabled={loading}>
              {l.submit}
            </Button>
          </div>
        </Panel>
      </form>
    </div>
  )
}

function ColourSquare({ colour }: { colour: Colour }) {
  return <span aria-hidden className={cn('size-3 shrink-0 rounded-[3px] border border-faint', colour === 'WHITE' ? 'bg-trim' : 'bg-roof')} />
}

function ColourLabel({ colour, label }: { colour: Colour; label: string }) {
  return (
    <span className="inline-flex items-center gap-2">
      <ColourSquare colour={colour} />
      {label}
    </span>
  )
}

function ColourChip({ colour, player, label }: { colour: Colour; player: PlayerRef; label: string }) {
  return (
    <span className="inline-flex min-w-0 items-center gap-2" title={label}>
      <ColourSquare colour={colour} />
      <span className="sr-only">{label}:</span>
      <span className="truncate font-medium">{player.realName}</span>
    </span>
  )
}
