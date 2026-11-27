import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Clock, Swords } from 'lucide-react'
import { onlineApi, usersApi } from '../api/endpoints'
import type { Challenge, ChallengeColour, ClockKind, Lobby, OnlineGame } from '../api/types'
import { useAuth } from '../auth/AuthProvider'
import { Avatar } from '../components/chess/Avatar'
import { PlayerSelect } from '../components/chess/PlayerSelect'
import { Rating } from '../components/chess/PlayerLink'
import { PageHeader } from '../components/layout/PageHeader'
import { Button } from '../components/ui/Button'
import { ErrorNotice } from '../components/ui/ErrorNotice'
import { Panel } from '../components/ui/Panel'
import { Segmented } from '../components/ui/Segmented'
import { SkeletonRows } from '../components/ui/Skeleton'
import { useToast } from '../components/ui/Toast'
import { useI18n } from '../i18n/I18nProvider'
import { useErrorText } from '../i18n/useErrorText'
import { DAILY_PRESETS, LIVE_PRESETS, presetLabel } from '../lib/onlineClock'

const LOBBY_KEY = ['online', 'lobby']

/** Online play: challenges to and from you, your games, and games to watch. */
export function PlayPage() {
  const p = useI18n().t.play
  const navigate = useNavigate()
  const q = useQuery({ queryKey: LOBBY_KEY, queryFn: onlineApi.lobby, refetchInterval: 4_000 })

  // Someone took our challenge for a game with a clock: go to the board
  const known = useRef<Set<number> | null>(null)
  useEffect(() => {
    if (!q.data) return
    const before = known.current
    known.current = new Set(q.data.myGames.map((g) => g.id))
    const started = before && q.data.myGames.find((g) => !before.has(g.id) && g.timeControl.kind === 'LIVE' && g.plies === 0)
    if (started) navigate(`/play/${started.id}`)
  }, [q.data, navigate])

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={p.title} description={p.description} />
      {q.isPending ? (
        <Panel>
          <SkeletonRows rows={5} />
        </Panel>
      ) : q.isError ? (
        <Panel>
          <ErrorNotice error={q.error} onRetry={() => void q.refetch()} />
        </Panel>
      ) : (
        <LobbyView lobby={q.data} />
      )}
    </div>
  )
}

function LobbyView({ lobby }: { lobby: Lobby }) {
  const p = useI18n().t.play
  const others = lobby.playing.filter((g) => g.myColour === null)
  return (
    <>
      {lobby.challengesToMe.length > 0 && (
        <Panel title={p.challengesToMe}>
          <ul className="divide-y divide-line">
            {lobby.challengesToMe.map((c) => (
              <ChallengeRow key={c.id} challenge={c} kind="toMe" />
            ))}
          </ul>
        </Panel>
      )}

      <div className="grid items-start gap-6 lg:grid-cols-[1fr_24rem]">
        <Panel title={p.myGames}>
          {lobby.myGames.length === 0 ? (
            <p className="flex items-start gap-3 px-5 pt-1 pb-5 text-[0.9375rem] text-muted">
              <Swords aria-hidden className="mt-0.5 size-5 shrink-0 text-faint" />
              {p.noGames}
            </p>
          ) : (
            <ul className="divide-y divide-line">
              {[...lobby.myTurn, ...lobby.myGames.filter((g) => !lobby.myTurn.some((m) => m.id === g.id))].map((g) => (
                <GameRowItem key={g.id} game={g} />
              ))}
            </ul>
          )}
        </Panel>
        <Panel title={p.challengeTitle}>
          <ChallengeForm />
        </Panel>
      </div>

      {(lobby.myChallenges.length > 0 || lobby.openChallenges.length > 0) && (
        <div className="grid gap-6 md:grid-cols-2">
          {lobby.myChallenges.length > 0 && (
            <Panel title={p.myChallenges}>
              <ul className="divide-y divide-line">
                {lobby.myChallenges.map((c) => (
                  <ChallengeRow key={c.id} challenge={c} kind="mine" />
                ))}
              </ul>
            </Panel>
          )}
          {lobby.openChallenges.length > 0 && (
            <Panel title={p.openChallenges}>
              <ul className="divide-y divide-line">
                {lobby.openChallenges.map((c) => (
                  <ChallengeRow key={c.id} challenge={c} kind="open" />
                ))}
              </ul>
            </Panel>
          )}
        </div>
      )}

      <Panel title={p.playing}>
        {others.length === 0 ? (
          <p className="px-5 pt-1 pb-5 text-[0.9375rem] text-muted">{p.nobodyPlaying}</p>
        ) : (
          <ul className="divide-y divide-line">
            {others.map((g) => (
              <GameRowItem key={g.id} game={g} />
            ))}
          </ul>
        )}
      </Panel>

      {lobby.recent.length > 0 && (
        <Panel title={p.recent}>
          <ul className="divide-y divide-line">
            {lobby.recent.map((g) => (
              <GameRowItem key={g.id} game={g} />
            ))}
          </ul>
        </Panel>
      )}
    </>
  )
}

