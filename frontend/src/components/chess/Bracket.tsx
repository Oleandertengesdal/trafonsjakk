import { Link } from 'react-router'
import { Crown } from 'lucide-react'
import type { Match, PlayerRef, TournamentDetail } from '../../api/types'
import { notation } from '../../i18n/format'
import { useI18n } from '../../i18n/I18nProvider'
import { roundName } from '../../i18n/rounds'
import { cn } from '../../lib/cn'

/** Knockout bracket: one column per round, matches spaced so each pair feeds the next. */
export function Bracket({ detail }: { detail: TournamentDetail }) {
  const { t } = useI18n()
  const seeds = new Map(detail.participants.map((p) => [p.player.id, p.seed]))
  const champion = detail.summary.winner
  return (
    <div className="overflow-x-auto pb-2">
      <ol className="flex min-w-max gap-6 px-5 py-4">
        {detail.rounds.map((round) => (
          <li key={round.number} className="flex w-60 flex-col">
            <h3 className="mb-3 text-sm font-medium text-muted">{roundName(t, 'SINGLE_ELIMINATION', round.number, detail.totalRounds)}</h3>
            <ul className="flex flex-1 flex-col justify-around gap-4">
              {round.matches.map((m) => (
                <MatchCard key={m.id} match={m} seeds={seeds} />
              ))}
            </ul>
          </li>
        ))}
        {champion && (
          <li className="flex w-48 flex-col">
            <h3 className="mb-3 text-sm font-medium text-muted">{t.tournamentDetail.champion}</h3>
            <div className="flex flex-1 items-center">
              <Link
                to={`/players/${champion.username}`}
                className="brick flex w-full items-center gap-2.5 rounded-t-[2rem] rounded-b-control px-4 py-4 font-display font-semibold text-on-accent"
              >
                <Crown aria-hidden className="size-5" />
                {champion.realName}
              </Link>
            </div>
          </li>
        )}
      </ol>
    </div>
  )
}

function MatchCard({ match, seeds }: { match: Match; seeds: Map<number, number | null> }) {
  const { t } = useI18n()
  const decided = match.status === 'COMPLETED' || match.status === 'BYE'
  const won = (p: PlayerRef | null) => decided && !!p && match.winner?.id === p.id
  const lost = (p: PlayerRef | null) => decided && !!p && !!match.winner && match.winner.id !== p.id
  return (
    <li className={cn('overflow-hidden rounded-control border bg-surface text-[0.9375rem]', match.status === 'READY' ? 'border-accent/60' : 'border-line')}>
      <Slot player={match.white} seed={match.white ? seeds.get(match.white.id) : null} won={won(match.white)} lost={lost(match.white)} />
      <div className="border-t border-line" />
      {match.status === 'BYE' ? (
        <div className="px-3 py-2 text-sm text-faint">{t.bracket.bye}</div>
      ) : (
        <Slot player={match.black} seed={match.black ? seeds.get(match.black.id) : null} won={won(match.black)} lost={lost(match.black)} />
      )}
      {(match.needsReplay || match.openGameId || match.games.length > 0) && (
        <div className="border-t border-line px-3 py-1.5 text-[0.8125rem] text-muted">
          {match.needsReplay && <span className="text-gold">{t.bracket.replay}</span>}
          {!match.needsReplay && match.openGameId && (
            <Link to={`/games/${match.openGameId}`} className="text-gold hover:underline">
              {t.bracket.waitingConfirmation}
            </Link>
          )}
          {!match.needsReplay && !match.openGameId && match.games.length > 0 && (
            <span>
              {match.games
                .filter((g) => g.status === 'CONFIRMED')
                .map((g) => notation[g.result])
                .join(', ')}
            </span>
          )}
        </div>
      )}
    </li>
  )
}

function Slot({ player, seed, won, lost }: { player: PlayerRef | null; seed?: number | null; won: boolean; lost: boolean }) {
  const { t } = useI18n()
  if (!player) return <div className="px-3 py-2 text-faint">{t.bracket.tbd}</div>
  return (
    <Link to={`/players/${player.username}`} className={cn('flex items-center gap-2 px-3 py-2 hover:bg-raised/70', lost && 'text-faint', won && 'font-semibold')}>
      <span className="w-5 text-right text-[0.8125rem] text-faint tabular-nums">{seed ?? ''}</span>
      <span className="flex-1 truncate">{player.realName}</span>
      {won && <span role="img" className="size-2 rounded-full bg-win" aria-label={t.bracket.winner} />}
    </Link>
  )
}
