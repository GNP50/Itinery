import { useState, useEffect, useRef, useCallback } from 'react'
import type { ItineraryStatusResponse } from '../api/client'
import { useClient } from '../contexts/ClientContext'
import { useItineraryStore } from '../store/itineraryStore'

interface UseItineraryPollingOptions {
  intervalMs?: number
}

interface UseItineraryPollingResult {
  status: ItineraryStatusResponse | null
  loading: boolean
  stopPolling: () => void
}

const TERMINAL_STATUSES = ['COMPLETED', 'FAILED', 'CANCELLED']

export function useItineraryPolling(
  id: string,
  accessToken: string,
  opts?: UseItineraryPollingOptions
): UseItineraryPollingResult {
  const intervalMs = opts?.intervalMs ?? 5000
  const client = useClient()
  const { updateStatus } = useItineraryStore()

  const [status, setStatus] = useState<ItineraryStatusResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const stoppedRef = useRef(false)

  const fetchStatus = useCallback(async () => {
    if (stoppedRef.current) return
    try {
      const data = await client.getItineraryStatus(id, accessToken)
      setStatus(data)
      updateStatus(id, data.status)
      if (TERMINAL_STATUSES.includes(data.status)) {
        stoppedRef.current = true
        if (intervalRef.current) clearInterval(intervalRef.current)
      }
    } catch {
      // silently ignore polling errors
    } finally {
      setLoading(false)
    }
  }, [id, accessToken, client, updateStatus])

  const stopPolling = useCallback(() => {
    stoppedRef.current = true
    if (intervalRef.current) clearInterval(intervalRef.current)
  }, [])

  useEffect(() => {
    stoppedRef.current = false
    setLoading(true)
    fetchStatus()
    intervalRef.current = setInterval(fetchStatus, intervalMs)
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current)
    }
  }, [id, accessToken, intervalMs])

  return { status, loading, stopPolling }
}
