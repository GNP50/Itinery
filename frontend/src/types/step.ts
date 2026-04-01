export interface Step {
  id: string
  stepOrder: number
  placeName: string
  city?: string
  province?: string
  region?: string
  country?: string
  countryCode?: string
  latitude: number
  longitude: number
  osmId?: number
  notes?: string
  aiDescription?: string
  aiTips?: string
  distanceFromPrevKm?: number
  durationFromPrevMin?: number
  routeGeometryFromPrev?: any
  poiNearby?: any[]
  status: string
  arrivalDate?: string
}
