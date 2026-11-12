// Opt-in error alerts with Sentry. Nothing happens unless VITE_SENTRY_DSN is set when the app is built
// (see docs/DEPLOYMENT.md), and Sentry's code is only downloaded then. What's sent: the error, the browser,
// and the page path. Not sent: who you are, IP addresses, request bodies, or anything after a "?" in a URL
// (password-reset links and invite codes travel there).

type Sentry = typeof import('@sentry/react')

let sentry: Sentry | null = null

/** Everything after "?" or "#" goes; that's where one-time tokens live. */
export function scrubUrl(url: string): string {
  return url.replace(/[?#].*$/, '')
}

export function startErrorReporting(dsn: string | undefined = import.meta.env.VITE_SENTRY_DSN) {
  if (!dsn || !import.meta.env.PROD) return
  void import('@sentry/react').then((S) => {
    S.init({
      dsn,
      environment: import.meta.env.VITE_SENTRY_ENVIRONMENT || 'production',
      tracesSampleRate: 0,
      dataCollection: { userInfo: false, cookies: false, httpHeaders: false, httpBodies: [], urlQueryParams: false },
      beforeSend(event) {
        // An explicit empty IP stops Sentry from filling one in from the connection
        event.user = { ip_address: null }
        if (event.request) {
          if (event.request.url) event.request.url = scrubUrl(event.request.url)
          delete event.request.query_string
          delete event.request.cookies
          delete event.request.headers
          delete event.request.data
        }
        return event
      },
      beforeBreadcrumb(crumb) {
        // Typed text never leaves the device
        if (crumb.category === 'ui.input') return null
        const data = crumb.data
        if (data) {
          for (const key of ['url', 'from', 'to'] as const) {
            if (typeof data[key] === 'string') data[key] = scrubUrl(data[key])
          }
        }
        return crumb
      },
    })
    sentry = S
  })
}

/** The server failed (5xx). Reported from here because the API itself has no error alerts. */
export function reportServerError(status: number, path: string) {
  sentry?.captureMessage(`API ${status} on ${scrubUrl(path).replace(/\/\d+(?=\/|$)/g, '/:id')}`, 'error')
}
