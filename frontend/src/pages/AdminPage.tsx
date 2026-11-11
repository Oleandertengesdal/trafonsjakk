import { Link } from 'react-router'
import { useMutation, useQuery } from '@tanstack/react-query'
import { RefreshCw, ShieldCheck } from 'lucide-react'
import { adminApi } from '../api/endpoints'
import { invalidateResults, queryClient } from '../api/queryClient'
import type { Game, Role } from '../api/types'
import { useAuth } from '../auth/AuthProvider'
import { AdminResolveControls } from '../components/chess/AdminResolveControls'
import { ResultMark } from '../components/chess/ResultMark'
import { PageHeader } from '../components/layout/PageHeader'
import { Button } from '../components/ui/Button'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorNotice } from '../components/ui/ErrorNotice'
import { Panel } from '../components/ui/Panel'
import { SkeletonRows } from '../components/ui/Skeleton'
import { useToast } from '../components/ui/Toast'
import { useI18n } from '../i18n/I18nProvider'
import { useErrorText } from '../i18n/useErrorText'

export function AdminPage() {
  const { me } = useAuth()
  const { t, f } = useI18n()
  const a = t.admin
  const errorText = useErrorText()
  const toast = useToast()
  const disputed = useQuery({ queryKey: ['disputed'], queryFn: adminApi.disputed })
  const users = useQuery({ queryKey: ['adminUsers'], queryFn: adminApi.users })

  const updateUser = useMutation({
    mutationFn: ({ id, body }: { id: number; body: { role?: Role; enabled?: boolean } }) => adminApi.updateUser(id, body),
    onSuccess: () => {
      toast(a.userUpdated)
      void queryClient.invalidateQueries({ queryKey: ['adminUsers'] })
      void queryClient.invalidateQueries({ queryKey: ['users'] })
    },
    onError: (e) => toast(errorText(e), 'error'),
  })
  const recompute = useMutation({
    mutationFn: adminApi.recompute,
    onSuccess: async (r) => {
      await invalidateResults()
      toast(a.recomputed(r.games, r.players, r.millis))
    },
    onError: (e) => toast(errorText(e), 'error'),
  })

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={a.title}
        description={a.description}
        action={
          <Button variant="secondary" onClick={() => recompute.mutate()} loading={recompute.isPending}>
            <RefreshCw aria-hidden className="size-4.5" /> {a.recompute}
          </Button>
        }
      />

      <Panel title={a.disputes}>
        {disputed.isPending ? (
          <SkeletonRows rows={2} />
        ) : disputed.isError ? (
          <ErrorNotice error={disputed.error} onRetry={() => disputed.refetch()} />
        ) : disputed.data.length === 0 ? (
          <EmptyState icon={<ShieldCheck />} title={a.noDisputesTitle}>
            {a.noDisputesBody}
          </EmptyState>
        ) : (
          <ul className="divide-y divide-line">
            {disputed.data.map((g) => (
              <DisputeRow key={g.id} game={g} />
            ))}
          </ul>
        )}
      </Panel>

      <Panel title={a.members}>
        {users.isPending ? (
          <SkeletonRows rows={4} />
        ) : users.isError ? (
          <ErrorNotice error={users.error} onRetry={() => users.refetch()} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-[0.9375rem]">
              <thead>
                <tr className="text-left text-[0.8125rem] text-muted">
                  <th scope="col" className="px-5 py-2 font-medium">{a.member}</th>
                  <th scope="col" className="hidden px-3 py-2 font-medium sm:table-cell">{a.joined}</th>
                  <th scope="col" className="px-3 py-2 text-right font-medium">{a.games}</th>
                  <th scope="col" className="px-5 py-2 text-right font-medium">{a.access}</th>
                </tr>
              </thead>
              <tbody>
                {users.data.map((u) => {
                  const pending = updateUser.isPending && updateUser.variables?.id === u.id
                  return (
                    <tr key={u.id} className="border-t border-line">
                      <td className="px-5 py-3">
                        <Link to={`/players/${u.username}`} className="font-medium hover:text-accent">
                          {u.realName}
                        </Link>
                        <span className="block text-[0.8125rem] text-muted">
                          {u.username}
                          {u.role === 'ADMIN' && `, ${t.common.admin}`}
                          {!u.enabled && a.disabledSuffix}
                        </span>
                      </td>
                      <td className="hidden px-3 py-3 text-muted sm:table-cell">{f.date(u.createdAt)}</td>
                      <td className="px-3 py-3 text-right tabular-nums">{u.ratedGames}</td>
                      <td className="px-5 py-3">
                        <div className="flex justify-end gap-2">
                          {u.id === me!.id ? (
                            <span className="text-[0.8125rem] text-faint">{t.common.you}</span>
                          ) : (
                            <>
                              <Button size="sm" variant="ghost" disabled={pending} onClick={() => updateUser.mutate({ id: u.id, body: { role: u.role === 'ADMIN' ? 'USER' : 'ADMIN' } })}>
                                {u.role === 'ADMIN' ? a.removeAdmin : a.makeAdmin}
                              </Button>
                              <Button size="sm" variant={u.enabled ? 'danger' : 'secondary'} disabled={pending} onClick={() => updateUser.mutate({ id: u.id, body: { enabled: !u.enabled } })}>
                                {u.enabled ? a.disable : a.enable}
                              </Button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
    </div>
  )
}

function DisputeRow({ game: g }: { game: Game }) {
  const { t, f } = useI18n()
  return (
    <li className="flex flex-col gap-3 px-5 py-4 lg:flex-row lg:items-center lg:justify-between">
      <div className="min-w-0">
        <Link to={`/games/${g.id}`} className="flex flex-wrap items-center gap-x-2 font-medium hover:text-accent">
          {g.white.realName} <ResultMark result={g.result} /> {g.black.realName}
        </Link>
        <p className="text-[0.8125rem] text-muted">{t.admin.disputeLine(g.reportedBy.realName, f.date(g.playedAt), g.disputeReason ?? '')}</p>
      </div>
      <AdminResolveControls game={g} />
    </li>
  )
}
