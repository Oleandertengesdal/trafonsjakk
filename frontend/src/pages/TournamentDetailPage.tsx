import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Crown, Play, UserMinus, UserPlus, XCircle } from 'lucide-react'
import { tournamentsApi } from '../api/endpoints'
import { invalidateResults } from '../api/queryClient'
import type { Match, TournamentDetail } from '../api/types'
import { useAuth } from '../auth/AuthProvider'
import { Avatar } from '../components/chess/Avatar'
import { Bracket } from '../components/chess/Bracket'
import { Crosstable } from '../components/chess/Crosstable'
import { Rating } from '../components/chess/PlayerLink'
import { ResultMark } from '../components/chess/ResultMark'
import { PageHeader } from '../components/layout/PageHeader'
import { Button } from '../components/ui/Button'
import { Dialog } from '../components/ui/Dialog'
import { ErrorNotice } from '../components/ui/ErrorNotice'
import { Panel } from '../components/ui/Panel'
import { Skeleton } from '../components/ui/Skeleton'
import { useToast } from '../components/ui/Toast'
import { useI18n } from '../i18n/I18nProvider'
import { roundName } from '../i18n/rounds'
import { useErrorText } from '../i18n/useErrorText'
import { cn } from '../lib/cn'
import { isNotFound } from '../api/client'
import { NotFoundPage } from './NotFoundPage'

type Action = 'join' | 'leave' | 'start' | 'cancel'

export function TournamentDetailPage() {
  const { id: rawId } = useParams()
  const id = Number(rawId)
  const valid = Number.isInteger(id) && id > 0
  const { t } = useI18n()
  const td = t.tournamentDetail
  const q = useQuery({ queryKey: ['tournament', id], queryFn: () => tournamentsApi.get(id), enabled: valid })

  if (!valid) return <NotFoundPage what="tournament" />
  if (q.isPending)
    return (
      <div className="flex flex-col gap-6" role="status" aria-label={t.common.loading}>
        <Skeleton className="h-12 w-72" />
        <Skeleton className="h-96 rounded-panel" />
      </div>
    )
  if (q.isError && isNotFound(q.error)) return <NotFoundPage what="tournament" />
  if (q.isError)
    return (
      <Panel>
        <ErrorNotice error={q.error} onRetry={() => q.refetch()} />
      </Panel>
    )
  const d = q.data
  const s = d.summary
  const formatLabel = s.doubleRoundRobin ? `${t.format[s.format]} (${t.format.double})` : t.format[s.format]

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={s.name}
        description={
          <>
            {td.meta(formatLabel, t.common.players(s.participantCount), s.createdBy.realName)}{' '}
            <span className={cn(s.status === 'IN_PROGRESS' && 'text-win', s.status === 'REGISTRATION' && 'text-gold')}>
              {t.tournamentStatus[s.status]}
              {s.status === 'IN_PROGRESS' &&
                (s.format === 'SWISS'
                  ? ` ${td.roundProgress(d.rounds.length, d.totalRounds)}`
                  : d.totalMatches > 0 && ` ${td.progress(d.completedMatches, d.totalMatches)}`)}
            </span>
          </>
        }
        action={<Actions detail={d} />}
      />

      {s.winner && (
        <div className="brick flex items-center gap-4 rounded-t-[2rem] rounded-b-panel px-6 py-5 text-on-accent">
          <Crown aria-hidden className="size-8 shrink-0" />
          <div>
            <p className="text-[0.8125rem] text-on-accent/80">{td.champion}</p>
            <Link to={`/players/${s.winner.username}`} className="font-display text-2xl font-bold hover:underline">
              {s.winner.realName}
            </Link>
          </div>
        </div>
      )}

      {s.status === 'REGISTRATION' ? (
        <Panel title={td.signedUp}>
          {d.participants.length === 0 ? (
            <p className="px-5 pb-5 text-muted">{td.nobody}</p>
          ) : (
            <ul className="grid gap-1 p-2 sm:grid-cols-2 lg:grid-cols-3">
              {d.participants.map(({ player }) => (
                <li key={player.id} className="flex items-center gap-3 rounded-control px-3 py-2.5">
                  <Avatar name={player.realName} seed={player.username} size="sm" />
                  <Link to={`/players/${player.username}`} className="flex-1 truncate hover:text-accent">
                    {player.realName}
                  </Link>
                  <Rating value={player.rating} provisional={player.provisional} className="text-sm text-muted" />
                </li>
              ))}
            </ul>
          )}
        </Panel>
      ) : s.format === 'ROUND_ROBIN' ? (
        <RoundRobinView detail={d} />
      ) : s.format === 'SWISS' ? (
        <SwissView detail={d} />
      ) : (
        <Panel title={td.bracket}>
          <Bracket detail={d} />
        </Panel>
      )}
    </div>
  )
}

