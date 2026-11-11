import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { en, type Dict } from './en'
import { nb } from './nb'
import { makeFormatters, type Formatters } from './format'

export type Lang = 'nb' | 'en'

const DICTS: Record<Lang, Dict> = { nb, en }
const KEY = 'tf-lang'
const DEFAULT_LANG: Lang = 'nb'

function readStoredLang(): Lang {
  try {
    const v = localStorage.getItem(KEY)
    return v === 'en' || v === 'nb' ? v : DEFAULT_LANG
  } catch {
    return DEFAULT_LANG
  }
}

interface I18nValue {
  lang: Lang
  setLang: (l: Lang) => void
  t: Dict
  f: Formatters
}

const I18nContext = createContext<I18nValue | null>(null)

export function I18nProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(readStoredLang)

  useEffect(() => {
    document.documentElement.lang = lang === 'nb' ? 'nb' : 'en'
  }, [lang])

  const setLang = useCallback((l: Lang) => {
    setLangState(l)
    try {
      localStorage.setItem(KEY, l)
    } catch {
      /* storage unavailable: choice lasts for this visit */
    }
  }, [])

  const value = useMemo<I18nValue>(() => ({ lang, setLang, t: DICTS[lang], f: makeFormatters(lang) }), [lang, setLang])
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useI18n() {
  const ctx = useContext(I18nContext)
  if (!ctx) throw new Error('useI18n must be used inside I18nProvider')
  return ctx
}
