import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { Trophy, Zap } from 'lucide-react'
import { gamesApi } from '../api/endpoints'
import type { PlayerRef, RatingDelta as Delta } from '../api/types'
import { useAuth } from '../auth/AuthProvider'
import { AdminResolveControls } from '../components/chess/AdminResolveControls'
import { Avatar } from '../components/chess/Avatar'
import { GameActions } from '../components/chess/GameActions'
import { GameComments } from '../components/chess/GameComments'
import { GameContextPanel } from '../components/chess/GameContextPanel'
import { GameMoves } from '../components/chess/GameMoves'
import { KudosButton } from '../components/chess/KudosButton'
import { RatingDelta, ResultMark } from '../components/chess/ResultMark'
import { ErrorNotice } from '../components/ui/ErrorNotice'
import { Panel } from '../components/ui/Panel'
import { Skeleton } from '../components/ui/Skeleton'
import { useI18n } from '../i18n/I18nProvider'
import { cn } from '../lib/cn'

export function GameDetailPage() {
  const { id: rawId } = useParams()
  const id = Number(rawId)
  const { me } = useAuth()
  const { t, f } = useI18n()
  const q = useQuery({ queryKey: ['games', 'detail', id], queryFn: () => gamesApi.get(id), enabled: Number.isInteger(id) && id > 0 })
  // Shared with the context panel below; only a confirmed result can be an upset.
  const ctx = useQuery({ queryKey: ['games', 'context', id], queryFn: () => gamesApi.context(id), enabled: q.data?.status === 'CONFIRMED' })

  if (!Number.isInteger(id) || id <= 0) return <Panel><ErrorNotice error={null} /></Panel>
  if (q.isPending) return <Skeleton className="h-72 w-full rounded-panel" />
  if (q.isError) return <Panel><ErrorNotice error={q.error} onRetry={() => q.refetch()} /></Panel>
  const g = q.data
  const d = t.gameDetail
  const statusTone = { PENDING: 'text-gold', DISPUTED: 'text-accent', CONFIRMED: 'text-win', VOIDED: 'text-faint' }[g.status]

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-2 text-[0.9375rem]">
        <Link to="/games" className="text-muted hover:text-accent">
          {d.breadcrumb}
        </Link>
        <span className={cn('font-medium', statusTone)}>{t.gameStatus[g.status]}</span>
      </div>

      <section className="rounded-t-[2.5rem] rounded-b-panel border border-line bg-surface px-5 py-8 sm:px-8">
        {g.tournament && (
          <Link to={`/tournaments/${g.tournament.id}`} className="mb-6 flex items-center justify-center gap-2 text-[0.9375rem] text-accent hover:underline">
            <Trophy aria-hidden className="size-4" /> {d.tournamentRound(g.tournament.name, g.tournament.round)}
          </Link>
        )}
        <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-4">
          <PlayerSide player={g.white} colour={t.common.white} delta={g.ratingChange?.white} won={g.result === 'WHITE_WIN'} />
          <ResultMark result={g.result} className={cn('text-5xl sm:text-6xl', g.status === 'VOIDED' && 'text-faint line-through')} />
          <PlayerSide player={g.black} colour={t.common.black} delta={g.ratingChange?.black} won={g.result === 'BLACK_WIN'} />
        </div>
        {ctx.data?.upset && g.status === 'CONFIRMED' && (
          <p className="mt-5 flex justify-center">
            <span className="inline-flex items-center gap-1.5 rounded-full border border-gold/40 bg-gold/8 px-3 py-1 text-sm font-medium text-gold">
              <Zap aria-hidden className="size-3.5 fill-current" />
              {t.context.upset}
            </span>
          </p>
        )}
        <p className="mt-6 text-center text-[0.9375rem] text-muted">{f.dateTime(g.playedAt)}</p>
        <div className="mt-5 flex justify-center">
          <KudosButton game={g} />
        </div>
      </section>

      {(g.canConfirm || g.canWithdraw) && (
        <Panel className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-[0.9375rem] text-muted">
            {g.canConfirm ? d.confirmPrompt(g.reportedBy.realName) : d.waitingOpponent}
            {g.autoConfirmAt && ` ${d.autoConfirm(f.relative(g.autoConfirmAt))}`}
          </p>
          <div className="shrink-0">
            <GameActions game={g} />
          </div>
        </Panel>
      )}

      {g.status === 'DISPUTED' && g.disputeReason && (
        <Panel title={d.disputedTitle} className="border-accent/40">
          <p className="px-5 pb-5 text-[0.9375rem] text-muted">{d.disputedBody(g.disputeReason)}</p>
        </Panel>
      )}

      {me!.role === 'ADMIN' && g.status !== 'VOIDED' && (
        <Panel title={d.adminTitle} className="border-dashed">
          <div className="px-5 pt-1 pb-5">
            <p className="mb-3 text-[0.9375rem] text-muted">{d.adminBody}</p>
            <AdminResolveControls key={`${g.id}-${g.status}-${g.result}`} game={g} />
          </div>
        </Panel>
      )}


      {(g.moves || g.canEditMoves) && (
        <Panel title={g.moves ? t.viewer.title : t.moves.title}>
          <GameMoves key={`${g.id}-${g.moves ?? ''}`} game={g} />
        </Panel>
      )}

      {g.status !== 'VOIDED' && <GameContextPanel game={g} />}

      <Panel title={d.details}>
        <dl className="grid gap-x-6 gap-y-4 px-5 pt-2 pb-5 sm:grid-cols-2">
          <Detail label={d.loggedBy}>
            {g.reportedBy.realName}, {f.dateTime(g.createdAt)}
          </Detail>
          <Detail label={d.confirmed}>
            {g.confirmedAt ? `${g.confirmedBy ? g.confirmedBy.realName : d.automatically}, ${f.dateTime(g.confirmedAt)}` : d.notYet}
          </Detail>
          {(g.opening || g.eco) && (
            <Detail label={d.opening}>
              {g.opening}
              {g.eco && <span className="text-muted"> ({g.eco})</span>}
            </Detail>
          )}
          {g.notes && (
            <Detail label={d.notes} wide>
              <span className="whitespace-pre-wrap">{g.notes}</span>
            </Detail>
          )}
        </dl>
      </Panel>


      <Panel title={g.commentCount > 0 ? `${t.social.comments} (${g.commentCount})` : t.social.comments} id="comments">
        <GameComments game={g} />
      </Panel>
    </div>
  )
}

function PlayerSide({ player, colour, delta, won }: { player: PlayerRef; colour: string; delta?: Delta; won: boolean }) {
  return (
    <Link to={`/players/${player.username}`} className="flex min-w-0 flex-col items-center gap-2 text-center hover:text-accent">
      <Avatar name={player.realName} seed={player.username} size="lg" />
      <span className={cn('font-display text-lg leading-tight break-words sm:text-xl', won ? 'font-bold' : 'font-medium')}>{player.realName}</span>
      <span className="text-[0.8125rem] text-muted">{colour}</span>
      {delta && (
        <span className="text-sm text-muted tabular-nums">
          {delta.before} → {delta.after} <RatingDelta before={delta.before} after={delta.after} />
        </span>
      )}
    </Link>
  )
}

function Detail({ label, children, wide }: { label: string; children: ReactNode; wide?: boolean }) {
  return (
    <div className={wide ? 'sm:col-span-2' : undefined}>
      <dt className="text-[0.8125rem] text-muted">{label}</dt>
      <dd className="mt-0.5 text-[0.9375rem]">{children}</dd>
    </div>
  )
}
