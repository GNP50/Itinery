import React from 'react'
import { useMap } from 'react-leaflet'
import { ZoomIn, ZoomOut, MapPin, Navigation } from 'lucide-react'

interface MapControlsProps {
  onCenterToUser?: () => void
}

export function MapControls({ onCenterToUser }: MapControlsProps) {
  const map = useMap()

  const zoomIn = () => {
    map.zoomIn()
  }

  const zoomOut = () => {
    map.zoomOut()
  }

  const resetView = () => {
    map.flyTo([48.8566, 2.3522], 13)
  }

  return (
    <div className="absolute bottom-4 right-4 flex flex-col gap-2">
      {/* Zoom controls */}
      <div className="flex flex-col rounded-lg border bg-white shadow-sm overflow-hidden">
        <button
          onClick={zoomIn}
          className="p-2 hover:bg-gray-100 disabled:opacity-50"
          aria-label="Zoom in"
        >
          <ZoomIn className="h-5 w-5" />
        </button>
        <button
          onClick={zoomOut}
          className="p-2 hover:bg-gray-100 disabled:opacity-50"
          aria-label="Zoom out"
        >
          <ZoomOut className="h-5 w-5" />
        </button>
      </div>

      {/* Additional controls */}
      <div className="flex flex-col gap-2">
        {onCenterToUser && (
          <button
            onClick={onCenterToUser}
            className="flex items-center justify-center rounded-lg bg-blue-600 p-2 text-white shadow hover:bg-blue-700"
            title="Center to current location"
          >
            <Navigation className="h-5 w-5" />
          </button>
        )}
        <button
          onClick={resetView}
          className="flex items-center justify-center rounded-lg bg-white p-2 text-gray-600 shadow hover:bg-gray-100"
          title="Reset view"
        >
          <MapPin className="h-5 w-5" />
        </button>
      </div>
    </div>
  )
}
