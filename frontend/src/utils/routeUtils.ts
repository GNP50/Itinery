import type { ItineraryStep } from '../types/itinerary'

/**
 * Calculate average speed for a route segment
 * @param distanceKm Distance in kilometers
 * @param durationMin Duration in minutes
 * @returns Speed in km/h, or null if invalid input
 */
export function calculateSegmentSpeed(
  distanceKm: number | undefined,
  durationMin: number | undefined
): number | null {
  if (!distanceKm || !durationMin || durationMin === 0) return null
  const durationHours = durationMin / 60
  return distanceKm / durationHours
}

/**
 * Format duration in minutes to human-readable string
 * @param minutes Duration in minutes
 * @returns Formatted string (e.g., "2 hours 30 min", "45 min")
 */
export function formatDurationDetailed(minutes: number | undefined): string {
  if (!minutes) return '—'

  const hours = Math.floor(minutes / 60)
  const mins = Math.round(minutes % 60)

  if (hours === 0) {
    return `${mins} min`
  }
  if (mins === 0) {
    return `${hours} ${hours === 1 ? 'hour' : 'hours'}`
  }
  return `${hours} ${hours === 1 ? 'hour' : 'hours'} ${mins} min`
}

/**
 * Format distance in kilometers to human-readable string
 * @param km Distance in kilometers
 * @returns Formatted string (e.g., "150 m", "2.3 km", "125.5 km")
 */
export function formatDistance(km: number | undefined): string {
  if (!km) return '—'

  if (km < 1) {
    return `${Math.round(km * 1000)} m`
  }
  if (km < 10) {
    return `${km.toFixed(1)} km`
  }
  return `${Math.round(km)} km`
}

/**
 * Format speed in km/h to human-readable string
 * @param kmh Speed in kilometers per hour
 * @returns Formatted string (e.g., "65 km/h")
 */
export function formatSpeed(kmh: number | null): string {
  if (!kmh) return '—'
  return `${Math.round(kmh)} km/h`
}

/**
 * Calculate cumulative distance and duration up to each step
 * @param steps Array of itinerary steps
 * @returns Array of objects with cumulative stats for each step
 */
export function calculateCumulativeStats(
  steps: ItineraryStep[]
): Array<{ stepId: string; cumulativeDistance: number; cumulativeDuration: number }> {
  let totalDistance = 0
  let totalDuration = 0

  return steps.map((step) => {
    totalDistance += step.distanceFromPrevKm || 0
    totalDuration += step.durationFromPrevMin || 0

    return {
      stepId: step.id,
      cumulativeDistance: totalDistance,
      cumulativeDuration: totalDuration,
    }
  })
}

/**
 * Get cumulative stats for a specific step
 * @param steps Array of itinerary steps
 * @param stepId ID of the target step
 * @returns Object with cumulative distance and duration, or null if not found
 */
export function getCumulativeStatsForStep(
  steps: ItineraryStep[],
  stepId: string
): { cumulativeDistance: number; cumulativeDuration: number } | null {
  const allStats = calculateCumulativeStats(steps)
  return allStats.find((stat) => stat.stepId === stepId) || null
}

/**
 * Calculate progress percentage based on distance or duration
 * @param current Current value (distance or duration)
 * @param total Total value
 * @returns Percentage (0-100)
 */
export function calculateProgressPercentage(
  current: number,
  total: number
): number {
  if (!total || total === 0) return 0
  return Math.min(100, Math.max(0, (current / total) * 100))
}

/**
 * Estimate time of arrival at a step based on current position
 * @param currentStepIndex Current step index
 * @param targetStepIndex Target step index
 * @param steps Array of itinerary steps
 * @returns Estimated duration in minutes, or null if invalid
 */
export function estimateTimeToStep(
  currentStepIndex: number,
  targetStepIndex: number,
  steps: ItineraryStep[]
): number | null {
  if (currentStepIndex >= targetStepIndex) return null

  let totalDuration = 0
  for (let i = currentStepIndex + 1; i <= targetStepIndex; i++) {
    totalDuration += steps[i]?.durationFromPrevMin || 0
  }

  return totalDuration
}

/**
 * Get travel mode emoji/icon
 * @param travelMode Travel mode string (TRANSIT, DRIVING, WALKING, etc.)
 * @returns Emoji string
 */
export function getTravelModeEmoji(travelMode: string): string {
  const mode = travelMode.toUpperCase()

  switch (mode) {
    case 'DRIVING':
      return '🚗'
    case 'TRANSIT':
      return '🚌'
    case 'WALKING':
      return '🚶'
    case 'BICYCLING':
      return '🚴'
    case 'FLYING':
      return '✈️'
    default:
      return '🗺️'
  }
}

/**
 * Format route geometry coordinates for display
 * @param coordinates Array of [lon, lat] coordinate pairs
 * @returns Formatted string with coordinate count
 */
export function formatRouteGeometry(coordinates: number[][] | undefined): string {
  if (!coordinates || coordinates.length === 0) return 'No route data'
  return `${coordinates.length} waypoints`
}

/**
 * Calculate total distance and duration for an itinerary
 * @param steps Array of itinerary steps
 * @returns Object with total distance (km) and duration (min)
 */
export function calculateTotalStats(
  steps: ItineraryStep[]
): { totalDistance: number; totalDuration: number } {
  return steps.reduce(
    (acc, step) => ({
      totalDistance: acc.totalDistance + (step.distanceFromPrevKm || 0),
      totalDuration: acc.totalDuration + (step.durationFromPrevMin || 0),
    }),
    { totalDistance: 0, totalDuration: 0 }
  )
}
