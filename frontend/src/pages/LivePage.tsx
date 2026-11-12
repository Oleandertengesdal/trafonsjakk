import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Radio, Tv } from 'lucide-react'
import { liveApi, usersApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthProvider'
import { LiveRow } from '../components/chess/Live'
import { PlayerSelect } from '../components/chess/PlayerSelect'
import { PageHeader } from '../components/layout/PageHeader'
import { Button, ButtonLink } from '../components/ui/Button'
import { EmptyState } from '../components/ui/EmptyState'
import { Field } from '../components/ui/Field'
import { Panel } from '../components/ui/Panel'
import { Skeleton } from '../components/ui/Skeleton'
import { useToast } from '../components/ui/Toast'
import { useI18n } from '../i18n/I18nProvider'
import { useErrorText } from '../i18n/useErrorText'

/** Games being played right now, and starting a new one. */
export function LivePage() {
  const { t } = useI18n()
  const l = t.live
  const { me } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const errorText = useErrorText()
  const running = useQuery({ queryKey: ['live'], queryFn: liveApi.running, refetchInterval: 10_000 })
  const players = useQuery({ queryKey: ['users'], queryFn: usersApi.list })
  const [white, setWhite] = useState<number | ''>(me!.id)
  const [black, setBlack] = useState<number | ''>('')

  const start = useMutation({
    mutationFn: () => liveApi.start(Number(white), Number(black)),
    onSuccess: (g) => navigate(`/live/${g.id}`),
    onError: (e) => toast(errorText(e), 'error'),
  })

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={l.title}
        description={l.description}
        action={
          <ButtonLink to="/tv" variant="secondary" title={l.tvHint}>
            <Tv aria-hidden className="size-4.5" />
            {l.tvMode}
          </ButtonLink>
        }
      />

      <Panel title={l.running}>
        {running.isPending ? (
          <div className="px-5 pt-2 pb-5">
            <Skeleton className="h-16 w-full" />
          </div>
        ) : running.data && running.data.length > 0 ? (
          <ul className="divide-y divide-line px-5 pb-2">
            {running.data.map((g) => (
              <LiveRow key={g.id} game={g} />
            ))}
          </ul>
        ) : (
          <div className="px-5 pb-5">
            <EmptyState icon={<Radio aria-hidden className="size-6" />} title={l.noneTitle}>
              {l.noneBody}
            </EmptyState>
          </div>
        )}
      </Panel>

      <Panel title={l.startTitle}>
        <form
          className="flex flex-col gap-4 px-5 pt-1 pb-5"
          onSubmit={(e: FormEvent) => {
            e.preventDefault()
            if (white && black) start.mutate()
          }}
        >
          <p className="text-[0.9375rem] text-muted">{l.startHint}</p>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label={l.white}>
              {(p) => <PlayerSelect {...p} players={players.data ?? []} value={white} onChange={setWhite} exclude={black} placeholder={l.choosePlayer} />}
            </Field>
            <Field label={l.black}>
              {(p) => <PlayerSelect {...p} players={players.data ?? []} value={black} onChange={setBlack} exclude={white} placeholder={l.choosePlayer} />}
            </Field>
          </div>
          <div>
            <Button type="submit" size="lg" loading={start.isPending} disabled={!white || !black}>
              {l.start}
            </Button>
          </div>
        </form>
      </Panel>
    </div>
  )
}
