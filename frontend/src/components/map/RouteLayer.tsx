import React, { useEffect } from 'react'
import { Polyline, useMap } from 'react-leaflet'
import { LayerGroup, PolylineOptions } from 'leaflet'

interface RouteLayerProps {
  geometry: any
  color?: string
  weight?: number
  opacity?: number
}

export function RouteLayer({
  geometry,
  color = '#3b82f6',
  weight = 5,
  opacity = 0.8,
}: RouteLayerProps) {
  if (!geometry || !geometry.coordinates) return null

  const positions = geometry.coordinates.map((coord: number[]) => [coord[1], coord[0]])

  return <Polyline positions={positions} pathOptions={{ color, weight, opacity }} />
}
