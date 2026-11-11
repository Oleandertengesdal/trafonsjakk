import { useEffect, useRef, type ReactNode } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { House, ListOrdered, LogOut, Plus, Settings, Shield, Ticket, TrendingUp, Trophy, UserRound } from 'lucide-react'
import { gamesApi } from '../../api/endpoints'
import { useAuth } from '../../auth/AuthProvider'
import { useI18n } from '../../i18n/I18nProvider'
import { cn } from '../../lib/cn'
import { LogoMark, Wordmark } from '../brand/LogoMark'
import { Avatar } from '../chess/Avatar'
import { Rating } from '../chess/PlayerLink'
import { LanguageSwitch } from '../ui/LanguageSwitch'

type NavItem = { to: string; label: string; icon: typeof House; end?: boolean; badge?: number }

export function AppShell() {
  const { me, logout } = useAuth()
  const { t } = useI18n()
  const pending = useQuery({ queryKey: ['pending'], queryFn: gamesApi.pending, refetchInterval: 60_000 })
  const pendingCount = pending.data?.length ?? 0
  const { pathname } = useLocation()
  useEffect(() => {
    window.scrollTo({ top: 0 })
  }, [pathname])
  if (!me) return null

  const primaryNav: NavItem[] = [
    { to: '/', label: t.nav.home, icon: House, end: true, badge: pendingCount },
    { to: '/games', label: t.nav.games, icon: ListOrdered },
    { to: '/leaderboard', label: t.nav.ratings, icon: TrendingUp },
    { to: '/tournaments', label: t.nav.tournaments, icon: Trophy },
  ]
  const secondaryNav: NavItem[] = [
    { to: `/players/${me.username}`, label: t.nav.myProfile, icon: UserRound },
    { to: '/invites', label: t.nav.invites, icon: Ticket },
    { to: '/settings', label: t.nav.settings, icon: Settings },
    ...(me.role === 'ADMIN' ? [{ to: '/admin', label: t.nav.admin, icon: Shield }] : []),
  ]

  return (
    <div className="min-h-dvh md:grid md:grid-cols-[16rem_1fr]">
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus:z-50 focus:rounded-control focus:bg-surface focus:px-3 focus:py-2">
        {t.nav.skipToContent}
      </a>

      {/* Desktop sidebar */}
      <div className="hidden border-r border-line bg-surface md:block">
        <aside className="sticky top-0 flex h-dvh flex-col px-4 py-5">
          <Link to="/" className="flex items-center gap-2.5 px-2">
            <LogoMark className="size-8" />
            <Wordmark className="text-[1.3rem]" />
          </Link>

          <Link
            to="/log"
            className="mt-7 flex h-12 items-center justify-center gap-2 rounded-full bg-brand font-semibold text-on-accent shadow-[inset_0_-3px_0_rgb(0_0_0/0.18)] transition-[background-color,transform] hover:bg-brand-hover active:translate-y-px"
          >
            <Plus aria-hidden className="size-5" />
            {t.nav.logGame}
          </Link>

          <nav aria-label={t.nav.main} className="mt-6 flex flex-col gap-0.5">
            {primaryNav.map((item) => (
              <SideLink key={item.to} {...item} badgeLabel={t.nav.waitingForYou} />
            ))}
          </nav>
          <nav aria-label={t.nav.account} className="mt-6 flex flex-col gap-0.5 border-t border-line pt-6">
            {secondaryNav.map((item) => (
              <SideLink key={item.to} {...item} badgeLabel={t.nav.waitingForYou} />
            ))}
          </nav>

          <div className="mt-auto flex flex-col gap-3">
            <LanguageSwitch className="self-start" />
            <div className="flex items-center gap-3 rounded-control px-1 py-1">
              <Avatar name={me.realName} seed={me.username} />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{me.realName}</p>
                <p className="text-[0.8125rem] text-muted">
                  <Rating value={me.rating} provisional={me.provisional} />
                </p>
              </div>
              <button
                type="button"
                onClick={() => void logout()}
                className="rounded-md p-2 text-muted hover:bg-raised hover:text-ink"
                aria-label={t.nav.logOut}
                title={t.nav.logOut}
              >
                <LogOut className="size-4.5" />
              </button>
            </div>
          </div>
        </aside>
      </div>

      {/* Mobile top bar */}
      <header className="sticky top-0 z-30 flex items-center justify-between border-b border-line bg-page/90 px-4 py-3 backdrop-blur md:hidden">
        <Link to="/" className="flex items-center gap-2">
          <LogoMark className="size-7" />
          <Wordmark className="text-lg" />
        </Link>
        <AccountMenu label={t.nav.accountMenu}>
          {secondaryNav.map((item) => (
            <Link key={item.to} to={item.to} className="flex items-center gap-3 rounded-md px-3 py-2.5 hover:bg-raised">
              <item.icon aria-hidden className="size-4.5 text-muted" />
              {item.label}
            </Link>
          ))}
          <button type="button" onClick={() => void logout()} className="flex w-full items-center gap-3 rounded-md px-3 py-2.5 text-left hover:bg-raised">
            <LogOut aria-hidden className="size-4.5 text-muted" />
            {t.nav.logOut}
          </button>
          <div className="mt-1 border-t border-line px-3 pt-3 pb-1.5">
            <LanguageSwitch />
          </div>
        </AccountMenu>
      </header>

      <main id="main" className="min-w-0 px-4 pt-5 pb-28 sm:px-6 md:px-10 md:pt-9 md:pb-16">
        <div className="mx-auto max-w-[68rem]">
          <Outlet />
        </div>
      </main>

      {/* Mobile tab bar with the red "Log game" button in the middle */}
      <nav
        aria-label={t.nav.main}
        className="fixed inset-x-0 bottom-0 z-30 grid grid-cols-5 items-end border-t border-line bg-surface/95 px-1 pt-1.5 pb-[max(0.5rem,env(safe-area-inset-bottom))] backdrop-blur md:hidden"
      >
        <TabLink {...primaryNav[0]} badgeLabel={t.nav.waitingForYou} />
        <TabLink {...primaryNav[1]} badgeLabel={t.nav.waitingForYou} />
        <Link to="/log" className="flex flex-col items-center gap-1">
          <span className="-mt-6 flex size-14 items-center justify-center rounded-full border-4 border-page bg-brand text-on-accent shadow-[inset_0_-3px_0_rgb(0_0_0/0.2)]">
            <Plus aria-hidden className="size-6" />
          </span>
          <span className="max-w-full truncate text-[0.6875rem] text-muted">{t.nav.logGame}</span>
        </Link>
        <TabLink {...primaryNav[2]} badgeLabel={t.nav.waitingForYou} />
        <TabLink {...primaryNav[3]} badgeLabel={t.nav.waitingForYou} />
      </nav>
    </div>
  )
}

