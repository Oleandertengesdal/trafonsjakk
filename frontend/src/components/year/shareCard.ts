/** Turns a story card into a PNG and shares it (phones) or downloads it (desktop). */
export async function shareCard(el: HTMLElement, fileName: string, title: string): Promise<'shared' | 'downloaded' | 'cancelled'> {
  const { toBlob } = await import('html-to-image')
  const blob = await toBlob(el, { pixelRatio: 3, cacheBust: true })
  if (!blob) throw new Error('No image')
  const file = new File([blob], fileName, { type: 'image/png' })
  if (typeof navigator.canShare === 'function' && navigator.canShare({ files: [file] })) {
    try {
      await navigator.share({ files: [file], title })
      return 'shared'
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') return 'cancelled'
      // Some browsers claim they can share files and then refuse: fall back to a download
    }
  }
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.append(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
  return 'downloaded'
}
