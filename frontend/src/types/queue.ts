/**
 * Enhanced queue types with separate metrics for registered and anonymous queues
 */

/**
 * Single item in the processing queue
 *
 * CHANGES FROM PREVIOUS:
 * - queuePosition is now RELATIVE and always accurate
 * - No more "absolute position that becomes stale" issue
 */
export interface QueueItem {
  id: string
  title: string
  status: 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  /**
   * RELATIVE queue position (1 = next to be processed)
   * This value is calculated dynamically from Redis and is always accurate
   */
  queuePosition?: number
  progress?: number
  estimatedCompletion?: string
}

/**
 * Response from GET /api/v1/queue/status endpoint
 *
 * CHANGES FROM PREVIOUS:
 * - Renamed fields for clarity
 * - Added separate queue size metrics
 * - queuePosition in items is now guaranteed to be relative and accurate
 * - Added maxQueueSize, maxConcurrent, processingCount
 */
export interface QueueStatusResponse {
  /** Total number of items across both queues */
  totalQueueLength: number

  /** Number of items in the registered/admin user queue (high priority) */
  registeredQueueSize: number

  /** Number of items in the anonymous user queue (low priority) */
  anonymousQueueSize: number

  /** Ordered list of queue entries (optional, may be empty) */
  items: QueueItem[]

  /** Maximum number of items that can be queued (from queue.max-size config) */
  maxQueueSize: number

  /** Maximum number of parallel saga executions (from saga.max-concurrent config) */
  maxConcurrent: number

  /** Current number of items being processed */
  processingCount: number
}

/**
 * Props for queue status display component
 */
export interface QueueStatusProps {
  status: 'IDLE' | 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  
  /** RELATIVE position (always accurate) */
  queuePosition?: number
  
  estimatedCompletion?: string
  progress?: number
}

/**
 * Queue statistics summary
 */
export interface QueueStatistics {
  total: number
  registered: number
  anonymous: number
  registeredPercentage: number
  anonymousPercentage: number
  isEmpty: boolean
  hasItems: boolean
  hasRegistered: boolean
  hasAnonymous: boolean
}

