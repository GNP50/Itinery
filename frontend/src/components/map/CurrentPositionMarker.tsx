import React from 'react'
import { Marker, Popup } from 'react-leaflet'
import { divIcon } from 'leaflet'
import { Navigation } from 'lucide-react'

interface CurrentPositionMarkerProps {
  latitude: number
  longitude: number
  accuracy?: number
}

export function CurrentPositionMarker({
  latitude,
  longitude,
  accuracy,
}: CurrentPositionMarkerProps) {
  const icon = divIcon({
    html: `
      <div class="flex items-center justify-center rounded-full border-4 border-blue-200 bg-blue-500 shadow-lg animate-pulse"
           style="width: 24px; height: 24px; color: white;">
        <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
          <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5a2.5 2.5 0 110-5 2.5 2.5 0 010 5z"/>
        </svg>
      </div>
    `,
    className: 'marker-icon',
    iconSize: [24, 24],
    iconAnchor: [12, 12],
  })

  return (
    <Marker position={[latitude, longitude]} icon={icon}>
      <Popup>
        <div className="flex items-center gap-2">
          <Navigation className="h-5 w-5 text-blue-600" />
          <div>
            <p className="font-semibold text-gray-900">Current Location</p>
            {accuracy !== undefined && (
              <p className="text-sm text-gray-500">
                Accuracy: {accuracy < 100 ? `${Math.round(accuracy)}m` : 'Unknown'}
              </p>
            )}
          </div>
        </div>
      </Popup>
    </Marker>
  )
}
