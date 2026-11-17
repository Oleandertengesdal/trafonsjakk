import { memo, useEffect, useRef } from 'react'
import { glyph, type GameReview } from '../../chess/analysis'
import { useI18n } from '../../i18n/I18nProvider'
import { cn } from '../../lib/cn'
import { glyphColour } from './judgement'

/** The moves, two to a row; tap one to go there. Only redraws when the move or the marks change. */
export const MoveList = memo(function MoveList({
  sans,
  ply,
  review,
  onSelect,
}: {
  sans: string[]
  ply: number
  review: GameReview | null
  onSelect: (ply: number) => void
}) {
  const t = useI18n().t
  const listRef = useRef<HTMLOListElement>(null)
  useEffect(() => {
    const list = listRef.current
    const el = list?.querySelector<HTMLElement>('[aria-current="true"]')
    if (list && el && (el.offsetTop < list.scrollTop || el.offsetTop > list.scrollTop + list.clientHeight - el.offsetHeight)) {
      list.scrollTop = el.offsetTop - list.clientHeight / 2
    }
  }, [ply])
  const rows: { n: number; white: number; black: number | null }[] = []
  for (let i = 1; i <= sans.length; i += 2) rows.push({ n: (i + 1) / 2, white: i, black: i + 1 <= sans.length ? i + 1 : null })
  const move = (p: number) => {
    const j = review?.moves[p - 1].judgement
    return (
      <button
        type="button"
        onClick={() => onSelect(p)}
        aria-current={p === ply ? 'true' : undefined}
        className={cn('rounded px-1.5 py-0.5 text-left transition-colors', p === ply ? 'bg-brand text-on-accent' : 'hover:bg-raised')}
      >
        {sans[p - 1]}
        {j && <span className={cn('ml-0.5 font-sans font-semibold', p === ply ? 'text-on-accent' : glyphColour[j])}>{glyph[j]}</span>}
      </button>
    )
  }
  return (
    <ol
      ref={listRef}
      aria-label={t.moves.list}
      className="relative grid max-h-60 grid-cols-[auto_1fr_1fr] content-start gap-x-2 gap-y-0.5 overflow-y-auto font-mono text-sm tabular-nums"
    >
      {rows.map((r) => (
        <li key={r.n} className="col-span-3 grid grid-cols-subgrid items-center">
          <span className="pr-1 text-faint">{r.n}.</span>
          {move(r.white)}
          {r.black ? move(r.black) : <span />}
        </li>
      ))}
    </ol>
  )
})

