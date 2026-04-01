import React from 'react'
import { MapPin, Navigation, Clock, Globe } from 'lucide-react'
import { GeoSearchResult } from './PlaceAutocomplete'

interface PlaceDetailProps {
  place: GeoSearchResult
  onReverseGeocode?: (lat: number, lon: number) => Promise<void>
}

export function PlaceDetail({ place, onReverseGeocode }: PlaceDetailProps) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <MapPin className="h-5 w-5 text-blue-600" />
          <h3 className="font-semibold text-gray-900">{place.displayName}</h3>
        </div>
        {onReverseGeocode && (
          <button
            onClick={() => onReverseGeocode(place.lat, place.lon)}
            className="rounded-full bg-gray-100 px-3 py-1 text-xs font-medium text-gray-600 hover:bg-gray-200"
          >
            Get Details
          </button>
        )}
      </div>

      <div className="space-y-2 text-sm">
        <div className="flex items-center gap-2 text-gray-600">
          <Globe className="h-4 w-4" />
          <div>
            <span className="text-gray-500">Country: </span>
            <span className="font-medium">{place.country}</span>
            {place.countryCode && (
              <span className="ml-2 text-gray-400 uppercase">{place.countryCode}</span>
            )}
          </div>
        </div>

        {place.city && (
          <div className="flex items-center gap-2 text-gray-600">
            <Navigation className="h-4 w-4" />
            <div>
              <span className="text-gray-500">City: </span>
              <span className="font-medium">{place.city}</span>
            </div>
          </div>
        )}

        {place.region && (
          <div className="flex items-center gap-2 text-gray-600">
            <MapPin className="h-4 w-4" />
            <div>
              <span className="text-gray-500">Region: </span>
              <span className="font-medium">{place.region}</span>
            </div>
          </div>
        )}

        <div className="flex items-center gap-2 text-gray-600">
          <span className="text-gray-500">Coordinates: </span>
          <span className="font-mono text-xs">
            {place.lat.toFixed(6)}, {place.lon.toFixed(6)}
          </span>
        </div>

        <div className="flex items-center gap-2 text-gray-600">
          <span className="text-gray-500">Type: </span>
          <span className="font-medium">{place.type}</span>
        </div>
      </div>

      <div className="mt-3 flex gap-2">
        <a
          href={`https://www.openstreetmap.org/?mlat=${place.lat}&mlon=${place.lon}#map=15/${place.lat}/${place.lon}`}
          target="_blank"
          rel="noopener noreferrer"
          className="flex-1 rounded-lg bg-blue-600 px-3 py-2 text-center text-sm font-medium text-white hover:bg-blue-700"
        >
          View on OSM
        </a>
      </div>
    </div>
  )
}
