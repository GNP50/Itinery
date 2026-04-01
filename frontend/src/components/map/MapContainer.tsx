import React, { useEffect } from 'react'
import { MapContainer as LeafletMap, TileLayer, useMap } from 'react-leaflet'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import iconUrl from 'leaflet/dist/images/marker-icon.png'
import iconRetinaUrl from 'leaflet/dist/images/marker-icon-2x.png'
import shadowUrl from 'leaflet/dist/images/marker-shadow.png'

// Fix Leaflet's default marker icon paths broken by Vite's asset hashing
delete (L.Icon.Default.prototype as any)._getIconUrl
L.Icon.Default.mergeOptions({ iconUrl, iconRetinaUrl, shadowUrl })

interface MapContainerProps {
  children: React.ReactNode
  center?: [number, number]
  zoom?: number
  bounds?: [[number, number], [number, number]]
  className?: string
  height?: string
}

export function MapContainer({
  children,
  center = [48.8566, 2.3522],
  zoom = 13,
  bounds,
  className = 'h-[500px]',
  height,
}: MapContainerProps) {
  return (
    <div className={className} style={height ? { height } : undefined}>
      <LeafletMap center={center} zoom={zoom} zoomControl={false} style={{ height: '100%', width: '100%' }}>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <FitBounds bounds={bounds} />
        {children}
      </LeafletMap>
    </div>
  )
}

// Internal component to handle fitBounds
function FitBounds({ bounds }: { bounds?: [[number, number], [number, number]] }) {
  const map = useMap()

  useEffect(() => {
    if (bounds && map) {
      map.flyToBounds(bounds, { padding: [50, 50] })
    }
  }, [bounds, map])

  return null
}
