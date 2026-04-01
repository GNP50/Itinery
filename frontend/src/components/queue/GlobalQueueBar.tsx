import React from 'react'
import { Clock } from 'lucide-react'
import { useQueueStore } from '../../store/queueStore'

export function GlobalQueueBar() {
  const { totalQueueLength, lastUpdated } = useQueueStore()

  if (totalQueueLength === 0) return null

  const secondsAgo = lastUpdated
    ? Math.round((Date.now() - lastUpdated.getTime()) / 1000)
    : null

  return (
    <div className="queue-banner">
      <div className="container mx-auto max-w-7xl flex items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          {totalQueueLength > 0 && (
            <span className="queue-banner-item">
              <Clock className="h-3 w-3" />
              {totalQueueLength} waiting in queue
            </span>
          )}
        </div>
        {secondsAgo !== null && (
          <span className="text-muted-foreground hidden sm:block text-xs">
            updated {secondsAgo}s ago
          </span>
        )}
      </div>
    </div>
  )
}
