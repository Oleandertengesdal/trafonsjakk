import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { gamesApi } from '../../api/endpoints'
import { invalidateResults } from '../../api/queryClient'
import type { Game } from '../../api/types'
import { useI18n } from '../../i18n/I18nProvider'
import { useErrorText } from '../../i18n/useErrorText'
import { Button } from '../ui/Button'
import { Dialog } from '../ui/Dialog'
import { Field, Textarea } from '../ui/Field'
import { useToast } from '../ui/Toast'

/** Confirm / dispute / withdraw buttons for a pending game. Renders nothing if the viewer can't act. */
export function GameActions({ game, size = 'md' }: { game: Game; size?: 'sm' | 'md' }) {
  const { t } = useI18n()
  const errorText = useErrorText()
  const toast = useToast()
  const [disputeOpen, setDisputeOpen] = useState(false)
  const [reason, setReason] = useState('')

  const onError = (e: unknown) => toast(errorText(e), 'error')
  const confirm = useMutation({
    mutationFn: () => gamesApi.confirm(game.id),
    onSuccess: async (g) => {
      await invalidateResults()
      toast(g.ratingChange ? t.gameActions.confirmedRated : t.gameActions.confirmed)
    },
    onError,
  })
  const dispute = useMutation({
    mutationFn: () => gamesApi.dispute(game.id, reason.trim()),
    onSuccess: async () => {
      setDisputeOpen(false)
      setReason('')
      await invalidateResults()
      toast(t.gameActions.disputed)
    },
    onError,
  })
  const withdraw = useMutation({
    mutationFn: () => gamesApi.withdraw(game.id),
    onSuccess: async () => {
      await invalidateResults()
      toast(t.gameActions.withdrawn)
    },
    onError,
  })

  if (!game.canConfirm && !game.canWithdraw) return null
  const busy = confirm.isPending || dispute.isPending || withdraw.isPending

  return (
    <div className="flex flex-wrap gap-2">
      {game.canConfirm && (
        <Button size={size} onClick={() => confirm.mutate()} loading={confirm.isPending} disabled={busy}>
          {t.gameActions.confirm}
        </Button>
      )}
      {game.canDispute && (
        <Button size={size} variant="secondary" onClick={() => setDisputeOpen(true)} disabled={busy}>
          {t.gameActions.dispute}
        </Button>
      )}
      {game.canWithdraw && !game.canConfirm && (
        <Button size={size} variant="ghost" onClick={() => withdraw.mutate()} loading={withdraw.isPending} disabled={busy}>
          {t.gameActions.withdraw}
        </Button>
      )}
      <Dialog open={disputeOpen} onClose={() => setDisputeOpen(false)} title={t.gameActions.disputeTitle}>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            if (reason.trim().length >= 5) dispute.mutate()
          }}
          className="flex flex-col gap-4"
        >
          <Field label={t.gameActions.disputeLabel} hint={t.gameActions.disputeHint}>
            {(p) => <Textarea {...p} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} autoFocus />}
          </Field>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="ghost" onClick={() => setDisputeOpen(false)}>
              {t.common.cancel}
            </Button>
            <Button type="submit" variant="danger" loading={dispute.isPending} disabled={reason.trim().length < 5}>
              {t.gameActions.disputeSubmit}
            </Button>
          </div>
        </form>
      </Dialog>
    </div>
  )
}
