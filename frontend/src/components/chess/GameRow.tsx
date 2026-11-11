import { Link } from 'react-router'
import { MessageCircle, ThumbsUp } from 'lucide-react'
import type { Game } from '../../api/types'
import { useI18n } from '../../i18n/I18nProvider'
import { cn } from '../../lib/cn'
import { Avatar } from './Avatar'
import { RatingDelta, ResultMark } from './ResultMark'

const statusTone: Record<Game['status'], string> = {
  PENDING: 'text-gold',
  DISPUTED: 'text-accent',
  CONFIRMED: 'text-muted',
  VOIDED: 'text-faint line-through',
}

/** A game as a scoresheet line: White · result · Black, with rating changes under each name. */
export function GameRow({ game }: { game: Game }) {
  const { t, f } = useI18n()
  const rc = game.ratingChange
  return (
    <li>
      <Link
        to={`/games/${game.id}`}
        className="grid grid-cols-[1fr_auto_1fr] items-center gap-2 px-4 py-3 transition-colors hover:bg-raised/70 sm:grid-cols-[4.5rem_1fr_auto_1fr_8rem] sm:gap-3 sm:px-5"
      >
        <span className="hidden text-sm text-muted sm:block">{f.shortDate(game.playedAt)}</span>
        <Side name={game.white.realName} seed={game.white.username} delta={rc?.white} winner={game.result === 'WHITE_WIN'} align="end" colourLabel={t.common.white} light />
        <span className="flex flex-col items-center">
          <ResultMark result={game.result} className={cn('w-12 text-center text-lg', game.status === 'VOIDED' && 'text-faint line-through')} />
          {(game.kudosCount > 0 || game.commentCount > 0) && (
            <span className="flex items-center gap-2 text-[0.75rem] text-muted tabular-nums">
              {game.kudosCount > 0 && (
                <span className="inline-flex items-center gap-0.5" aria-label={t.social.kudosCount(game.kudosCount)}>
                  <ThumbsUp aria-hidden className="size-3" />
                  {game.kudosCount}
                </span>
              )}
              {game.commentCount > 0 && (
                <span className="inline-flex items-center gap-0.5" aria-label={t.social.commentCount(game.commentCount)}>
                  <MessageCircle aria-hidden className="size-3" />
                  {game.commentCount}
                </span>
              )}
            </span>
          )}
        </span>
        <Side name={game.black.realName} seed={game.black.username} delta={rc?.black} winner={game.result === 'BLACK_WIN'} align="start" colourLabel={t.common.black} />
        <span className={cn('hidden text-right text-[0.8125rem] sm:block', statusTone[game.status])}>
          {game.status === 'CONFIRMED' ? (game.tournament?.name ?? '') : t.gameStatus[game.status]}
        </span>
      </Link>
    </li>
  )
}

function Side({
  name,
  seed,
  delta,
  winner,
  align,
  colourLabel,
  light,
}: {
  name: string
  seed: string
  delta?: { before: number; after: number }
  winner: boolean
  align: 'start' | 'end'
  colourLabel: string
  light?: boolean
}) {
  return (
    <span className={cn('flex min-w-0 items-center gap-2.5', align === 'end' && 'flex-row-reverse text-right')}>
      <span className="hidden sm:inline-flex">
        <Avatar name={name} seed={seed} size="sm" />
      </span>
      <span className="min-w-0">
        <span className={cn('flex items-center gap-1.5', align === 'end' && 'justify-end')}>
          <span role="img" aria-label={colourLabel} className={cn('size-2 shrink-0 rounded-[2px] border border-faint', light ? 'bg-trim' : 'bg-roof')} />
          <span className={cn('truncate text-[0.9375rem]', winner ? 'font-semibold text-ink' : 'text-ink/80')}>{name}</span>
        </span>
        {delta && <RatingDelta before={delta.before} after={delta.after} className="block text-[0.8125rem]" />}
      </span>
    </span>
  )
}
