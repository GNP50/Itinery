import React, { useState, useEffect, useRef } from 'react'
import { Search, MapPin } from 'lucide-react'
import { toast } from 'react-hot-toast'
import { useClient } from '../../contexts/ClientContext'

export interface GeoSearchResult {
  placeId: string
  displayName: string
  lat: number
  lon: number
  osmId: number
  type: string
  city?: string
  region?: string
  country?: string
  countryCode?: string
}

interface PlaceAutocompleteProps {
  value: string
  onChange: (placeName: string | null) => void
  onSelect?: (place: GeoSearchResult) => void
  placeholder?: string
  debounceMs?: number
}

export function PlaceAutocomplete({
  value,
  onChange,
  onSelect,
  placeholder = 'Enter a place...',
  debounceMs = 300,
}: PlaceAutocompleteProps) {
  const client = useClient()
  const [results, setResults] = useState<GeoSearchResult[]>([])
  const [isSearching, setIsSearching] = useState(false)
  const [showResults, setShowResults] = useState(false)
  const [selectedResult, setSelectedResult] = useState<GeoSearchResult | null>(null)
  const wrapperRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(event.target as Node)) {
        setShowResults(false)
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  useEffect(() => {
    if (value.length < 2) {
      setResults([])
      return
    }

    const timer = setTimeout(async () => {
      try {
        setIsSearching(true)
        const items = await client.searchPlaces(value)
        setResults(
          items.map((item) => ({
            placeId: item.osmId?.toString() ?? item.displayName,
            displayName: item.displayName,
            lat: typeof item.lat === 'number' ? item.lat : Number(item.lat),
            lon: typeof item.lon === 'number' ? item.lon : Number(item.lon),
            osmId: item.osmId ?? 0,
            type: item.type ?? '',
            city: item.city,
            region: item.region,
            country: item.country,
            countryCode: item.countryCode,
          }))
        )
      } catch (error) {
        toast.error('Failed to search locations')
      } finally {
        setIsSearching(false)
      }
    }, debounceMs)

    return () => clearTimeout(timer)
  }, [value, debounceMs])

  const handleSelect = (result: GeoSearchResult) => {
    setSelectedResult(result)
    onChange(result.displayName)
    setShowResults(false)
    onSelect?.(result)
  }

  const renderHighlight = (text: string, query: string) => {
    if (!query) return text

    const regex = new RegExp(`(${query})`, 'gi')
    const parts = text.split(regex)

    return parts.map((part, i) =>
      regex.test(part) ? (
        <span key={i} className="font-bold text-primary">
          {part}
        </span>
      ) : (
        <span key={i}>{part}</span>
      )
    )
  }

  return (
    <div ref={wrapperRef} className="relative">
      <div className="relative">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <input
          type="text"
          value={value}
          onChange={(e) => {
            onChange(e.target.value)
            setShowResults(true)
          }}
          onFocus={() => setShowResults(true)}
          placeholder={placeholder}
          className="input pl-10"
        />
        {selectedResult && (
          <button
            onClick={() => {
              setSelectedResult(null)
              onChange('')
            }}
            className="absolute right-3 top-1/2 h-5 w-5 -translate-y-1/2 rounded-full bg-muted hover:bg-muted/80 flex items-center justify-center"
          >
            <span className="text-xs text-muted-foreground">✕</span>
          </button>
        )}
      </div>

      {showResults && value.length >= 2 && (
        <div className="absolute z-50 mt-1 w-full overflow-hidden rounded-xl border border-border bg-background shadow-lg">
          {isSearching ? (
            <div className="p-4 text-center text-sm text-muted-foreground">
              Searching...
            </div>
          ) : results.length === 0 ? (
            <div className="p-4 text-center text-sm text-muted-foreground">
              No locations found
            </div>
          ) : (
            <ul className="max-h-60 overflow-y-auto">
              {results.map((result) => (
                <li
                  key={result.placeId}
                  className="cursor-pointer border-b border-border last:border-0 hover:bg-accent"
                  onClick={() => handleSelect(result)}
                >
                  <div className="p-3">
                    <div className="flex items-start gap-2">
                      <MapPin className="mt-1 h-4 w-4 text-primary shrink-0" />
                      <div>
                        <p className="text-sm font-medium text-foreground">
                          {renderHighlight(result.displayName, value)}
                        </p>
                        <p className="text-xs text-muted-foreground">
                          {result.type} • {result.city}, {result.country}
                        </p>
                      </div>
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
