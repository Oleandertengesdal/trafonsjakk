import { useEffect, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Bell, BellOff } from 'lucide-react'
import { ApiError } from '../../api/client'
import { pushApi } from '../../api/endpoints'
import { pushState, turnOff, turnOn, type PushState } from '../../lib/push'
import { useI18n } from '../../i18n/I18nProvider'
import { useErrorText } from '../../i18n/useErrorText'
import { Button } from '../ui/Button'
import { Panel } from '../ui/Panel'
import { Skeleton } from '../ui/Skeleton'
import { useToast } from '../ui/Toast'

/** Push notifications on this device: "Ola logged a game against you – confirm?" */
export function NotificationsPanel() {
  const { t } = useI18n()
  const n = t.notifications
  const toast = useToast()
  const errorText = useErrorText()
  const [state, setState] = useState<PushState | null>(null)

  useEffect(() => {
    let current = true
    pushState().then(
      (s) => current && setState(s),
      () => current && setState('unsupported'),
    )
    return () => {
      current = false
    }
  }, [])

  const change = useMutation({
    mutationFn: (on: boolean) => (on ? turnOn() : turnOff()),
    onSuccess: (s) => {
      setState(s)
      if (s === 'on') toast(n.turnedOn, 'success')
    },
    // The browser's own push service can refuse too (private windows, no connection)
    onError: (e) => toast(e instanceof ApiError ? errorText(e) : n.failed, 'error'),
  })
  const test = useMutation({
    mutationFn: pushApi.test,
    onSuccess: ({ sent }) => toast(sent > 0 ? n.testSent(sent) : n.testFailed, sent > 0 ? 'success' : 'error'),
    onError: (e) => toast(errorText(e), 'error'),
  })

  let body
  if (state === null) body = <Skeleton className="h-10 w-64" />
  else if (state === 'on' || state === 'off')
    body = (
      <>
        <p className="text-[0.9375rem] text-muted">{state === 'on' ? n.onBody : n.offBody}</p>
        <div className="flex flex-wrap gap-2">
          <Button variant={state === 'on' ? 'secondary' : 'primary'} loading={change.isPending} onClick={() => change.mutate(state === 'off')}>
            {state === 'on' ? <BellOff aria-hidden className="size-4.5" /> : <Bell aria-hidden className="size-4.5" />}
            {state === 'on' ? n.turnOff : n.turnOn}
          </Button>
          {state === 'on' && (
            <Button variant="ghost" loading={test.isPending} onClick={() => test.mutate()}>
              {n.test}
            </Button>
          )}
        </div>
      </>
    )
  else body = <p className="text-[0.9375rem] text-muted">{n.why[state]}</p>

  return (
    <Panel title={n.title}>
      <div className="flex flex-col items-start gap-4 px-5 pt-1 pb-5" aria-live="polite">
        {body}
      </div>
    </Panel>
  )
}
