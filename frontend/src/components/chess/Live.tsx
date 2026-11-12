import { Link } from 'react-router'
import type { LiveGame } from '../../api/types'
import { splitMovetext } from '../../chess/movetext'
import { useI18n } from '../../i18n/I18nProvider'
import { Avatar } from './Avatar'

/** Plies in the stored move text, without loading chess.js. */
function countPlies(moves: string | null): number {
  const pairs = moves ? splitMovetext(moves) : null
  return pairs ? pairs.reduce((n, p) => n + (p.black ? 2 : 1), 0) : 0
}

/** A live game in a list: who's playing, how far they've got, and a link to follow it. */
export function LiveRow({ game }: { game: LiveGame }) {
  const { t, f } = useI18n()
  const plies = countPlies(game.moves)
  return (
    <li>
      <Link to={`/live/${game.id}`} className="group flex items-center gap-4 py-3">
        <span className="flex gap-1">
          <Avatar name={game.white.realName} seed={game.white.username} size="sm" />
          <Avatar name={game.black.realName} seed={game.black.username} size="sm" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate font-medium group-hover:text-accent">
            {game.white.realName} <span className="font-normal text-muted">{t.live.vs}</span> {game.black.realName}
          </span>
          <span className="flex flex-wrap gap-x-3 text-sm text-muted">
            <span>{t.live.moveCount(Math.ceil(plies / 2))}</span>
            <span>{t.live.startedAt(f.relative(game.startedAt))}</span>
          </span>
        </span>
        <LiveDot label={t.live.liveBadge} />
      </Link>
    </li>
  )
}

/** A small pulsing "live" marker. The pulse stops for people who prefer less motion. */
export function LiveDot({ label }: { label: string }) {
  return (
    <span className="inline-flex shrink-0 items-center gap-1.5 rounded-full bg-accent-soft px-2.5 py-1 text-[0.8125rem] font-semibold text-accent">
      <span aria-hidden className="relative flex size-2">
        <span className="absolute inline-flex size-full rounded-full bg-accent opacity-60 motion-safe:animate-ping" />
        <span className="relative inline-flex size-2 rounded-full bg-accent" />
      </span>
      {label}
    </span>
  )
}
