import axios from 'axios'
import type { QueueStatusResponse } from '../types/queue'

// Step types
export interface StepRequest {
  placeName: string
  notes?: string
  arrivalDate?: string
  preferences?: {
    interests?: string[]
    avoidHighways?: boolean
    generateAiTips?: boolean
  }
}

export interface StepResponse {
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
  preferences?: {
    interests?: string[]
    avoidHighways?: boolean
    generateAiTips?: boolean
  }
}

// Itinerary types
export interface ItineraryRequest {
  title: string
  description?: string
  travelMode: 'CAR' | 'BIKE' | 'WALK' | 'TRANSIT'
  steps: StepRequest[]
  preferences?: {
    interests?: string[]
    avoidHighways?: boolean
    generateAiTips?: boolean
  }
}

export interface ItineraryResponse {
  id: string
  accessToken: string
  title: string
  description?: string
  status: string
  queuePosition?: number
  estimatedCompletion?: string
  travelMode: string
  totalDistanceKm?: number
  totalDurationMinutes?: number
  currentStepIndex?: number
  steps: StepResponse[]
  preferences?: {
    interests?: string[]
    avoidHighways?: boolean
    generateAiTips?: boolean
  }
  aiSuggestions?: any
  createdAt: string
  updatedAt: string
}

export interface ItineraryStatusResponse {
  id: string
  status: string
  queuePosition?: number
  estimatedCompletion?: string
  progressPercent: number
}

export interface ItineraryCreateResponse {
  id: string
  accessToken: string
  status: string
  queuePosition?: number
  estimatedCompletion?: string
}


// Position update types
export interface PositionUpdateRequest {
  currentStepIndex?: number
  latitude?: number
  longitude?: number
}

export interface PositionUpdateResponse {
  id: string
  currentStepIndex?: number
  updatedAt: string
}

// Geo search types
export interface GeoSearchRequest {
  query: string
  countryCode?: string
  limit?: number
}

export interface GeoResult {
  displayName: string
  lat: number | string   // Java BigDecimal serialises as string in some configs
  lon: number | string
  osmId?: number | null
  city?: string
  province?: string
  region?: string
  country?: string
  countryCode?: string
  type?: string
}

export interface GeoSearchResponse {
  results: GeoResult[]
}

// Reverse geocode types
export interface ReverseGeocodeRequest {
  lat: number
  lon: number
}

export interface ReverseGeocodeResponse {
  displayName: string
  lat: number
  lon: number
  city?: string
  province?: string
  region?: string
  country?: string
  countryCode?: string
}

// Route types
export interface RouteRequest {
  fromLat: number
  fromLon: number
  toLat: number
  toLon: number
  travelMode: string
}

export interface RouteResponse {
  distanceKm: number
  durationMinutes: number
  geoJsonGeometry: any
  fromFallback: boolean
}

// POI types
export interface PoiRequest {
  lat: number
  lon: number
  radiusMeters: number
  category?: string
}

export interface PoiItem {
  osmId: number
  name: string
  category: string
  lat: number
  lon: number
  tags: Record<string, string>
}

export interface PoiResponse {
  pois: PoiItem[]
}

// AI Suggestion types
export interface AiSuggestionRequest {
  itineraryId: string
  accessToken: string
  placeName: string
  city?: string
  region?: string
  country?: string
  travelMode: string
  interests?: string[]
}

export interface AiSuggestionResponse {
  description: string
  tips: string[]
  mustSee: string[]
  localFood: string
  recommendedDuration: string
}

