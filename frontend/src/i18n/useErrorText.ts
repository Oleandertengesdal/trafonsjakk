import { useCallback } from 'react'
import { ApiError } from '../api/client'
import { useI18n } from './I18nProvider'

/** Turns any thrown error into a message in the current language. */
export function useErrorText() {
  const { t } = useI18n()
  return useCallback(
    (e: unknown): string => {
      if (e instanceof ApiError) {
        if (e.status === 0) return typeof navigator !== 'undefined' && navigator.onLine === false ? t.errors.offline : t.errors.network
        const entry = e.code ? t.errors.codes[e.code] : undefined
        if (typeof entry === 'function') return entry(e.params)
        if (typeof entry === 'string') return entry
        if (e.status === 502 || e.status === 503 || e.status === 504) return t.errors.waking
        if (t.errors.http[e.status]) return t.errors.http[e.status]
        return t.errors.generic
      }
      return t.errors.generic
    },
    [t],
  )
}

/** Server-side field validation → messages in the current language, keyed by field name. */
export function useFieldErrors() {
  const { t } = useI18n()
  return useCallback(
    (e: unknown): Record<string, string> => {
      if (!(e instanceof ApiError)) return {}
      const out: Record<string, string> = {}
      for (const field of Object.keys(e.fieldErrors)) {
        const code = e.fieldCodes[field]
        out[field] = (code && t.errors.field[code]) || t.errors.field.fallback
      }
      return out
    },
    [t],
  )
}
