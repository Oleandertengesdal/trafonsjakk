import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { I18nProvider, useI18n } from '../../i18n/I18nProvider'
import { LanguageSwitch } from './LanguageSwitch'

function Probe() {
  const { t } = useI18n()
  return <p data-testid="probe">{t.common.cancel}</p>
}

function setup() {
  render(
    <I18nProvider>
      <LanguageSwitch />
      <Probe />
    </I18nProvider>,
  )
  return { no: screen.getByRole('button', { name: 'Norsk' }), en: screen.getByRole('button', { name: 'English' }) }
}

describe('LanguageSwitch', () => {
  it('starts in Norwegian', () => {
    const { no, en } = setup()
    expect(no).toHaveAttribute('aria-pressed', 'true')
    expect(en).toHaveAttribute('aria-pressed', 'false')
    expect(document.documentElement.lang).toBe('nb')
    expect(screen.getByTestId('probe')).toHaveTextContent('Avbryt')
  })

  it('switches the whole app to English and remembers it', async () => {
    const { en } = setup()
    await userEvent.click(en)
    expect(en).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByTestId('probe')).toHaveTextContent('Cancel')
    expect(document.documentElement.lang).toBe('en')
    expect(localStorage.getItem('tf-lang')).toBe('en')
  })

  it('restores the remembered language and ignores junk', () => {
    localStorage.setItem('tf-lang', 'en')
    expect(setup().en).toHaveAttribute('aria-pressed', 'true')
    localStorage.setItem('tf-lang', 'klingon')
    document.body.innerHTML = ''
    expect(setup().no).toHaveAttribute('aria-pressed', 'true')
  })
})