function summary(p: ReturnType<typeof useI18n>['t']['play'], tc: { kind: ClockKind; label: string; daysPerMove: number }, rated: boolean) {
  return tc.kind === 'DAILY' ? p.dailySummary(tc.daysPerMove, rated) : p.challengeSummary(tc.label, rated)
}

function GameRowItem({ game: g }: { game: OnlineGame }) {
  const { t } = useI18n()
  const p = t.play
  const myTurn = g.status === 'ACTIVE' && g.myColour !== null && g.myColour === g.turn
  const result = g.result === 'WHITE_WIN' ? '1–0' : g.result === 'BLACK_WIN' ? '0–1' : g.result === 'DRAW' ? '½–½' : null
  return (
    <li>
      <Link to={`/play/${g.id}`} className="flex items-center gap-3 px-5 py-3 hover:bg-raised/60">
        <span className="min-w-0 flex-1">
          <span className="block font-medium break-words">
            {g.white.realName} <span className="font-normal text-muted">{p.vs}</span> {g.black.realName}
          </span>
          <span className="flex items-center gap-1.5 text-[0.8125rem] text-muted">
            <Clock aria-hidden className="size-3.5" />
            {summary(p, g.timeControl, g.rated)} · {t.live.moveCount(Math.ceil(g.plies / 2))}
          </span>
        </span>
        {g.status === 'ACTIVE' ? (
          myTurn ? (
            <span className="rounded-full bg-brand px-2.5 py-0.5 text-[0.8125rem] font-medium text-on-accent">{p.yourTurn}</span>
          ) : g.myColour === null ? (
            <span className="text-[0.8125rem] font-medium text-accent">{p.watch}</span>
          ) : null
        ) : (
          <span className="font-display font-semibold tabular-nums text-muted">{result ?? '–'}</span>
        )}
      </Link>
    </li>
  )
}

function ChallengeRow({ challenge: c, kind }: { challenge: Challenge; kind: 'toMe' | 'mine' | 'open' }) {
  const { t } = useI18n()
  const p = t.play
  const qc = useQueryClient()
  const toast = useToast()
  const errorText = useErrorText()
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const who = kind === 'mine' ? c.opponent : c.createdBy

  async function run(action: () => Promise<unknown>) {
    setBusy(true)
    try {
      const res = await action()
      if (res && typeof res === 'object' && 'fen' in res) {
        navigate(`/play/${(res as OnlineGame).id}`)
        return
      }
      void qc.invalidateQueries({ queryKey: LOBBY_KEY })
    } catch (e) {
      toast(errorText(e), 'error')
      void qc.invalidateQueries({ queryKey: LOBBY_KEY })
    } finally {
      setBusy(false)
    }
  }

  // The colour the person looking at it would get
  const colourText =
    c.colour === 'RANDOM'
      ? p.randomColours
      : kind === 'mine'
        ? p.youPlay(p.colours[c.colour])
        : p.youPlay(p.colours[c.colour === 'WHITE' ? 'BLACK' : 'WHITE'])

  return (
    <li className="flex flex-wrap items-center gap-3 px-5 py-3">
      {who ? <Avatar name={who.realName} seed={who.username} size="sm" /> : <Swords aria-hidden className="size-5 text-muted" />}
      <span className="min-w-0 flex-1">
        <span className="block font-medium break-words">
          {kind === 'toMe' ? p.challengedYou(c.createdBy.realName) : kind === 'open' ? p.openBy(c.createdBy.realName) : who ? p.waitingFor(who.realName) : p.openToAnyone}
          {who && (
            <span className="ml-1.5 text-[0.8125rem] font-normal text-muted">
              <Rating value={who.rating} provisional={who.provisional} />
            </span>
          )}
        </span>
        <span className="text-[0.8125rem] text-muted">
          {summary(p, c.timeControl, c.rated)} · {colourText}
        </span>
      </span>
      <span className="flex gap-2">
        {kind === 'mine' ? (
          <Button variant="ghost" size="sm" disabled={busy} onClick={() => void run(() => onlineApi.cancel(c.id))}>
            {p.withdraw}
          </Button>
        ) : (
          <>
            <Button size="sm" loading={busy} onClick={() => void run(() => onlineApi.accept(c.id))}>
              {kind === 'toMe' ? p.accept : p.take}
            </Button>
            {kind === 'toMe' && (
              <Button variant="secondary" size="sm" disabled={busy} onClick={() => void run(() => onlineApi.decline(c.id))}>
                {p.decline}
              </Button>
            )}
          </>
        )}
      </span>
    </li>
  )
}

