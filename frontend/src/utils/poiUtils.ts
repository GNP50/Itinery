import {
  Landmark,
  Utensils,
  ShoppingBag,
  Hotel,
  Fuel,
  MapPin,
  Building,
  Church,
  Coffee,
  Music,
  Heart,
  LucideIcon,
} from 'lucide-react'
import type { PoiNearby } from '../types/itinerary'

/**
 * Calculate distance between two coordinates using Haversine formula
 * @param lat1 Latitude of first point
 * @param lon1 Longitude of first point
 * @param lat2 Latitude of second point
 * @param lon2 Longitude of second point
 * @returns Distance in kilometers
 */
export function calculatePoiDistance(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number
): number {
  const R = 6371 // Earth's radius in km
  const dLat = ((lat2 - lat1) * Math.PI) / 180
  const dLon = ((lon2 - lon1) * Math.PI) / 180
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2)
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  return R * c
}

/**
 * Group POIs by their category
 * @param pois Array of POI objects
 * @returns Object with categories as keys and arrays of POIs as values
 */
export function groupPoiByCategory(pois: PoiNearby[]): Record<string, PoiNearby[]> {
  return pois.reduce((groups, poi) => {
    const category = poi.category || 'Other'
    if (!groups[category]) {
      groups[category] = []
    }
    groups[category].push(poi)
    return groups
  }, {} as Record<string, PoiNearby[]>)
}

/**
 * Sort POIs by distance from a reference point
 * @param pois Array of POI objects
 * @param refLat Reference latitude
 * @param refLon Reference longitude
 * @returns Sorted array of POIs with distance property
 */
export function sortPoiByDistance(
  pois: PoiNearby[],
  refLat: number,
  refLon: number
): (PoiNearby & { distance: number })[] {
  return pois
    .map((poi) => ({
      ...poi,
      distance: calculatePoiDistance(refLat, refLon, poi.lat, poi.lon),
    }))
    .sort((a, b) => a.distance - b.distance)
}

/**
 * Filter POIs based on tag criteria
 * @param pois Array of POI objects
 * @param tagKey Tag key to filter by
 * @param tagValue Optional tag value to match
 * @returns Filtered array of POIs
 */
export function filterPoiByTags(
  pois: PoiNearby[],
  tagKey: string,
  tagValue?: string
): PoiNearby[] {
  return pois.filter((poi) => {
    if (!poi.tags) return false
    const hasKey = tagKey in poi.tags
    if (!tagValue) return hasKey
    return hasKey && poi.tags[tagKey] === tagValue
  })
}

/**
 * Search POIs by name
 * @param pois Array of POI objects
 * @param query Search query
 * @returns Filtered array of POIs matching the query
 */
export function searchPoiByName(pois: PoiNearby[], query: string): PoiNearby[] {
  const lowerQuery = query.toLowerCase().trim()
  if (!lowerQuery) return pois

  return pois.filter((poi) => poi.name.toLowerCase().includes(lowerQuery))
}

/**
 * Get appropriate icon for POI category
 * @param category POI category string
 * @returns Lucide icon component
 */
export function getPoiIconByCategory(category: string): LucideIcon {
  const categoryLower = category.toLowerCase()

  if (categoryLower.includes('tourism') || categoryLower.includes('attraction')) {
    return Landmark
  }
  if (categoryLower.includes('food') || categoryLower.includes('restaurant')) {
    return Utensils
  }
  if (categoryLower.includes('cafe') || categoryLower.includes('coffee')) {
    return Coffee
  }
  if (categoryLower.includes('shop') || categoryLower.includes('store')) {
    return ShoppingBag
  }
  if (categoryLower.includes('hotel') || categoryLower.includes('accommodation')) {
    return Hotel
  }
  if (categoryLower.includes('fuel') || categoryLower.includes('gas')) {
    return Fuel
  }
  if (categoryLower.includes('church') || categoryLower.includes('religion')) {
    return Church
  }
  if (categoryLower.includes('entertainment') || categoryLower.includes('music')) {
    return Music
  }
  if (categoryLower.includes('health') || categoryLower.includes('hospital')) {
    return Heart
  }
  if (categoryLower.includes('building') || categoryLower.includes('office')) {
    return Building
  }

  return MapPin // Default icon
}

/**
 * Format distance for display
 * @param distanceKm Distance in kilometers
 * @returns Formatted string (e.g., "150 m", "2.3 km")
 */
export function formatPoiDistance(distanceKm: number): string {
  if (distanceKm < 1) {
    return `${Math.round(distanceKm * 1000)} m`
  }
  return `${distanceKm.toFixed(1)} km`
}

/**
 * Extract useful tags from POI for display
 * @param poi POI object
 * @returns Object with formatted display tags
 */
export function extractPoiTags(poi: PoiNearby): Record<string, string> {
  const tags: Record<string, string> = {}
  const { tags: poiTags } = poi

  if (!poiTags) return tags

  // Opening hours
  if (poiTags.opening_hours) {
    tags['Opening Hours'] = poiTags.opening_hours
  }

  // Contact info
  if (poiTags.phone) {
    tags['Phone'] = poiTags.phone
  }
  if (poiTags.website || poiTags.url) {
    tags['Website'] = poiTags.website || poiTags.url || ''
  }

  // Address (prefer colon format, fallback to underscore)
  const street = poiTags['addr:street'] || poiTags.addr_street
  const housenumber = poiTags['addr:housenumber'] || poiTags.addr_housenumber
  const city = poiTags['addr:city'] || poiTags.addr_city
  const postcode = poiTags['addr:postcode'] || poiTags.addr_postcode

  if (street || housenumber) {
    tags['Address'] = [housenumber, street].filter(Boolean).join(' ')
  }
  if (city) {
    tags['City'] = city
  }
  if (postcode) {
    tags['Postal Code'] = postcode
  }

  // Accessibility
  if (poiTags.wheelchair) {
    tags['Wheelchair Access'] = poiTags.wheelchair
  }

  // Amenities
  if (poiTags.internet_access) {
    tags['Internet'] = poiTags.internet_access
  }
  if (poiTags.air_conditioning) {
    tags['Air Conditioning'] = poiTags.air_conditioning
  }

  // Cuisine
  if (poiTags.cuisine) {
    tags['Cuisine'] = poiTags.cuisine
  }

  return tags
}

/**
 * Get all unique categories from a list of POIs
 * @param pois Array of POI objects
 * @returns Array of unique category strings
 */
export function getUniqueCategories(pois: PoiNearby[]): string[] {
  const categories = new Set(pois.map((poi) => poi.category || 'Other'))
  return Array.from(categories).sort()
}
