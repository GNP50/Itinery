import React, { useState } from 'react'
import { MapPin, ChevronDown, ChevronUp, Globe, Compass } from 'lucide-react'
import type { ItineraryStep } from '../../types/itinerary'

interface Props {
  step: ItineraryStep
}

export function LocationHierarchyCard({ step }: Props) {
  const [expanded, setExpanded] = useState(false)

  const hasDetailedLocation = step.city || step.province || step.region || step.country

  // Get country flag emoji
  const getFlagEmoji = (countryCode?: string) => {
    if (!countryCode || countryCode.length !== 2) return null
    const codePoints = countryCode
      .toUpperCase()
      .split('')
      .map((char) => 127397 + char.charCodeAt(0))
    return String.fromCodePoint(...codePoints)
  }

  const flag = getFlagEmoji(step.countryCode)

  return (
    <div className="card card-body animate-fade-in">
      <button
        onClick={() => setExpanded(!expanded)}
        className="flex items-center justify-between w-full text-left group"
      >
        <div className="flex items-center gap-2">
          <div className="h-8 w-8 rounded-lg bg-primary/10 flex items-center justify-center">
            <MapPin className="h-4 w-4 text-primary" />
          </div>
          <h3 className="font-semibold text-sm">Location Details</h3>
        </div>
        {expanded ? (
          <ChevronUp className="h-4 w-4 text-muted-foreground group-hover:text-foreground transition-colors" />
        ) : (
          <ChevronDown className="h-4 w-4 text-muted-foreground group-hover:text-foreground transition-colors" />
        )}
      </button>

      {/* Compact view */}
      {!expanded && hasDetailedLocation && (
        <div className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
          {flag && <span className="text-lg">{flag}</span>}
          <span>
            {[step.city, step.region, step.country].filter(Boolean).join(', ')}
          </span>
        </div>
      )}

      {/* Expanded view */}
      {expanded && (
        <div className="mt-4 space-y-3 animate-fade-in">
          {/* Place name */}
          <div className="flex items-start gap-3">
            <MapPin className="h-4 w-4 text-muted-foreground mt-0.5 flex-shrink-0" />
            <div className="min-w-0 flex-1">
              <p className="text-xs text-muted-foreground">Place</p>
              <p className="font-medium">{step.placeName}</p>
            </div>
          </div>

          {/* Location hierarchy */}
          {hasDetailedLocation && (
            <div className="flex items-start gap-3">
              <Globe className="h-4 w-4 text-muted-foreground mt-0.5 flex-shrink-0" />
              <div className="min-w-0 flex-1">
                <p className="text-xs text-muted-foreground mb-1">Location Hierarchy</p>
                <div className="space-y-1">
                  {step.city && (
                    <p className="text-sm">
                      <span className="text-muted-foreground">City:</span>{' '}
                      <span className="font-medium">{step.city}</span>
                    </p>
                  )}
                  {step.province && (
                    <p className="text-sm">
                      <span className="text-muted-foreground">Province:</span>{' '}
                      <span className="font-medium">{step.province}</span>
                    </p>
                  )}
                  {step.region && (
                    <p className="text-sm">
                      <span className="text-muted-foreground">Region:</span>{' '}
                      <span className="font-medium">{step.region}</span>
                    </p>
                  )}
                  {step.country && (
                    <p className="text-sm">
                      <span className="text-muted-foreground">Country:</span>{' '}
                      <span className="font-medium">
                        {flag && `${flag} `}
                        {step.country}
                        {step.countryCode && ` (${step.countryCode})`}
                      </span>
                    </p>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* Coordinates */}
          <div className="flex items-start gap-3">
            <Compass className="h-4 w-4 text-muted-foreground mt-0.5 flex-shrink-0" />
            <div className="min-w-0 flex-1">
              <p className="text-xs text-muted-foreground">Coordinates</p>
              <p className="font-mono text-sm">
                {step.latitude.toFixed(6)}, {step.longitude.toFixed(6)}
              </p>
              {step.osmId && (
                <p className="text-xs text-muted-foreground mt-1">
                  OSM ID: {step.osmId}
                </p>
              )}
            </div>
          </div>

          {/* Quick action */}
          <div className="pt-2 border-t border-border">
            <a
              href={`https://www.openstreetmap.org/?mlat=${step.latitude}&mlon=${step.longitude}&zoom=15`}
              target="_blank"
              rel="noopener noreferrer"
              className="text-sm text-primary hover:underline inline-flex items-center gap-1"
            >
              View on OpenStreetMap
              <Globe className="h-3 w-3" />
            </a>
          </div>
        </div>
      )}
    </div>
  )
}
