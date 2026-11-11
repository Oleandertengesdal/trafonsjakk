import { ButtonLink } from '../components/ui/Button'
import { useI18n } from '../i18n/I18nProvider'

export function NotFoundPage() {
  const { t } = useI18n()
  return (
    <div className="flex flex-col items-center py-20 text-center">
      <p className="font-display text-6xl font-bold text-faint" aria-hidden>
        0–1
      </p>
      <h1 className="mt-4 font-display text-2xl font-bold">{t.notFound.title}</h1>
      <p className="mt-2 text-muted">{t.notFound.body}</p>
      <ButtonLink to="/" variant="secondary" className="mt-6">
        {t.notFound.back}
      </ButtonLink>
    </div>
  )
}
