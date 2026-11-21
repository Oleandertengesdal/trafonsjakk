import { Crosshair, Flame, Trophy } from 'lucide-react'
import type { AwardKind } from '../../api/types'
import { cn } from '../../lib/cn'

const ICONS = { PLAYER_OF_MONTH: Trophy, MOST_ACTIVE: Flame, MOST_ACCURATE: Crosshair } as const

/** The symbol of each monthly award, in gold. Decorative unless given a label. */
export function AwardIcon({ kind, label, className }: { kind: AwardKind; label?: string; className?: string }) {
  const Icon = ICONS[kind]
  return label ? (
    <Icon role="img" aria-label={label} className={cn('size-4 shrink-0 text-gold', className)} />
  ) : (
    <Icon aria-hidden className={cn('size-4 shrink-0 text-gold', className)} />
  )
}
