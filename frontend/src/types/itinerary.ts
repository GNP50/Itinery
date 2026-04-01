export interface PoiTag {
  [key: string]: string | undefined
  name?: string
  amenity?: string
  tourism?: string
  cuisine?: string
  opening_hours?: string
  phone?: string
  website?: string
  url?: string
  wheelchair?: string
  addr_street?: string
  addr_housenumber?: string
  addr_city?: string
  addr_postcode?: string
  addr_country?: string
  'addr:street'?: string
  'addr:housenumber'?: string
  'addr:city'?: string
  'addr:postcode'?: string
  'addr:country'?: string
  check_date?: string
  start_date?: string
  internet_access?: string
  air_conditioning?: string
}

export interface PoiNearby {
  lat: number
  lon: number
  name: string
  tags: PoiTag
  osmId: number
  category: string
}

export interface RouteGeometry {
  type: string
  coordinates: number[][]
}

export interface ItineraryStep {
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
  routeGeometryFromPrev?: RouteGeometry
  poiNearby?: PoiNearby[]
  status: string
  arrivalDate?: string
  preferences?: ItineraryPreferences
}

export interface ItineraryPreferences {
  interests?: string[]
  avoidHighways?: boolean
  generateAiTips?: boolean
}

export interface AiSuggestions {
  estimatedBudget?: string
  generalTips?: string[]
  [key: string]: any
}

export interface Itinerary {
  id: string
  accessToken: string
  title: string
  description?: string
  status: string
  queuePosition?: number
  estimatedCompletion?: string
  travelMode: string
  preferences?: ItineraryPreferences
  aiSuggestions?: AiSuggestions
  totalDistanceKm?: number
  totalDurationMinutes?: number
  currentStepIndex?: number
  steps: ItineraryStep[]
  createdAt: string
  updatedAt: string
}

export type ViewMode = 'overview' | 'timeline' | 'grid'
