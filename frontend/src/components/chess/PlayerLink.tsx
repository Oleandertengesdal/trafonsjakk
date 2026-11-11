import { Link } from 'react-router'
import type { PlayerRef } from '../../api/types'
import { useI18n } from '../../i18n/I18nProvider'
import { cn } from '../../lib/cn'
import { Avatar } from './Avatar'

export function Rating({ value, provisional, className }: { value: number; provisional: boolean; className?: string }) {
  const { t } = useI18n()
  return (
    <span className={cn('tabular-nums', className)} title={provisional ? t.rating.provisionalHint : undefined}>
      {value}
      {provisional && <span className="text-faint">?</span>}
    </span>
  )
}

export function PlayerLink({
  player,
  showAvatar = true,
  showRating = false,
  className,
}: {
  player: PlayerRef
  showAvatar?: boolean
  showRating?: boolean
  className?: string
}) {
  return (
    <Link to={`/players/${player.username}`} className={cn('group inline-flex min-w-0 items-center gap-2.5 rounded-md', className)}>
      {showAvatar && <Avatar name={player.realName} seed={player.username} size="sm" />}
      <span className="min-w-0 truncate font-medium group-hover:text-accent">{player.realName}</span>
      {showRating && <Rating value={player.rating} provisional={player.provisional} className="text-sm text-muted" />}
    </Link>
  )
}
