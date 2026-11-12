import { useCallback, useRef, useState, type KeyboardEvent, type PointerEvent, type ReactNode } from 'react'
import { ChevronLeft, ChevronRight, Share2 } from 'lucide-react'
import { useI18n } from '../../i18n/I18nProvider'
import { cn } from '../../lib/cn'
import { Button } from '../ui/Button'
import { useToast } from '../ui/Toast'
import { shareCard } from './shareCard'

export interface Slide {
  key: string
  /** The card (a StoryCard). */
  card: ReactNode
  /** Readable summary for screen readers and the image file name. */
  label: string
}

/**
 * Wrapped-style stories: one card at a time. Tap the right side (or swipe left, or →) for the next card,
 * the left side for the previous one. Every card can be shared as an image.
 * Give it a `key` that changes with the content, so it starts on the first card.
 */
export function StoryViewer({ slides, fileStem }: { slides: Slide[]; fileStem: string }) {
  const { t } = useI18n()
  const y = t.year
  const toast = useToast()
  const [i, setI] = useState(0)
  const [busy, setBusy] = useState(false)
  const card = useRef<HTMLDivElement>(null)
  const swipe = useRef<{ x: number; y: number; moved: boolean } | null>(null)
  const total = slides.length
  const index = Math.min(i, total - 1)

  const go = useCallback((n: number) => setI(Math.max(0, Math.min(total - 1, n))), [total])

  const onKey = (e: KeyboardEvent) => {
    if (e.key === 'ArrowRight' || e.key === 'PageDown') go(index + 1)
    else if (e.key === 'ArrowLeft' || e.key === 'PageUp') go(index - 1)
    else if (e.key === 'Home') go(0)
    else if (e.key === 'End') go(total - 1)
    else return
    e.preventDefault()
  }

  const onPointerDown = (e: PointerEvent) => {
    swipe.current = { x: e.clientX, y: e.clientY, moved: false }
  }
  const onPointerUp = (e: PointerEvent) => {
    const s = swipe.current
    swipe.current = null
    if (!s) return
    const dx = e.clientX - s.x
    const dy = e.clientY - s.y
    if (Math.abs(dx) > 40 && Math.abs(dx) > Math.abs(dy)) {
      go(index + (dx < 0 ? 1 : -1))
      return
    }
    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) return
    // A tap: links and buttons on the card keep working
    if ((e.target as HTMLElement).closest('a, button')) return
    const rect = e.currentTarget.getBoundingClientRect()
    go(index + (e.clientX - rect.left < rect.width * 0.35 ? -1 : 1))
  }

  const share = async () => {
    if (!card.current) return
    setBusy(true)
    try {
      const result = await shareCard(card.current, `${fileStem}-${index + 1}.png`, slides[index].label)
      if (result === 'downloaded') toast(y.downloaded, 'success')
    } catch {
      toast(y.shareFailed, 'error')
    } finally {
      setBusy(false)
    }
  }

  const slide = slides[index]
  return (
    <section
      aria-roledescription="carousel"
      aria-label={y.story}
      tabIndex={0}
      onKeyDown={onKey}
      className="mx-auto flex w-full flex-col items-center gap-4 outline-none"
      style={{ maxWidth: 'min(420px, calc((100dvh - var(--story-chrome, 19rem)) * 9 / 16))' }}
    >
      <div className="flex w-full items-center gap-2">
      <ol className="flex flex-1 gap-1" aria-label={y.story}>
        {slides.map((s, n) => (
          <li key={s.key} className="flex-1">
            <button
              type="button"
              aria-label={y.slide(n + 1, total)}
              aria-current={n === index ? 'step' : undefined}
              onClick={() => go(n)}
              className="block h-4 w-full py-1.5"
            >
              <span className={cn('block h-1 rounded-full transition-colors', n <= index ? 'bg-accent' : 'bg-line')} />
            </button>
          </li>
        ))}
      </ol>
      <Button variant="ghost" size="sm" className="-mr-2 sm:hidden" aria-label={y.share} loading={busy} onClick={share}>
        {!busy && <Share2 aria-hidden className="size-4.5" />}
      </Button>
      </div>

      <div
        role="group"
        aria-roledescription="slide"
        aria-label={`${y.slide(index + 1, total)}: ${slide.label}`}
        className="w-full cursor-pointer touch-pan-y select-none"
        onPointerDown={onPointerDown}
        onPointerUp={onPointerUp}
        onPointerCancel={() => (swipe.current = null)}
      >
        <div ref={card} key={slide.key} className="rise">
          {slide.card}
        </div>
      </div>
      <p aria-live="polite" className="sr-only">
        {y.slide(index + 1, total)}: {slide.label}
      </p>

      <div className="hidden w-full items-center justify-between gap-2 sm:flex">
        <Button variant="ghost" size="sm" aria-label={y.previous} disabled={index === 0} onClick={() => go(index - 1)}>
          <ChevronLeft aria-hidden className="size-5" />
        </Button>
        <Button variant="secondary" size="sm" loading={busy} onClick={share}>
          <Share2 aria-hidden className="size-4" />
          {busy ? y.sharing : y.share}
        </Button>
        <Button variant="ghost" size="sm" aria-label={y.next} disabled={index === total - 1} onClick={() => go(index + 1)}>
          <ChevronRight aria-hidden className="size-5" />
        </Button>
      </div>
      <p className="hidden text-[0.8125rem] text-faint sm:block">{y.hint}</p>
    </section>
  )
}
