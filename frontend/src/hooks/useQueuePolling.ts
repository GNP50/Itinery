import { useEffect, useRef } from 'react'
import { useClient } from '../contexts/ClientContext'
import { useQueueStore } from '../store/queueStore'

interface UseQueuePollingOptions {
  intervalMs?: number
  enabled?: boolean
}

export function useQueuePolling(opts?: UseQueuePollingOptions): void {
  const intervalMs = opts?.intervalMs ?? 5000
  const enabled = opts?.enabled ?? true
  const client = useClient()
  const { setQueueData } = useQueueStore()
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    if (!enabled) return

    const fetch = async () => {
      try {
        const data = await client.getQueueStatus()
        setQueueData(data)
      } catch (err) {
        // Silently ignore errors (e.g., 401 during logout/re-auth)
        console.debug('Queue polling error (ignored):', err)
      }
    }

    fetch()
    intervalRef.current = setInterval(fetch, intervalMs)

    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current)
    }
  }, [enabled, intervalMs, client, setQueueData])
}
