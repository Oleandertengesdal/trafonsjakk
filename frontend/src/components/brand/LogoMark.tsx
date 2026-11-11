import { cn } from '../../lib/cn'

/** A pawn in an arched window: the Trafonsjakk mark. */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" aria-hidden className={cn('shrink-0', className)}>
      <path d="M4 30V14a12 12 0 0 1 24 0v16Z" fill="var(--c-brand)" />
      <circle cx="16" cy="12.2" r="3.2" fill="var(--c-on-accent)" />
      <path d="M13.3 16.4h5.4l-.8 2.1c1.4 1.7 2.1 3.6 2.2 5.6h-8.2c.1-2 .8-3.9 2.2-5.6Z" fill="var(--c-on-accent)" />
      <rect x="10.6" y="24.6" width="10.8" height="2.4" rx="1.2" fill="var(--c-on-accent)" />
    </svg>
  )
}

export function Wordmark({ className }: { className?: string }) {
  return <span className={cn('font-display font-bold tracking-[-0.02em]', className)}>Trafonsjakk</span>
}