// AI Chat types
export interface AiChatRequest {
  itineraryId: string
  accessToken: string
  message: string
  conversation_history: ChatMessage[]
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface AiChatResponse {
  reply: string
  suggestions: string[]
}

// AI Route Optimization types
export interface OptimizedStep {
  originalIndex: number
  newIndex: number
  placeName: string
}

export interface AiRouteOptimizationRequest {
  itineraryId: string
  accessToken: string
  steps: string[]
}

export interface AiRouteOptimizationResponse {
  optimizedOrder: OptimizedStep[]
  reasoning: string
  estimatedTotalKm: number
}

// Admin types
export interface AdminItinerarySummary {
  id: string
  title: string
  status: string
  ownerId?: string
  ownerEmail?: string
  ownerName?: string
  ownerType?: string
  createdAt?: string
  queuePosition?: number
  accessToken?: string
}

export interface AdminPagedItineraries {
  items: AdminItinerarySummary[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

// Saga types
export interface SagaInstanceDto {
  id: string
  itineraryId: string
  currentState: string
  completedSteps: string[]
  failedStep?: string
  errorMessage?: string
  version: number
  retryCount: number
  maxRetries: number
  preferences?: string
  createdAt: string
  updatedAt: string
}

// Client interface
// List my itineraries types
export interface ItinerarySummary {
  id: string
  title: string
  status: string
  travelMode: string
  stepCount: number
  createdAt: string
}

export interface ListItinerariesResponse {
  itineraries: ItinerarySummary[]
}

export interface ApiClient {
  // Itineraries
  createItinerary: (data: ItineraryRequest) => Promise<ItineraryCreateResponse>
  cloneItinerary: (id: string, token: string) => Promise<ItineraryCreateResponse>
  getItinerary: (id: string, token: string) => Promise<ItineraryResponse>
  getItineraryStatus: (id: string, token: string) => Promise<ItineraryStatusResponse>
  listMyItineraries: () => Promise<ListItinerariesResponse>
  updateItinerary: (id: string, token: string, data: ItineraryRequest) => Promise<ItineraryResponse>
  deleteItinerary: (id: string, token: string) => Promise<void>
  updatePosition: (id: string, token: string, data: PositionUpdateRequest) => Promise<PositionUpdateResponse>
  getItinerarySagas: (itineraryId: string, token: string) => Promise<SagaInstanceDto[]>

  // Queue
  getQueueStatus: () => Promise<QueueStatusResponse>

  // Geo — /search returns a list of results
  searchPlaces: (query: string, countryCode?: string) => Promise<GeoResult[]>
  reverseGeocode: (lat: number, lon: number) => Promise<ReverseGeocodeResponse>
  getRoute: (data: RouteRequest) => Promise<RouteResponse>
  findPoi: (data: PoiRequest) => Promise<PoiResponse>

  // AI
  suggest: (data: AiSuggestionRequest) => Promise<AiSuggestionResponse>
  chat: (data: AiChatRequest) => Promise<AiChatResponse>
  optimizeRoute: (data: AiRouteOptimizationRequest) => Promise<AiRouteOptimizationResponse>

  // Admin
  getAdminItineraries: (page: number, size: number) => Promise<AdminPagedItineraries>
  getSagaInstance: (sagaId: string) => Promise<SagaInstanceDto>
}

export function createClient(baseURL: string): ApiClient {
  const api = axios.create({
    baseURL,
    headers: {
      'Content-Type': 'application/json',
    },
  })

  // Attach JWT from storage on every request
  api.interceptors.request.use((config) => {
    // Try registered token first
    let token = localStorage.getItem('itinerary_registered_token')

    // Fall back to current anonymous session JWT
    if (!token) {
      try {
        const sessionsRaw = localStorage.getItem('itinerary_sessions')
        if (sessionsRaw) {
          const sessions = JSON.parse(sessionsRaw)
          if (sessions.currentSessionId && sessions.sessions[sessions.currentSessionId]) {
            token = sessions.sessions[sessions.currentSessionId].jwt
          }
        }
      } catch (err) {
        console.warn('Failed to load JWT from sessions:', err)
      }
    }

    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })

  // On 401, clear the stored session and signal the app to re-authenticate
  api.interceptors.response.use(
    (res) => res,
    (err) => {
      if (err.response?.status === 401) {
        localStorage.removeItem('itinerary_registered_token')
        localStorage.removeItem('itinerary_registered_user')
        window.dispatchEvent(new CustomEvent('auth:unauthorized'))
      }
      return Promise.reject(err)
    }
  )

  return {
    // Itineraries
    createItinerary: (data) => api.post('/api/v1/itineraries', data).then((res) => res.data),
    cloneItinerary: (id, token) => {
      const url = token 
        ? `/api/v1/itineraries/${id}/clone?token=${token}`
        : `/api/v1/itineraries/${id}/clone`
      return api.post(url).then((res) => res.data)
    },
    getItinerary: (id, token) => {
      const url = token ? `/api/v1/itineraries/${id}?token=${token}` : `/api/v1/itineraries/${id}`
      return api.get(url).then((res) => res.data)
    },
    getItineraryStatus: (id, token) => {
      const url = token ? `/api/v1/itineraries/${id}/status?token=${token}` : `/api/v1/itineraries/${id}/status`
      return api.get(url).then((res) => res.data)
    },
    listMyItineraries: () => api.get('/api/v1/itineraries').then((res) => res.data),
    updateItinerary: (id, token, data) => {
      const url = token ? `/api/v1/itineraries/${id}?token=${token}` : `/api/v1/itineraries/${id}`
      return api.put(url, data).then((res) => res.data)
    },
    deleteItinerary: (id, token) => {
      const url = token ? `/api/v1/itineraries/${id}?token=${token}` : `/api/v1/itineraries/${id}`
      return api.delete(url)
    },
    updatePosition: (id, token, data) => {
      const url = token ? `/api/v1/itineraries/${id}/position?token=${token}` : `/api/v1/itineraries/${id}/position`
      return api.patch(url, data).then((res) => res.data)
    },
    getItinerarySagas: (itineraryId, token) => {
      const url = token ? `/api/v1/itineraries/${itineraryId}/saga?token=${token}` : `/api/v1/itineraries/${itineraryId}/saga`
      return api.get(url).then((res) => res.data)
    },

    // Queue
    getQueueStatus: () => api.get('/api/v1/queue/status').then((res) => res.data),

    // Geo — /search returns a list of GeoResults
    searchPlaces: (query, countryCode) => {
      const url = countryCode
        ? `/api/v1/geo/search?q=${encodeURIComponent(query)}&country_code=${countryCode}`
        : `/api/v1/geo/search?q=${encodeURIComponent(query)}`
      return api.get(url).then((res) => res.data as GeoResult[])
    },
    reverseGeocode: (lat, lon) => api.get(`/api/v1/geo/reverse?lat=${lat}&lon=${lon}`).then((res) => res.data),
    getRoute: (data) => {
      const params = new URLSearchParams(data as any)
      return api.get(`/api/v1/geo/route?${params.toString()}`).then((res) => res.data)
    },
    findPoi: (data) => {
      const params = new URLSearchParams(data as any)
      return api.get(`/api/v1/geo/poi?${params.toString()}`).then((res) => res.data)
    },

    // AI
    suggest: (data) => api.post('/api/v1/ai/suggest', data).then((res) => res.data),
    chat: (data) => api.post('/api/v1/ai/chat', data).then((res) => res.data),
    optimizeRoute: (data) => api.post('/api/v1/ai/optimize-route', data).then((res) => res.data),

    // Admin
    getAdminItineraries: (page, size) =>
      api.get(`/api/v1/admin/itineraries?page=${page}&size=${size}`).then((res) => res.data),
    getSagaInstance: (sagaId) =>
      api.get(`/api/v1/admin/saga/${sagaId}`).then((res) => res.data),
  }
}