function ChallengeForm() {
  const { t } = useI18n()
  const p = t.play
  const { me } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const errorText = useErrorText()
  const players = useQuery({ queryKey: ['users'], queryFn: usersApi.list })
  const [opponent, setOpponent] = useState<number | ''>('')
  const [kind, setKind] = useState<ClockKind>('LIVE')
  const [preset, setPreset] = useState('2')
  const [days, setDays] = useState('3')
  const [colour, setColour] = useState<ChallengeColour>('RANDOM')
  const [rated, setRated] = useState<'rated' | 'casual'>('rated')
  const [sending, setSending] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setSending(true)
    const live = LIVE_PRESETS[Number(preset)]
    try {
      await onlineApi.challenge({
        opponentId: opponent === '' ? null : opponent,
        colour,
        rated: rated === 'rated',
        kind,
        ...(kind === 'LIVE' ? { initialSeconds: live.initialSeconds, incrementSeconds: live.incrementSeconds } : { daysPerMove: Number(days) }),
      })
      const name = players.data?.find((u) => u.id === opponent)?.realName
      toast(name ? p.sent(name) : p.sentOpen)
      void qc.invalidateQueries({ queryKey: LOBBY_KEY })
    } catch (err) {
      toast(errorText(err), 'error')
    } finally {
      setSending(false)
    }
  }

  return (
    <form onSubmit={(e) => void submit(e)} className="flex flex-col gap-4 px-5 pt-1 pb-5">
      <div className="flex flex-col gap-1.5">
        <label htmlFor="challenge-opponent" className="text-sm font-medium">
          {p.opponent}
        </label>
        <PlayerSelect
          id="challenge-opponent"
          players={players.data ?? []}
          value={opponent}
          onChange={setOpponent}
          exclude={me?.id}
          placeholder={p.anyone}
        />
      </div>
      <Segmented
        label={p.timeControl}
        value={kind}
        onChange={setKind}
        options={[
          { value: 'LIVE', label: p.kinds.LIVE },
          { value: 'DAILY', label: p.kinds.DAILY },
        ]}
      />
      {kind === 'LIVE' ? (
        <Segmented
          label={p.kindHints.LIVE}
          value={preset}
          onChange={setPreset}
          options={LIVE_PRESETS.map((lp, i) => ({ value: String(i), label: <span className="tabular-nums">{presetLabel(lp)}</span> }))}
        />
      ) : (
        <Segmented
          label={p.kindHints.DAILY}
          value={days}
          onChange={setDays}
          options={DAILY_PRESETS.map((d) => ({ value: String(d), label: p.days(d) }))}
        />
      )}
      <Segmented
        label={p.colour}
        value={colour}
        onChange={setColour}
        options={(['WHITE', 'RANDOM', 'BLACK'] as const).map((c) => ({ value: c, label: p.colours[c] }))}
      />
      <Segmented
        label={p.rating}
        value={rated}
        onChange={setRated}
        options={[
          { value: 'rated', label: p.ratedOptions.rated, hint: p.ratedHints.rated },
          { value: 'casual', label: p.ratedOptions.casual, hint: p.ratedHints.casual },
        ]}
      />
      <Button type="submit" loading={sending} className="self-start">
        <Swords aria-hidden className="size-4" />
        {p.send}
      </Button>
    </form>
  )
}
