// Push notifications in this browser: whether they can work here, and turning them on and off. The
// service worker (public/sw.js) shows them; the server sends them (see docs/API.md, "Push notifications").
import { pushApi } from '../api/endpoints'

export type PushState =
  /** This browser has no push at all, or the app isn't running with its service worker (development). */
  | 'unsupported'
  /** iPhone/iPad: only works once the site is added to the home screen and opened from there. */
  | 'install-first'
  /** The server has no keys: push is off for everyone. */
  | 'server-off'
  /** The person said no in the browser; only the browser's site settings can undo that. */
  | 'blocked'
  | 'off'
  | 'on'

/** The VAPID public key (base64url) as the bytes `pushManager.subscribe` wants. */
export function keyBytes(base64url: string): Uint8Array<ArrayBuffer> {
  const base64 = (base64url + '='.repeat((4 - (base64url.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/')
  const raw = atob(base64)
  const out = new Uint8Array(new ArrayBuffer(raw.length))
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i)
  return out
}

export function isIos(nav: Navigator = navigator): boolean {
  return /iPad|iPhone|iPod/.test(nav.userAgent) || (nav.platform === 'MacIntel' && nav.maxTouchPoints > 1)
}

function standalone(): boolean {
  return (navigator as Navigator & { standalone?: boolean }).standalone === true || window.matchMedia?.('(display-mode: standalone)').matches
}

async function registration(): Promise<ServiceWorkerRegistration | undefined> {
  if (!('serviceWorker' in navigator)) return undefined
  return navigator.serviceWorker.getRegistration()
}

/** What the switch should show for this browser. */
export async function pushState(): Promise<PushState> {
  const supported = 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window
  if (!supported) return isIos() && !standalone() ? 'install-first' : 'unsupported'
  const reg = await registration()
  if (!reg) return 'unsupported'
  const { publicKey } = await pushApi.key()
  if (!publicKey) return 'server-off'
  if (Notification.permission === 'denied') return 'blocked'
  const sub = await reg.pushManager.getSubscription()
  if (!sub) return 'off'
  // The browser may be subscribed for someone else who used it, or the server may have forgotten it
  const { subscribed } = await pushApi.status(sub.endpoint)
  return subscribed ? 'on' : 'off'
}

/** Asks for permission, subscribes this browser and tells the server. Resolves to the new state. */
export async function turnOn(): Promise<PushState> {
  const reg = await registration()
  const { publicKey } = await pushApi.key()
  if (!reg || !publicKey) return 'unsupported'
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') return permission === 'denied' ? 'blocked' : 'off'
  const key = keyBytes(publicKey)
  let sub = await reg.pushManager.getSubscription()
  if (sub && !sameKey(sub, key)) {
    // Subscribed with old server keys: start over
    await sub.unsubscribe()
    sub = null
  }
  sub ??= await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key })
  const json = sub.toJSON()
  await pushApi.subscribe({ endpoint: sub.endpoint, keys: { p256dh: json.keys?.p256dh ?? '', auth: json.keys?.auth ?? '' } })
  return 'on'
}

export async function turnOff(): Promise<PushState> {
  const reg = await registration()
  const sub = await reg?.pushManager.getSubscription()
  if (sub) {
    await pushApi.unsubscribe(sub.endpoint).catch(() => {})
    await sub.unsubscribe()
  }
  return 'off'
}

function sameKey(sub: PushSubscription, key: Uint8Array): boolean {
  const current = sub.options.applicationServerKey
  if (!current) return false
  const a = new Uint8Array(current)
  return a.length === key.length && a.every((b, i) => b === key[i])
}
