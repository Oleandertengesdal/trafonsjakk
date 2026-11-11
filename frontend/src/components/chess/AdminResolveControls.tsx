import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { adminApi } from '../../api/endpoints'
import { invalidateResults } from '../../api/queryClient'
import type { Game, GameResult } from '../../api/types'
import { notation } from '../../i18n/format'
import { useI18n } from '../../i18n/I18nProvider'
import { useErrorText } from '../../i18n/useErrorText'
import { Button } from '../ui/Button'
import { Select } from '../ui/Field'
import { useToast } from '../ui/Toast'

/** Admin: confirm a game (optionally with a corrected result) or void it. */
export function AdminResolveControls({ game }: { game: Game }) {
  const { t } = useI18n()
  const errorText = useErrorText()
  const toast = useToast()
  const [result, setResult] = useState<GameResult>(game.result)
  const resolve = useMutation({
    mutationFn: (action: 'CONFIRM' | 'VOID') => adminApi.resolve(game.id, action, action === 'CONFIRM' ? result : undefined),
    onSuccess: async (_, action) => {
      await invalidateResults()
      toast(action === 'VOID' ? t.adminResolve.voided : game.status === 'CONFIRMED' ? t.adminResolve.corrected : t.adminResolve.confirmed)
    },
    onError: (e) => toast(errorText(e), 'error'),
  })
  if (game.status === 'VOIDED') return null
  const unchanged = game.status === 'CONFIRMED' && result === game.result
  return (
    <div className="flex flex-wrap items-center gap-2">
      <div className="w-48">
        <Select aria-label={t.adminResolve.result} value={result} onChange={(e) => setResult(e.target.value as GameResult)} className="h-8 text-sm">
          {(['WHITE_WIN', 'DRAW', 'BLACK_WIN'] as const).map((r) => (
            <option key={r} value={r}>
              {notation[r]} {t.result[r]}
            </option>
          ))}
        </Select>
      </div>
      <Button size="sm" onClick={() => resolve.mutate('CONFIRM')} disabled={unchanged || resolve.isPending} loading={resolve.isPending && resolve.variables === 'CONFIRM'}>
        {game.status === 'CONFIRMED' ? t.adminResolve.correct : t.adminResolve.confirm}
      </Button>
      <Button size="sm" variant="danger" onClick={() => resolve.mutate('VOID')} disabled={resolve.isPending} loading={resolve.isPending && resolve.variables === 'VOID'}>
        {t.adminResolve.void}
      </Button>
    </div>
  )
}
