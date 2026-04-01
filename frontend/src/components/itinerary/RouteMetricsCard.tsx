import React from 'react'
import { Navigation, Clock, Gauge, MapPin, TrendingUp } from 'lucide-react'
import type { ItineraryStep } from '../../types/itinerary'
import {
  formatDistance,
  formatDurationDetailed,
  calculateSegmentSpeed,
  formatSpeed,
  calculateProgressPercentage,
} from '../../utils/routeUtils'

interface Props {
  step: ItineraryStep
  cumulativeDistance?: number
  cumulativeDuration?: number
  totalDistance?: number
  totalDuration?: number
  showCumulative?: boolean
}

export function RouteMetricsCard({
  step,
  cumulativeDistance,
  cumulativeDuration,
  totalDistance,
  totalDuration,
  showCumulative = false,
}: Props) {
  const hasRouteData = step.distanceFromPrevKm || step.durationFromPrevMin
  const speed = calculateSegmentSpeed(step.distanceFromPrevKm, step.durationFromPrevMin)

  if (!hasRouteData) {
    return (
      <div className="card card-body bg-muted/30">
        <div className="flex items-center justify-center py-6 text-muted-foreground">
          <MapPin className="h-5 w-5 mr-2" />
          <p className="text-sm">No route data available</p>
        </div>
      </div>
    )
  }

  const progressPercent =
    showCumulative && cumulativeDistance && totalDistance
      ? calculateProgressPercentage(cumulativeDistance, totalDistance)
      : 0

  return (
    <div className="card card-body animate-fade-in">
      <div className="flex items-center gap-2 mb-4">
        <div className="h-8 w-8 rounded-lg bg-primary/10 flex items-center justify-center">
          <Navigation className="h-4 w-4 text-primary" />
        </div>
        <h3 className="font-semibold text-sm">Route Metrics</h3>
      </div>

      <div className="grid grid-cols-2 gap-4">
        {/* Distance from previous */}
        <div className="flex items-start gap-3">
          <div className="h-9 w-9 rounded-lg bg-blue-500/10 flex items-center justify-center flex-shrink-0">
            <Navigation className="h-4 w-4 text-blue-600 dark:text-blue-400" />
          </div>
          <div className="min-w-0">
            <p className="text-xs text-muted-foreground">Distance</p>
            <p className="font-bold text-lg">{formatDistance(step.distanceFromPrevKm)}</p>
          </div>
        </div>

        {/* Duration from previous */}
        <div className="flex items-start gap-3">
          <div className="h-9 w-9 rounded-lg bg-green-500/10 flex items-center justify-center flex-shrink-0">
            <Clock className="h-4 w-4 text-green-600 dark:text-green-400" />
          </div>
          <div className="min-w-0">
            <p className="text-xs text-muted-foreground">Duration</p>
            <p className="font-bold text-lg">{formatDurationDetailed(step.durationFromPrevMin)}</p>
          </div>
        </div>

        {/* Average speed */}
        {speed && (
          <div className="flex items-start gap-3">
            <div className="h-9 w-9 rounded-lg bg-purple-500/10 flex items-center justify-center flex-shrink-0">
              <Gauge className="h-4 w-4 text-purple-600 dark:text-purple-400" />
            </div>
            <div className="min-w-0">
              <p className="text-xs text-muted-foreground">Avg Speed</p>
              <p className="font-bold text-lg">{formatSpeed(speed)}</p>
            </div>
          </div>
        )}

        {/* Cumulative stats */}
        {showCumulative && cumulativeDistance !== undefined && cumulativeDuration !== undefined && (
          <div className="flex items-start gap-3">
            <div className="h-9 w-9 rounded-lg bg-orange-500/10 flex items-center justify-center flex-shrink-0">
              <TrendingUp className="h-4 w-4 text-orange-600 dark:text-orange-400" />
            </div>
            <div className="min-w-0">
              <p className="text-xs text-muted-foreground">Total So Far</p>
              <p className="font-semibold text-sm">
                {formatDistance(cumulativeDistance)} · {formatDurationDetailed(cumulativeDuration)}
              </p>
            </div>
          </div>
        )}
      </div>

      {/* Progress bar */}
      {showCumulative && progressPercent > 0 && (
        <div className="mt-4 pt-4 border-t border-border">
          <div className="flex items-center justify-between text-xs text-muted-foreground mb-2">
            <span>Journey Progress</span>
            <span className="font-semibold">{Math.round(progressPercent)}%</span>
          </div>
          <div className="progress-bar">
            <div
              className="progress-fill progress-fill-success"
              style={{ width: `${progressPercent}%` }}
            />
          </div>
        </div>
      )}
    </div>
  )
}
