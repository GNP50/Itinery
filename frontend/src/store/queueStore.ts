import { create } from 'zustand'
import type { QueueStatusResponse, QueueItem, QueueStatistics } from '../types/queue'

/**
 * Global queue state store using Zustand
 *
 * IMPROVEMENTS:
 * - Separate metrics for registered and anonymous queues
 * - Clear method for cleanup
 * - Better TypeScript typing
 * - Computed values for convenience
 * - Dynamic maxQueueSize, maxConcurrent, processingCount from backend
 */
interface QueueStore {
  /** Total number of items in all queues */
  totalQueueLength: number

  /** Number of items in registered user queue */
  registeredQueueSize: number

  /** Number of items in anonymous user queue */
  anonymousQueueSize: number

  /** List of queue items (may be empty) */
  items: QueueItem[]

  /** Timestamp of last successful fetch */
  lastUpdated: Date | null

  /** Maximum number of items that can be queued (from backend config) */
  maxQueueSize: number

  /** Maximum number of parallel saga executions (from backend config) */
  maxConcurrent: number

  /** Current number of items being processed (from backend statistics) */
  processingCount: number

  /** Update store with fresh data from API */
  setQueueData: (data: QueueStatusResponse) => void

  /** Clear all queue data (e.g., on logout) */
  clearQueueData: () => void

  // Backward compatibility computed properties
  /** @deprecated Use totalQueueLength instead */
  queueLength: number
}

export const useQueueStore = create<QueueStore>((set, get) => ({
  totalQueueLength: 0,
  registeredQueueSize: 0,
  anonymousQueueSize: 0,
  items: [],
  lastUpdated: null,
  maxQueueSize: 1000,      // Default until loaded from backend
  maxConcurrent: 5,        // Default until loaded from backend
  processingCount: 0,      // Default until loaded from backend

  // Backward compatibility computed property
  get queueLength() {
    return get().totalQueueLength
  },

  setQueueData: (data: QueueStatusResponse) =>
    set({
      totalQueueLength: data.totalQueueLength,
      registeredQueueSize: data.registeredQueueSize,
      anonymousQueueSize: data.anonymousQueueSize,
      items: data.items,
      maxQueueSize: data.maxQueueSize,
      maxConcurrent: data.maxConcurrent,
      processingCount: data.processingCount,
      lastUpdated: new Date(),
    }),

  clearQueueData: () =>
    set({
      totalQueueLength: 0,
      registeredQueueSize: 0,
      anonymousQueueSize: 0,
      items: [],
      lastUpdated: null,
      maxQueueSize: 1000,
      maxConcurrent: 5,
      processingCount: 0,
    }),
}))

// ============================================================================
// Selector Hooks (for optimized re-renders)
// ============================================================================

/**
 * Hook to get only the total queue length
 * Components using this will only re-render when totalQueueLength changes
 */
export const useTotalQueueLength = () => 
  useQueueStore((state) => state.totalQueueLength)

/**
 * Hook to get registered queue size
 */
export const useRegisteredQueueSize = () => 
  useQueueStore((state) => state.registeredQueueSize)

/**
 * Hook to get anonymous queue size
 */
export const useAnonymousQueueSize = () => 
  useQueueStore((state) => state.anonymousQueueSize)

/**
 * Hook to get queue items
 */
export const useQueueItems = () => 
  useQueueStore((state) => state.items)

/**
 * Hook to check if queue data is fresh (updated within last 10 seconds)
 */
export const useIsQueueDataFresh = () => 
  useQueueStore((state) => {
    if (!state.lastUpdated) return false
    const ageMs = Date.now() - state.lastUpdated.getTime()
    return ageMs < 10000 // 10 seconds
  })

/**
 * Hook to get queue statistics summary
 */
export const useQueueStatistics = (): QueueStatistics => 
  useQueueStore((state) => {
    const total = state.totalQueueLength
    const registered = state.registeredQueueSize
    const anonymous = state.anonymousQueueSize
    
    return {
      total,
      registered,
      anonymous,
      registeredPercentage: total > 0 ? (registered / total) * 100 : 0,
      anonymousPercentage: total > 0 ? (anonymous / total) * 100 : 0,
      isEmpty: total === 0,
      hasItems: total > 0,
      hasRegistered: registered > 0,
      hasAnonymous: anonymous > 0,
    }
  })

/**
 * Hook to find a specific queue item by ID
 */
export const useQueueItem = (itemId: string) => 
  useQueueStore((state) => 
    state.items.find((item) => item.id === itemId)
  )

/**
 * Hook to get the position of a specific item
 */
export const useQueuePosition = (itemId: string) =>
  useQueueStore((state) => {
    const item = state.items.find((item) => item.id === itemId)
    return item?.queuePosition ?? null
  })

/**
 * Hook to get queue utilization percentage (0-100)
 */
export const useQueueUtilization = () =>
  useQueueStore((state) => {
    if (state.maxQueueSize === 0) return 0
    return (state.totalQueueLength / state.maxQueueSize) * 100
  })

/**
 * Hook to get processing utilization percentage (0-100)
 */
export const useProcessingUtilization = () =>
  useQueueStore((state) => {
    if (state.maxConcurrent === 0) return 0
    return (state.processingCount / state.maxConcurrent) * 100
  })

/**
 * Hook to get max concurrent value
 */
export const useMaxConcurrent = () =>
  useQueueStore((state) => state.maxConcurrent)

/**
 * Hook to get processing count
 */
export const useProcessingCount = () =>
  useQueueStore((state) => state.processingCount)

/**
 * Hook to get max queue size
 */
export const useMaxQueueSize = () =>
  useQueueStore((state) => state.maxQueueSize)

