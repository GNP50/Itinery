import React, { useState, useMemo, useEffect } from 'react'
import { Search, MapPin, ChevronDown, ChevronUp, ExternalLink, ChevronLeft, ChevronRight } from 'lucide-react'
import type { PoiNearby } from '../../types/itinerary'
import {
  sortPoiByDistance,
  searchPoiByName,
  getPoiIconByCategory,
  formatPoiDistance,
  extractPoiTags,
  getUniqueCategories,
} from '../../utils/poiUtils'
import { PoiCategoryFilter } from './PoiCategoryFilter'

interface Props {
  pois: PoiNearby[]
  stepLocation: { lat: number; lon: number }
}

export function PoiDetailGrid({ pois, stepLocation }: Props) {
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedCategories, setSelectedCategories] = useState<string[]>([])
  const [expandedPois, setExpandedPois] = useState<Set<number>>(new Set())
  const [currentPage, setCurrentPage] = useState(1)
  const [itemsPerPage, setItemsPerPage] = useState(12)

  // Get unique categories
  const categories = useMemo(() => getUniqueCategories(pois), [pois])

  // Filter and sort POIs
  const filteredPois = useMemo(() => {
    let filtered = pois

    // Apply search filter
    if (searchQuery) {
      filtered = searchPoiByName(filtered, searchQuery)
    }

    // Apply category filter
    const isAllSelected = selectedCategories.length === 0 || selectedCategories.length === categories.length
    if (!isAllSelected) {
      filtered = filtered.filter((poi) => selectedCategories.includes(poi.category))
    }

    // Sort by distance
    return sortPoiByDistance(filtered, stepLocation.lat, stepLocation.lon)
  }, [pois, searchQuery, selectedCategories, stepLocation, categories])

  // Pagination calculations
  const totalPages = Math.ceil(filteredPois.length / itemsPerPage)
  const startIndex = (currentPage - 1) * itemsPerPage
  const endIndex = startIndex + itemsPerPage
  const paginatedPois = filteredPois.slice(startIndex, endIndex)

  // Reset to page 1 when filters change
  useEffect(() => {
    setCurrentPage(1)
  }, [searchQuery, selectedCategories])

  const togglePoiExpanded = (osmId: number) => {
    setExpandedPois((prev) => {
      const newSet = new Set(prev)
      if (newSet.has(osmId)) {
        newSet.delete(osmId)
      } else {
        newSet.add(osmId)
      }
      return newSet
    })
  }

  if (pois.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <MapPin className="h-12 w-12 mb-4 text-muted-foreground opacity-50" />
        <p className="text-lg font-semibold text-foreground">No Points of Interest</p>
        <p className="text-sm text-muted-foreground mt-1">
          No nearby POIs found for this location
        </p>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {/* Search bar */}
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search by name..."
          className="w-full pl-10 pr-4 py-2 rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20"
        />
      </div>

      {/* Category filter */}
      <PoiCategoryFilter
        categories={categories}
        selected={selectedCategories}
        onChange={setSelectedCategories}
      />

      {/* Results count and items per page */}
      <div className="flex items-center justify-between text-sm">
        <p className="text-muted-foreground">
          {filteredPois.length} {filteredPois.length === 1 ? 'result' : 'results'}
          {filteredPois.length > 0 && (
            <span className="ml-2 text-xs">
              (showing {startIndex + 1}-{Math.min(endIndex, filteredPois.length)})
            </span>
          )}
        </p>
        {filteredPois.length > 12 && (
          <select
            value={itemsPerPage}
            onChange={(e) => {
              setItemsPerPage(Number(e.target.value))
              setCurrentPage(1)
            }}
            className="text-xs border border-border rounded-md px-2 py-1 bg-background"
          >
            <option value={12}>12 per page</option>
            <option value={24}>24 per page</option>
            <option value={48}>48 per page</option>
          </select>
        )}
      </div>

      {/* POI Grid */}
      {filteredPois.length === 0 ? (
        <div className="text-center py-8">
          <p className="text-muted-foreground">No POIs match your filters</p>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {paginatedPois.map((poi) => {
            const Icon = getPoiIconByCategory(poi.category)
            const isExpanded = expandedPois.has(poi.osmId)
            const tags = extractPoiTags(poi)
            const hasTags = Object.keys(tags).length > 0

            return (
              <div
                key={`${poi.osmId}-${poi.name}`}
                className="card card-body hover:shadow-lg transition-shadow"
              >
                <div className="flex items-start gap-3">
                  <div className="h-10 w-10 rounded-lg bg-primary/10 flex items-center justify-center flex-shrink-0">
                    <Icon className="h-5 w-5 text-primary" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <h4 className="font-semibold text-sm mb-1 line-clamp-2">{poi.name}</h4>
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="inline-block px-2 py-0.5 rounded-md text-xs bg-primary/10 text-primary border border-primary/20">
                        {poi.category}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {formatPoiDistance(poi.distance)} away
                      </span>
                    </div>
                  </div>
                </div>

                {/* Tags section */}
                {hasTags && (
                  <div className="mt-3 pt-3 border-t border-border">
                    <button
                      onClick={() => togglePoiExpanded(poi.osmId)}
                      className="flex items-center justify-between w-full text-xs text-primary hover:underline"
                    >
                      <span>
                        {isExpanded ? 'Hide' : 'Show'} details ({Object.keys(tags).length})
                      </span>
                      {isExpanded ? (
                        <ChevronUp className="h-3 w-3" />
                      ) : (
                        <ChevronDown className="h-3 w-3" />
                      )}
                    </button>

                    {isExpanded && (
                      <div className="mt-2 space-y-2 animate-fade-in">
                        {Object.entries(tags).map(([key, value]) => (
                          <div key={key} className="text-xs">
                            <span className="text-muted-foreground">{key}:</span>{' '}
                            {key === 'Website' ? (
                              <a
                                href={value}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="text-primary hover:underline inline-flex items-center gap-1"
                              >
                                Visit
                                <ExternalLink className="h-3 w-3" />
                              </a>
                            ) : (
                              <span className="font-medium">{value}</span>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}

                {/* View on map link */}
                <div className="mt-3 pt-3 border-t border-border">
                  <a
                    href={`https://www.openstreetmap.org/?mlat=${poi.lat}&mlon=${poi.lon}&zoom=18`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-xs text-primary hover:underline inline-flex items-center gap-1"
                  >
                    <MapPin className="h-3 w-3" />
                    View on map
                  </a>
                </div>
              </div>
            )
            })}
          </div>

          {/* Pagination Controls */}
          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-2 pt-4 border-t border-border">
              <button
                onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                disabled={currentPage === 1}
                className="btn btn-sm btn-ghost disabled:opacity-50 disabled:cursor-not-allowed"
                aria-label="Previous page"
              >
                <ChevronLeft className="h-4 w-4" />
              </button>

              <div className="flex items-center gap-1">
                {/* Show page numbers */}
                {Array.from({ length: totalPages }, (_, i) => i + 1).map((page) => {
                  // Show first, last, current, and adjacent pages
                  const showPage =
                    page === 1 ||
                    page === totalPages ||
                    Math.abs(page - currentPage) <= 1

                  // Show ellipsis
                  if (!showPage && (page === 2 || page === totalPages - 1)) {
                    return (
                      <span key={page} className="px-2 text-muted-foreground">
                        …
                      </span>
                    )
                  }

                  if (!showPage) return null

                  return (
                    <button
                      key={page}
                      onClick={() => setCurrentPage(page)}
                      className={`h-8 w-8 rounded-md text-sm font-medium transition-colors ${
                        currentPage === page
                          ? 'bg-primary text-white'
                          : 'hover:bg-muted'
                      }`}
                    >
                      {page}
                    </button>
                  )
                })}
              </div>

              <button
                onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                disabled={currentPage === totalPages}
                className="btn btn-sm btn-ghost disabled:opacity-50 disabled:cursor-not-allowed"
                aria-label="Next page"
              >
                <ChevronRight className="h-4 w-4" />
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
