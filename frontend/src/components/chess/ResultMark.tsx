import type { GameResult } from '../../api/types'
import { notation, signed } from '../../i18n/format'
import { useI18n } from '../../i18n/I18nProvider'
import { cn } from '../../lib/cn'

/** Scoresheet notation: 1–0, ½–½, 0–1 (same in every language). */
export function ResultMark({ result, className }: { result: GameResult; className?: string }) {
  const { t } = useI18n()
  return (
    <span className={cn('font-display font-semibold tabular-nums tracking-tight', className)} aria-label={t.result[result]}>
      {notation[result]}
    </span>
  )
}

export function RatingDelta({ before, after, className }: { before: number; after: number; className?: string }) {
  const { t } = useI18n()
  const d = after - before
  return (
    <span
      className={cn('text-sm font-medium tabular-nums', d > 0 ? 'text-win' : d < 0 ? 'text-loss' : 'text-muted', className)}
      aria-label={t.rating.change(signed(d))}
    >
      {signed(d)}
    </span>
  )
}

// Fixed tones (not theme tokens) so the cream letters stay readable in both themes.
const formTone = { W: 'bg-[#2b7651] text-[#fff9f2]', D: 'bg-[#66717b] text-[#fff9f2]', L: 'bg-[#a8493f] text-[#fff9f2]' }

/** Recent results as small tiles, oldest → newest. Letters are localised (W/D/L or S/R/T). */
export function FormStrip({ form, className }: { form: ('W' | 'D' | 'L')[]; className?: string }) {
  const { t } = useI18n()
  if (!form.length) return <span className="text-sm text-faint">{t.outcome.noGames}</span>
  return (
    <span className={cn('inline-flex gap-1', className)} role="img" aria-label={t.outcome.recentForm(form.map((f) => t.outcome.word[f]))}>
      {form.map((f, i) => (
        <span key={i} aria-hidden className={cn('inline-flex size-5.5 items-center justify-center rounded-[5px] text-[0.6875rem] font-semibold', formTone[f])}>
          {t.outcome.letter[f]}
        </span>
      ))}
    </span>
  )
}