function Actions({ detail: d }: { detail: TournamentDetail }) {
  const { t } = useI18n()
  const td = t.tournamentDetail
  const errorText = useErrorText()
  const toast = useToast()
  const [confirmCancel, setConfirmCancel] = useState(false)
  const id = d.summary.id
  const run = useMutation({
    mutationFn: (a: Action) => tournamentsApi.action(id, a),
    onSuccess: async (_, a) => {
      setConfirmCancel(false)
      await invalidateResults()
      toast({ join: td.joined, leave: td.left, start: td.started, cancel: td.cancelled }[a])
    },
    onError: (e) => toast(errorText(e), 'error'),
  })
  const reg = d.summary.status === 'REGISTRATION'
  const busy = (a: Action) => run.isPending && run.variables === a
  return (
    <div className="flex flex-wrap gap-2">
      {reg && d.canJoin && (
        <Button variant="secondary" onClick={() => run.mutate('join')} loading={busy('join')} disabled={run.isPending}>
          <UserPlus aria-hidden className="size-4.5" /> {td.join}
        </Button>
      )}
      {reg && d.joined && (
        <Button variant="ghost" onClick={() => run.mutate('leave')} loading={busy('leave')} disabled={run.isPending}>
          <UserMinus aria-hidden className="size-4.5" /> {td.leave}
        </Button>
      )}
      {reg && d.canManage && (
        <Button onClick={() => run.mutate('start')} loading={busy('start')} disabled={run.isPending}>
          <Play aria-hidden className="size-4.5" /> {td.start}
        </Button>
      )}
      {d.canManage && (
        <Button variant="danger" onClick={() => setConfirmCancel(true)} disabled={run.isPending}>
          <XCircle aria-hidden className="size-4.5" /> {td.cancel}
        </Button>
      )}
      <Dialog open={confirmCancel} onClose={() => setConfirmCancel(false)} title={td.cancelTitle}>
        <p className="text-[0.9375rem] text-muted">{td.cancelBody}</p>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="ghost" onClick={() => setConfirmCancel(false)}>
            {td.keep}
          </Button>
          <Button variant="danger" onClick={() => run.mutate('cancel')} loading={busy('cancel')}>
            {td.cancel}
          </Button>
        </div>
      </Dialog>
    </div>
  )
}

function RoundRobinView({ detail: d }: { detail: TournamentDetail }) {
  const { t } = useI18n()
  const td = t.tournamentDetail
  const all = d.rounds.flatMap((r) => r.matches)
  return (
    <>
      <Panel title={td.standings}>
        <div className="pb-2">
          <Crosstable standings={d.standings ?? []} matches={all} />
        </div>
      </Panel>
      <Panel title={td.pairings}>
        <div className="grid sm:grid-cols-2 lg:grid-cols-3">
          {d.rounds.map((r) => (
            <section key={r.number} className="border-t border-line px-5 py-4">
              <h3 className={cn('mb-2 text-sm font-medium', r.number === d.currentRound ? 'text-accent' : 'text-muted')}>
                {roundName(t, 'ROUND_ROBIN', r.number, d.totalRounds)}
                {r.number === d.currentRound && ` ${td.upNext}`}
              </h3>
              <ul className="flex flex-col gap-1.5">
                {r.matches.map((m) => (
                  <PairingLine key={m.id} match={m} />
                ))}
              </ul>
            </section>
          ))}
        </div>
      </Panel>
    </>
  )
}

