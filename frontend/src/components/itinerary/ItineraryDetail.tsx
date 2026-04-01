import React, { useState } from 'react'
import { MapContainer, TileLayer, Marker, Popup, Polyline } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import { Itinerary } from '../../types/itinerary'
import { MapPin, Clock, Info, Footprints } from 'lucide-react'

interface ItineraryDetailProps {
  itinerary: Itinerary
  onMarkVisited: (stepId: string) => void
}


const STEP_BG_COLORS = {
  PENDING: 'bg-slate-100 text-slate-600',
  CURRENT: 'bg-indigo-100 text-indigo-600',
  VISITED: 'bg-green-100 text-green-600',
  SKIPPED: 'bg-gray-100 text-gray-500',
}

export function ItineraryDetail({ itinerary, onMarkVisited }: ItineraryDetailProps) {
  const [selectedStepId, setSelectedStepId] = useState<string | null>(null)

  // Calculate center and bounds for map
  const center = itinerary.steps.length > 0
    ? [itinerary.steps[0].latitude, itinerary.steps[0].longitude] as [number, number]
    : [48.8566, 2.3522] as [number, number]

  // Build polyline coordinates from route geometries
  const polylineCoords = itinerary.steps.reduce<[number, number][]>((coords, step) => {
    if (step.routeGeometryFromPrev && step.routeGeometryFromPrev.coordinates) {
      step.routeGeometryFromPrev.coordinates.forEach((coord: number[]) => {
        if (coord.length >= 2) {
          coords.push([coord[1], coord[0]])
        }
      })
    }
    return coords
  }, [])

  const steps = itinerary.steps || []

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 animate-fade-in">
      {/* Map Section */}
      <div className="lg:col-span-2 space-y-4">
        <div className="card overflow-hidden h-[500px] lg:h-[600px]">
          <MapContainer
            center={center}
            zoom={13}
            zoomControl={false}
            className="h-full w-full"
          >
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            {polylineCoords.length > 0 && (
              <Polyline
                positions={polylineCoords}
                pathOptions={{ color: '#6366f1', weight: 4, opacity: 0.8 }}
              />
            )}
            {steps.map((step) => (
              <Marker
                key={step.id}
                position={[step.latitude, step.longitude]}
                eventHandlers={{
                  click: () => setSelectedStepId(step.id),
                }}
              >
                <Popup className="rounded-xl">
                  <div className="p-1 min-w-[200px]">
                    <div className="flex items-center gap-2 mb-2">
                      <MapPin className="h-4 w-4 text-primary" />
                      <span className="font-bold">{step.placeName}</span>
                    </div>
                    <p className="text-sm text-muted-foreground mb-2">
                      {step.city}, {step.country}
                    </p>
                    <div className="flex items-center gap-2">
                      <span className={`badge ${STEP_BG_COLORS[step.status as keyof typeof STEP_BG_COLORS] || 'bg-gray-100'}`}>
                        {step.status}
                      </span>
                    </div>
                  </div>
                </Popup>
              </Marker>
            ))}
          </MapContainer>
        </div>

        {/* Route Info */}
        <div className="card card-body">
          <div>
            <h3 className="font-bold text-lg">{itinerary.title}</h3>
            {itinerary.description && (
              <p className="text-muted-foreground mt-1">{itinerary.description}</p>
            )}
          </div>
        </div>
      </div>

      {/* Sidebar Details */}
      <div className="space-y-4">
        {/* Stats */}
        <div className="grid grid-cols-2 gap-3">
          <div className="card card-body text-center">
            <p className="text-xs font-semibold text-muted-foreground uppercase">Distance</p>
            <p className="text-2xl font-bold mt-1">{itinerary.totalDistanceKm}</p>
            <p className="text-xs text-muted-foreground">km</p>
          </div>
          <div className="card card-body text-center">
            <p className="text-xs font-semibold text-muted-foreground uppercase">Duration</p>
            <p className="text-2xl font-bold mt-1">
              {Math.round((itinerary.totalDurationMinutes || 0) / 60)}
            </p>
            <p className="text-xs text-muted-foreground">hours</p>
          </div>
          <div className="card card-body text-center">
            <p className="text-xs font-semibold text-muted-foreground uppercase">Stops</p>
            <p className="text-2xl font-bold mt-1">{steps.length}</p>
          </div>
          <div className="card card-body text-center">
            <p className="text-xs font-semibold text-muted-foreground uppercase">Status</p>
            <p className={`text-lg font-bold mt-1 ${
              itinerary.status === 'COMPLETED' ? 'text-green-600' :
              itinerary.status === 'PROCESSING' ? 'text-primary' : ''
            }`}>
              {itinerary.status}
            </p>
          </div>
        </div>

        {/* Steps List */}
        <div className="card">
          <div className="card-header">
            <h3 className="font-bold">Stops ({steps.length})</h3>
          </div>
          <div className="divide-y divide-border">
            {steps.map((step, index) => (
              <div
                key={step.id}
                className={`p-4 transition-colors ${
                  step.id === selectedStepId ? 'bg-primary/5' : 'hover:bg-muted/50'
                }`}
              >
                <div className="flex items-start gap-3">
                  <div className={`
                    flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg text-sm font-bold
                    ${step.status === 'VISITED' ? 'bg-green-100 text-green-600' :
                      step.status === 'CURRENT' ? 'bg-indigo-100 text-indigo-600 ring-2 ring-indigo-400' :
                      'bg-muted text-muted-foreground'}
                  `}>
                    {index + 1}
                  </div>
                  <div className="flex-1 min-w-0">
                    <h4 className="font-semibold truncate">{step.placeName}</h4>
                    <p className="text-sm text-muted-foreground truncate">
                      {step.city}, {step.country}
                    </p>

                    {step.durationFromPrevMin && (
                      <div className="flex items-center gap-2 mt-2 text-xs text-muted-foreground">
                        <Clock className="h-3 w-3" />
                        <span>{step.distanceFromPrevKm} km · {step.durationFromPrevMin} min</span>
                      </div>
                    )}

                    {step.aiDescription && (
                      <div className="mt-3 p-3 rounded-xl bg-muted/50 text-sm text-muted-foreground">
                        <div className="flex items-center gap-1.5 mb-1">
                          <Info className="h-3 w-3" />
                          <span className="text-xs font-medium">AI Insight</span>
                        </div>
                        {step.aiDescription}
                      </div>
                    )}
                  </div>
                  <button
                    onClick={() => onMarkVisited(step.id)}
                    disabled={step.status === 'VISITED'}
                    className="btn btn-sm btn-primary disabled:opacity-50"
                  >
                    <Footprints className="h-3.5 w-3.5" />
                    {step.status === 'VISITED' ? 'Done' : 'Visit'}
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
