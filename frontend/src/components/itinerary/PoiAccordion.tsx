import React, { useState, useMemo } from 'react'
import { MapPin, ChevronDown, ChevronUp } from 'lucide-react'
import type { PoiNearby } from '../../types/itinerary'
import {
  sortPoiByDistance,
  groupPoiByCategory,
  getPoiIconByCategory,
  formatPoiDistance,
} from '../../utils/poiUtils'

interface Props {
  pois: PoiNearby[]
  stepLocation: { lat: number; lon: number }
  previewCount?: number
}

export function PoiAccordion({ pois, stepLocation, previewCount = 3 }: Props) {
  const [expanded, setExpanded] = useState(false)

  // Sort POIs by distance
  const sortedPois = useMemo(
    () => sortPoiByDistance(pois, stepLocation.lat, stepLocation.lon),
    [pois, stepLocation]
  )

  // Group by category
  const groupedPois = useMemo(() => groupPoiByCategory(sortedPois), [sortedPois])

  if (pois.length === 0) {
    return (
      <div className="rounded-xl border border-border bg-muted/30 p-6 text-center">
        <MapPin className="h-8 w-8 mx-auto mb-2 text-muted-foreground opacity-50" />
        <p className="text-sm text-muted-foreground">No nearby points of interest</p>
      </div>
    )
  }

  const poisToShow = expanded ? sortedPois : sortedPois.slice(0, previewCount)
  const hasMore = sortedPois.length > previewCount

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <MapPin className="h-4 w-4 text-muted-foreground" />
          <h4 className="font-semibold text-sm">Nearby Points of Interest</h4>
          <span className="text-xs text-muted-foreground">({sortedPois.length})</span>
        </div>

        {hasMore && (
          <button
            onClick={() => setExpanded(!expanded)}
            className="text-xs text-primary hover:underline flex items-center gap-1"
          >
            {expanded ? (
              <>
                Show less <ChevronUp className="h-3 w-3" />
              </>
            ) : (
              <>
                Show all <ChevronDown className="h-3 w-3" />
              </>
            )}
          </button>
        )}
      </div>

      {/* POI List */}
      <div className="space-y-2">
        {poisToShow.map((poi) => {
          const Icon = getPoiIconByCategory(poi.category)
          return (
            <div
              key={`${poi.osmId}-${poi.name}`}
              className="flex items-start gap-3 p-3 rounded-lg bg-muted/30 hover:bg-muted/50 transition-colors"
            >
              <div className="h-8 w-8 rounded-lg bg-primary/10 flex items-center justify-center flex-shrink-0">
                <Icon className="h-4 w-4 text-primary" />
              </div>
              <div className="flex-1 min-w-0">
                <p className="font-medium text-sm truncate">{poi.name}</p>
                <div className="flex items-center gap-2 mt-1">
                  <span className="inline-block px-2 py-0.5 rounded-md text-xs bg-primary/10 text-primary border border-primary/20">
                    {poi.category}
                  </span>
                  <span className="text-xs text-muted-foreground">
                    {formatPoiDistance(poi.distance)}
                  </span>
                </div>
              </div>
            </div>
          )
        })}
      </div>

      {/* Category breakdown when expanded */}
      {expanded && Object.keys(groupedPois).length > 1 && (
        <div className="pt-3 border-t border-border">
          <p className="text-xs text-muted-foreground mb-2">By Category:</p>
          <div className="flex flex-wrap gap-2">
            {Object.entries(groupedPois).map(([category, categoryPois]) => (
              <span
                key={category}
                className="inline-flex items-center px-2 py-1 rounded-md text-xs bg-muted text-muted-foreground"
              >
                {category} ({categoryPois.length})
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