/** Swiss: the table (points, then Buchholz) and the rounds so far; the next round is paired when one ends. */
function SwissView({ detail: d }: { detail: TournamentDetail }) {
  const { t, f } = useI18n()
  const td = t.tournamentDetail
  const played = d.rounds.length
  const finished = d.summary.status === 'COMPLETED'
  return (
    <>
      <Panel title={td.standings}>
        <p className="max-w-[65ch] px-5 text-sm text-muted">
          {finished ? td.swissDone(played) : td.swissProgress(played, d.totalRounds)} {td.swissRule}
        </p>
        <div className="overflow-x-auto pb-2">
          <table className="mt-2 w-full text-[0.9375rem]">
            <caption className="sr-only">{td.standings}</caption>
            <thead>
              <tr className="text-left text-[0.8125rem] text-muted">
                <th scope="col" className="w-12 px-5 py-2 font-medium">#</th>
                <th scope="col" className="py-2 font-medium">{td.player}</th>
                <th scope="col" className="px-3 py-2 text-right font-medium">{td.points}</th>
                <th scope="col" className="hidden px-3 py-2 text-right font-medium sm:table-cell">{t.leaderboard.wdl}</th>
                <th scope="col" className="px-5 py-2 text-right font-medium" title={td.buchholzHint}>
                  {td.buchholz}
                </th>
              </tr>
            </thead>
            <tbody>
              {(d.standings ?? []).map((s) => (
                <tr key={s.player.id} className="border-t border-line">
                  <td className={cn('px-5 py-2.5 font-display font-semibold tabular-nums', s.rank === 1 ? 'text-gold' : 'text-faint')}>{s.rank}</td>
                  <td className="py-2.5">
                    <Link to={`/players/${s.player.username}`} className="flex items-center gap-2.5 hover:text-accent">
                      <Avatar name={s.player.realName} seed={s.player.username} size="sm" />
                      <span className="truncate">{s.player.realName}</span>
                    </Link>
                  </td>
                  <td className="px-3 py-2.5 text-right font-medium tabular-nums">{f.decimal1(s.points).replace(/[.,]0$/, '')}</td>
                  <td className="hidden px-3 py-2.5 text-right text-muted tabular-nums sm:table-cell">
                    {s.wins} / {s.draws} / {s.losses}
                    {s.byes > 0 && <span className="ml-1.5 text-faint">{td.byes(s.byes)}</span>}
                  </td>
                  <td className="px-5 py-2.5 text-right text-muted tabular-nums">{f.decimal1(s.buchholz ?? 0).replace(/[.,]0$/, '')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Panel>
      <Panel title={td.pairings}>
        <div className="grid sm:grid-cols-2 lg:grid-cols-3">
          {[...d.rounds].reverse().map((r) => (
            <section key={r.number} className="border-t border-line px-5 py-4">
              <h3 className={cn('mb-2 text-sm font-medium', r.number === d.currentRound ? 'text-accent' : 'text-muted')}>
                {roundName(t, 'SWISS', r.number, d.totalRounds)}
                {r.number === d.currentRound && ` ${td.upNext}`}
              </h3>
              <ul className="flex flex-col gap-1.5">
                {r.matches.map((m) =>
                  m.status === 'BYE' && m.white ? (
                    <li key={m.id} className="text-center text-[0.9375rem] text-muted">
                      {td.bye(m.white.realName)}
                    </li>
                  ) : (
                    <PairingLine key={m.id} match={m} />
                  ),
                )}
              </ul>
            </section>
          ))}
        </div>
      </Panel>
    </>
  )
}

function PairingLine({ match: m }: { match: Match }) {
  const { me } = useAuth()
  const { t } = useI18n()
  if (!m.white || !m.black) return null
  const mine = m.white.id === me!.id || m.black.id === me!.id
  return (
    <li className="grid grid-cols-[1fr_4.5rem_1fr] items-center gap-2 text-[0.9375rem]">
      <span className={cn('truncate text-right', m.result === 'WHITE_WIN' && 'font-semibold')}>{m.white.realName}</span>
      <span className="text-center">
        {m.result ? (
          <ResultMark result={m.result} />
        ) : m.openGameId ? (
          <Link to={`/games/${m.openGameId}`} className="text-[0.8125rem] text-gold hover:underline">
            {t.tournamentDetail.waiting}
          </Link>
        ) : mine && m.status === 'READY' ? (
          <Link to={`/log?match=${m.id}`} className="text-[0.8125rem] font-medium text-accent hover:underline">
            {t.tournamentDetail.log}
          </Link>
        ) : (
          <span className="text-faint">–</span>
        )}
      </span>
      <span className={cn('truncate', m.result === 'BLACK_WIN' && 'font-semibold')}>{m.black.realName}</span>
    </li>
  )
}
