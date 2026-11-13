import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import { en } from '../../i18n/en'
import { I18nProvider } from '../../i18n/I18nProvider'
import * as reporting from '../../lib/errorReporting'
import * as stale from '../../lib/staleChunks'
import { NotFoundPage } from '../../pages/NotFoundPage'
import { ErrorNotice } from '../ui/ErrorNotice'
import { CrashPage } from './CrashPage'
import { ErrorBoundary } from './ErrorBoundary'

const e = en.errorPage

function wrap(node: React.ReactNode) {
  localStorage.setItem('tf-lang', 'en')
  return render(
    <I18nProvider>
      <MemoryRouter>{node}</MemoryRouter>
    </I18nProvider>,
  )
}

function Bomb({ error }: { error: Error }): React.ReactNode {
  throw error
}

describe('error pages', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })
  afterEach(() => vi.restoreAllMocks())

  it('shows a crash with an error code, reports it, and recovers when you move on', async () => {
    const report = vi.spyOn(reporting, 'reportCrash').mockImplementation(() => {})
    function Harness() {
      const [page, setPage] = useState('a')
      return (
        <>
          <button onClick={() => setPage('b')}>next page</button>
          <ErrorBoundary resetKey={page} fallback={(crash) => <CrashPage {...crash} />}>
            {page === 'a' ? <Bomb error={new TypeError("Cannot read properties of undefined (reading 'rating')")} /> : <p>page b works</p>}
          </ErrorBoundary>
        </>
      )
    }
    wrap(<Harness />)
    expect(screen.getByRole('heading', { name: e.crash.title })).toBeInTheDocument()
    const code = screen.getByText(/^[2-9A-HJKMNP-Z]{6}$/).textContent!
    expect(report).toHaveBeenCalledWith(expect.any(TypeError), code, expect.any(String))
    expect(screen.getByRole('link', { name: e.report })).toHaveAttribute('href', expect.stringContaining(`ref=${code}`))
    expect(screen.getByRole('button', { name: e.reload })).toBeInTheDocument()
    // The technical message never reaches the screen
    expect(screen.queryByText(/Cannot read properties/)).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'next page' }))
    expect(screen.getByText('page b works')).toBeInTheDocument()
  })

  it('treats a missing chunk after a deploy as a new version and reloads once', () => {
    const reload = vi.spyOn(stale, 'reloadForUpdate').mockReturnValue(false)
    const report = vi.spyOn(reporting, 'reportCrash').mockImplementation(() => {})
    wrap(
      <ErrorBoundary fallback={(crash) => <CrashPage {...crash} />}>
        <Bomb error={new TypeError('Failed to fetch dynamically imported module: https://x.app/assets/GameDetailPage-Dx1.js')} />
      </ErrorBoundary>,
    )
    expect(reload).toHaveBeenCalled()
    expect(screen.getByRole('heading', { name: e.update.title })).toBeInTheDocument()
    expect(screen.queryByText(e.reference)).not.toBeInTheDocument()
    // It didn't reload (already did once a moment ago), so it's reported like any crash
    expect(report).toHaveBeenCalled()
  })

  it('says what was not found and where to look instead', () => {
    wrap(<NotFoundPage what="game" />)
    expect(screen.getByRole('heading', { name: e.notFound.game.title })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: e.allOf.game })).toHaveAttribute('href', '/games')
    expect(screen.getByRole('link', { name: e.home })).toHaveAttribute('href', '/')
  })

  it('puts the error code of a server error next to the message', () => {
    wrap(<ErrorNotice error={new ApiError(500, 'x', { code: 'server_error', ref: 'K7Q2MX' })} />)
    expect(screen.getByText(en.errors.codes.server_error as string)).toBeInTheDocument()
    expect(screen.getByText('K7Q2MX')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: e.report })).toHaveAttribute('href', expect.stringContaining('ref=K7Q2MX'))
  })

  it('has no error code for ordinary errors, and says so when offline', () => {
    const online = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    wrap(<ErrorNotice error={new ApiError(0, 'Network error')} />)
    expect(screen.getByText(en.errors.offline)).toBeInTheDocument()
    expect(screen.queryByText(e.reference)).not.toBeInTheDocument()
    online.mockRestore()
  })
})

describe('stale chunks', () => {
  afterEach(() => {
    sessionStorage.clear()
    vi.restoreAllMocks()
  })

  it('recognises the messages browsers use', () => {
    expect(stale.isStaleChunkError(new TypeError('Failed to fetch dynamically imported module: /assets/a.js'))).toBe(true)
    expect(stale.isStaleChunkError(new TypeError('Importing a module script failed.'))).toBe(true)
    expect(stale.isStaleChunkError(new Error('error loading dynamically imported module'))).toBe(true)
    expect(stale.isStaleChunkError(new TypeError("Cannot read properties of undefined (reading 'x')"))).toBe(false)
  })

  it('reloads at most once a minute', () => {
    const reload = vi.fn()
    vi.spyOn(window, 'location', 'get').mockReturnValue({ ...window.location, reload })
    expect(stale.reloadForUpdate()).toBe(true)
    expect(stale.reloadForUpdate()).toBe(false)
    expect(reload).toHaveBeenCalledTimes(1)
  })
})
