import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Copy, Ticket } from 'lucide-react'
import { invitesApi } from '../api/endpoints'
import { queryClient } from '../api/queryClient'
import { useAuth } from '../auth/AuthProvider'
import { PageHeader } from '../components/layout/PageHeader'
import { Button } from '../components/ui/Button'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorNotice } from '../components/ui/ErrorNotice'
import { Panel } from '../components/ui/Panel'
import { SkeletonRows } from '../components/ui/Skeleton'
import { useToast } from '../components/ui/Toast'
import { useI18n } from '../i18n/I18nProvider'
import { useErrorText } from '../i18n/useErrorText'
import { cn } from '../lib/cn'

export function InvitesPage() {
  const { me } = useAuth()
  const { t, f } = useI18n()
  const inv = t.invites
  const errorText = useErrorText()
  const toast = useToast()
  const q = useQuery({ queryKey: ['invites'], queryFn: invitesApi.list })
  const [fresh, setFresh] = useState<{ code: string; expiresAt: string } | null>(null)

  const create = useMutation({
    mutationFn: invitesApi.create,
    onSuccess: (r) => {
      setFresh(r)
      void queryClient.invalidateQueries({ queryKey: ['invites'] })
    },
    onError: (e) => toast(errorText(e), 'error'),
  })
  const revoke = useMutation({
    mutationFn: invitesApi.revoke,
    onSuccess: () => {
      toast(inv.revoked)
      void queryClient.invalidateQueries({ queryKey: ['invites'] })
    },
    onError: (e) => toast(errorText(e), 'error'),
  })

  const link = fresh ? `${window.location.origin}/register?code=${encodeURIComponent(fresh.code)}` : ''
  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text)
      toast(t.common.copied)
    } catch {
      toast(t.common.copyFailed, 'error')
    }
  }

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title={inv.title}
        description={inv.description}
        action={
          <Button onClick={() => create.mutate()} loading={create.isPending}>
            <Ticket aria-hidden className="size-4.5" /> {inv.create}
          </Button>
        }
      />

      {fresh && (
        <Panel className="rise mb-6 border-accent/50 p-5">
          <p className="text-[0.9375rem] text-muted">{inv.freshIntro}</p>
          <p className="mt-2 font-display text-[2rem] font-bold tracking-[0.06em] break-all select-all">{fresh.code}</p>
          <div className="mt-4 flex flex-wrap gap-2">
            <Button variant="secondary" size="sm" onClick={() => copy(fresh.code)}>
              <Copy aria-hidden className="size-4" /> {inv.copyCode}
            </Button>
            <Button variant="secondary" size="sm" onClick={() => copy(link)}>
              <Copy aria-hidden className="size-4" /> {inv.copyLink}
            </Button>
          </div>
          <p className="mt-3 text-[0.8125rem] text-faint">{inv.expires(f.date(fresh.expiresAt))}</p>
        </Panel>
      )}

      <Panel title={me!.role === 'ADMIN' ? inv.allCodes : inv.yourCodes}>
        {q.isPending ? (
          <SkeletonRows rows={3} />
        ) : q.isError ? (
          <ErrorNotice error={q.error} onRetry={() => q.refetch()} />
        ) : q.data.length === 0 ? (
          <EmptyState icon={<Ticket />} title={inv.emptyTitle}>
            {inv.emptyBody}
          </EmptyState>
        ) : (
          <ul className="divide-y divide-line">
            {q.data.map((i) => (
              <li key={i.id} className="flex flex-wrap items-center justify-between gap-3 px-5 py-3.5">
                <div className="min-w-0">
                  <p className={cn('font-medium', i.status === 'ACTIVE' ? 'text-win' : 'text-muted')}>{inv.status[i.status]}</p>
                  <p className="text-[0.8125rem] text-muted">
                    {i.usedBy && i.usedAt
                      ? inv.joined(i.usedBy.realName, f.relative(i.usedAt))
                      : i.status === 'ACTIVE'
                        ? inv.expiresIn(f.relative(i.expiresAt))
                        : inv.created(f.date(i.createdAt))}
                    {me!.role === 'ADMIN' && i.createdBy && inv.createdBy(i.createdBy.realName)}
                  </p>
                </div>
                {i.status === 'ACTIVE' && (
                  <Button variant="ghost" size="sm" onClick={() => revoke.mutate(i.id)} loading={revoke.isPending && revoke.variables === i.id}>
                    {inv.revoke}
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </Panel>
    </div>
  )
}