function SideLink({ to, label, icon: Icon, end, badge = 0, badgeLabel }: NavItem & { badgeLabel: (n: number) => string }) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        cn(
          'flex items-center gap-3 rounded-control px-3 py-2 text-[0.9375rem] transition-colors',
          isActive ? 'bg-accent-soft font-medium text-accent' : 'text-muted hover:bg-raised hover:text-ink',
        )
      }
    >
      <Icon aria-hidden className="size-4.5" />
      <span className="flex-1">{label}</span>
      {badge > 0 && (
        <span className="rounded-full bg-[#8a6416] px-1.5 text-xs font-semibold text-[#fff9f2]" aria-label={badgeLabel(badge)}>
          {badge}
        </span>
      )}
    </NavLink>
  )
}

function TabLink({ to, label, icon: Icon, end, badge = 0, badgeLabel }: NavItem & { badgeLabel: (n: number) => string }) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) => cn('relative flex min-w-0 flex-col items-center gap-1 py-1 text-[0.6875rem]', isActive ? 'text-accent' : 'text-muted')}
    >
      <Icon aria-hidden className="size-5.5" />
      <span className="max-w-full truncate">{label}</span>
      {badge > 0 && <span role="img" className="absolute top-0.5 right-[calc(50%-1rem)] size-2 rounded-full bg-[#b8862a]" aria-label={badgeLabel(badge)} />}
    </NavLink>
  )
}

function AccountMenu({ children, label }: { children: ReactNode; label: string }) {
  const { me } = useAuth()
  const ref = useRef<HTMLDetailsElement>(null)
  const { pathname } = useLocation()
  useEffect(() => {
    if (ref.current) ref.current.open = false
  }, [pathname])
  // Close when tapping outside the menu
  useEffect(() => {
    const onDown = (e: PointerEvent) => {
      const d = ref.current
      if (d?.open && !d.contains(e.target as Node)) d.open = false
    }
    document.addEventListener('pointerdown', onDown)
    return () => document.removeEventListener('pointerdown', onDown)
  }, [])
  if (!me) return null
  return (
    <details ref={ref} className="relative">
      <summary className="list-none rounded-full [&::-webkit-details-marker]:hidden" aria-label={label}>
        <Avatar name={me.realName} seed={me.username} size="sm" />
      </summary>
      <div className="absolute right-0 z-40 mt-2 w-60 rounded-panel border border-line bg-surface p-1.5 shadow-[0_16px_40px_-12px_rgb(0_0_0/0.35)]">{children}</div>
    </details>
  )
}
