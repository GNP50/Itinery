import type { ItineraryResponse, StepResponse } from '../api/client'
import type { Itinerary, ItineraryStep } from '../types/itinerary'

function mapStep(s: StepResponse): ItineraryStep {
  return {
    id: s.id,
    stepOrder: s.stepOrder,
    placeName: s.placeName,
    city: s.city,
    province: s.province,
    region: s.region,
    country: s.country,
    countryCode: s.countryCode,
    latitude: s.latitude,
    longitude: s.longitude,
    osmId: s.osmId,
    notes: s.notes,
    aiDescription: s.aiDescription,
    aiTips: s.aiTips,
    distanceFromPrevKm: s.distanceFromPrevKm,
    durationFromPrevMin: s.durationFromPrevMin,
    routeGeometryFromPrev: s.routeGeometryFromPrev,
    poiNearby: s.poiNearby,
    status: s.status,
    arrivalDate: s.arrivalDate,
    preferences: s.preferences,
  }
}

export function mapResponseToItinerary(res: ItineraryResponse): Itinerary {
  return {
    id: res.id,
    accessToken: res.accessToken,
    title: res.title,
    description: res.description,
    status: res.status,
    queuePosition: res.queuePosition,
    estimatedCompletion: res.estimatedCompletion,
    travelMode: res.travelMode,
    totalDistanceKm: res.totalDistanceKm,
    totalDurationMinutes: res.totalDurationMinutes,
    currentStepIndex: res.currentStepIndex,
    aiSuggestions: res.aiSuggestions,
    steps: res.steps.map(mapStep),
    createdAt: res.createdAt,
    updatedAt: res.updatedAt,
  }
}
