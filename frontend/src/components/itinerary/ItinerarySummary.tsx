import React, { useState } from 'react'
import {
  MapPin,
  Clock,
  Car,
  Bike,
  Footprints,
  Train,
  Calendar,
  DollarSign,
  Map as MapIcon,
  ChevronDown,
  ChevronUp,
} from 'lucide-react'
import type { Itinerary } from '../../types/itinerary'
import { PreferencesCard } from './PreferencesCard'
import { formatDurationDetailed } from '../../utils/routeUtils'

interface ItinerarySummaryProps {
  itinerary: Itinerary
  showAiEstimates?: boolean
}

export function ItinerarySummary({ itinerary, showAiEstimates = false }: ItinerarySummaryProps) {
  const [showAllTips, setShowAllTips] = useState(false)

  const travelModeIcon = {
    CAR: Car,
    BIKE: Bike,
    DRIVING: Car,
    WALK: Footprints,
    WALKING: Footprints,
    TRANSIT: Train,
  }[itinerary.travelMode.toUpperCase()] || Train

  const visitedCount = itinerary.steps.filter((s) => s.status === 'VISITED').length
  const totalCount = itinerary.steps.length
  const progress = totalCount > 0 ? Math.round((visitedCount / totalCount) * 100) : 0

  // Get all AI tips
  const allTips = itinerary.aiSuggestions?.generalTips || []
  const tipsToShow = showAllTips ? allTips : allTips.slice(0, 5)
  const hasMoreTips = allTips.length > 5

  // Determine grid layout based on what we're showing
  const hasPreferences = itinerary.preferences && (
    (itinerary.preferences.interests && itinerary.preferences.interests.length > 0) ||
    itinerary.preferences.avoidHighways !== undefined ||
    itinerary.preferences.generateAiTips !== undefined
  )
  const hasAiSuggestions = showAiEstimates && itinerary.aiSuggestions

  // Grid class: show 4 columns if we have all components, else adapt
  const gridClass = hasPreferences && hasAiSuggestions
    ? 'grid-cols-1 md:grid-cols-2 lg:grid-cols-4'
    : hasPreferences || hasAiSuggestions
    ? 'grid-cols-1 md:grid-cols-2 lg:grid-cols-3'
    : 'grid-cols-1 md:grid-cols-2'

  return (
    <div className={`grid ${gridClass} gap-4 animate-fade-in-up`}>
      {/* Basic Info */}
      <div className="card card-body">
        <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wider mb-4">Overview</h3>
        <div className="space-y-4">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-500/10">
              {React.createElement(travelModeIcon, { className: 'h-5 w-5 text-blue-600 dark:text-blue-400' })}
            </div>
            <div>
              <p className="text-xs text-muted-foreground">Travel Mode</p>
              <p className="font-medium text-foreground capitalize">{itinerary.travelMode.toLowerCase()}</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-green-500/10">
              <MapIcon className="h-5 w-5 text-green-600 dark:text-green-400" />
            </div>
            <div>
              <p className="text-xs text-muted-foreground">Stops</p>
              <p className="font-medium text-foreground">{totalCount}</p>
            </div>
          </div>

          {itinerary.totalDistanceKm && (
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-purple-500/10">
                <MapPin className="h-5 w-5 text-purple-600 dark:text-purple-400" />
              </div>
              <div>
                <p className="text-xs text-muted-foreground">Total Distance</p>
                <p className="font-medium text-foreground">{itinerary.totalDistanceKm.toFixed(1)} km</p>
              </div>
            </div>
          )}

          {itinerary.totalDurationMinutes && (
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-orange-500/10">
                <Clock className="h-5 w-5 text-orange-600 dark:text-orange-400" />
              </div>
              <div>
                <p className="text-xs text-muted-foreground">Duration</p>
                <p className="font-medium text-foreground">
                  {formatDurationDetailed(itinerary.totalDurationMinutes)}
                </p>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Progress */}
      <div className="card card-body">
        <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wider mb-4">Progress</h3>
        <div className="space-y-4">
          <div>
            <div className="flex justify-between text-sm mb-2">
              <span className="text-muted-foreground">Completion</span>
              <span className="font-semibold text-foreground">{progress}%</span>
            </div>
            <div className="progress-bar">
              <div
                className="progress-fill progress-fill-success transition-all duration-500"
                style={{ width: `${progress}%` }}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="rounded-lg bg-green-500/10 p-3">
              <p className="text-2xl font-bold text-green-600 dark:text-green-400">{visitedCount}</p>
              <p className="text-xs text-green-600 dark:text-green-400">Completed</p>
            </div>
            <div className="rounded-lg bg-muted p-3">
              <p className="text-2xl font-bold text-foreground">{totalCount - visitedCount}</p>
              <p className="text-xs text-muted-foreground">Remaining</p>
            </div>
          </div>

          {itinerary.currentStepIndex !== undefined && (
            <div className="flex items-center gap-3">
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-blue-500/10">
                <Clock className="h-4 w-4 text-blue-600 dark:text-blue-400" />
              </div>
              <div>
                <p className="text-xs text-muted-foreground">Current Step</p>
                <p className="font-medium text-foreground">
                  Step {itinerary.currentStepIndex + 1} of {totalCount}
                </p>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Preferences */}
      {hasPreferences && <PreferencesCard preferences={itinerary.preferences} />}

      {/* AI Suggestions */}
      {hasAiSuggestions && itinerary.aiSuggestions && (
        <div className="card card-body bg-gradient-to-br from-indigo-600 to-purple-700 text-white">
          <h3 className="text-sm font-semibold text-indigo-200 uppercase tracking-wider mb-4">
            AI Suggestions
          </h3>
          <div className="space-y-4">
            {itinerary.aiSuggestions?.estimatedBudget && (
              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-white/10">
                  <DollarSign className="h-5 w-5" />
                </div>
                <div>
                  <p className="text-xs text-indigo-200">Estimated Budget</p>
                  <p className="font-medium">{itinerary.aiSuggestions.estimatedBudget}</p>
                </div>
              </div>
            )}

            {allTips.length > 0 && (
              <div>
                <p className="text-xs text-indigo-200 mb-2">
                  Travel Tips {allTips.length > 0 && `(${allTips.length})`}
                </p>
                <ul className="space-y-2">
                  {tipsToShow.map((tip: string, i: number) => (
                    <li key={i} className="flex items-start gap-2 text-sm">
                      <span className="text-indigo-200 flex-shrink-0">•</span>
                      <span className="text-indigo-50">{tip}</span>
                    </li>
                  ))}
                </ul>
                {hasMoreTips && (
                  <button
                    onClick={() => setShowAllTips(!showAllTips)}
                    className="mt-3 text-xs text-white/90 hover:text-white hover:underline flex items-center gap-1"
                  >
                    {showAllTips ? (
                      <>
                        Show less <ChevronUp className="h-3 w-3" />
                      </>
                    ) : (
                      <>
                        Show {allTips.length - 5} more <ChevronDown className="h-3 w-3" />
                      </>
                    )}
                  </button>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
