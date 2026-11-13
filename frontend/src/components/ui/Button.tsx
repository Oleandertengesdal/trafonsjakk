import { forwardRef, type AnchorHTMLAttributes, type ButtonHTMLAttributes } from 'react'
import { Link, type LinkProps } from 'react-router'
import { Loader2 } from 'lucide-react'
import { cn } from '../../lib/cn'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'
type Size = 'sm' | 'md' | 'lg'

const variants: Record<Variant, string> = {
  primary: 'bg-brand text-on-accent shadow-[inset_0_-2px_0_rgb(0_0_0/0.16)] hover:bg-brand-hover active:translate-y-px',
  secondary: 'bg-surface text-ink border border-line hover:border-faint active:translate-y-px',
  ghost: 'text-muted hover:text-ink hover:bg-raised',
  danger: 'bg-transparent text-accent border border-accent/45 hover:bg-accent-soft',
}

const sizes: Record<Size, string> = {
  sm: 'h-8 px-3 text-sm gap-1.5',
  md: 'h-10 px-4 text-[0.9375rem] gap-2',
  lg: 'h-12 px-5 text-base gap-2',
}

const base =
  'inline-flex select-none items-center justify-center rounded-control font-medium transition-[transform,background-color,border-color,color] duration-150 disabled:cursor-not-allowed disabled:opacity-50'

function buttonClass(variant: Variant = 'primary', size: Size = 'md', className?: string) {
  return cn(base, variants[variant], sizes[size], className)
}

/** A router link that looks like a button (never nest a <button> inside a link). */
export function ButtonLink({ variant, size, className, ...rest }: LinkProps & { variant?: Variant; size?: Size }) {
  return <Link className={buttonClass(variant, size, className)} {...rest} />
}

/** A plain link (full page load) that looks like a button: for places without a router, like the crash page. */
export function ButtonAnchor({ variant, size, className, ...rest }: AnchorHTMLAttributes<HTMLAnchorElement> & { variant?: Variant; size?: Size }) {
  return <a className={buttonClass(variant, size, className)} {...rest} />
}

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  loading?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', loading, disabled, className, children, ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      disabled={disabled || loading}
      className={buttonClass(variant, size, className)}
      {...rest}
    >
      {loading && <Loader2 aria-hidden className="size-4 animate-spin" />}
      {children}
    </button>
  )
})
