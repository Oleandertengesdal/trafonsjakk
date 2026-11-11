import { type Lang, useI18n } from '../../i18n/I18nProvider'
import { cn } from '../../lib/cn'

/** NO / EN toggle. Remembered on this device. */
export function LanguageSwitch({ className }: { className?: string }) {
  const { lang, setLang, t } = useI18n()
  const options: { value: Lang; short: string; label: string }[] = [
    { value: 'nb', short: 'NO', label: t.language.nb },
    { value: 'en', short: 'EN', label: t.language.en },
  ]
  return (
    <div role="group" aria-label={t.language.label} className={cn('inline-flex rounded-full border border-line bg-surface p-0.5 text-[0.75rem] font-semibold', className)}>
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          lang={o.value}
          aria-pressed={lang === o.value}
          aria-label={o.label}
          title={o.label}
          onClick={() => setLang(o.value)}
          className={cn('rounded-full px-2.5 py-1 transition-colors', lang === o.value ? 'bg-ink text-page' : 'text-muted hover:text-ink')}
        >
          {o.short}
        </button>
      ))}
    </div>
  )
}
