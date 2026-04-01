import React from 'react'
import { GeoJSON } from 'react-leaflet'
import { useMemo } from 'react'

interface PoiLayerProps {
  pois: Array<{
    osmId: number
    name: string
    category: string
    latitude: number
    longitude: number
    tags: Record<string, string>
  }>
  category?: string
  onPoiClick?: (poi: any) => void
}

// Category colors
const CATEGORY_COLORS: Record<string, string> = {
  tourism: '#f59e0b', // amber-500
  amenity: '#8b5cf6', // violet-500
  historic: '#ef4444', // red-500
  default: '#3b82f6', // blue-500
}

export function PoiLayer({ pois, category, onPoiClick }: PoiLayerProps) {
  const features = useMemo(() => {
    if (!pois || pois.length === 0) return null

    const filteredPois = category
      ? pois.filter((poi) => poi.category.toLowerCase() === category.toLowerCase())
      : pois

    return {
      type: 'FeatureCollection' as const,
      features: filteredPois.map((poi) => ({
        type: 'Feature',
        properties: {
          name: poi.name,
          category: poi.category,
          tags: poi.tags,
        },
        geometry: {
          type: 'Point',
          coordinates: [poi.longitude, poi.latitude],
        },
      })),
    }
  }, [pois, category])

  if (!features || features.features.length === 0) return null

  const style = (feature: any) => {
    const category = feature.properties.category?.toLowerCase() || 'default'
    return {
      color: CATEGORY_COLORS[category] || CATEGORY_COLORS.default,
      fillColor: CATEGORY_COLORS[category] || CATEGORY_COLORS.default,
      fillOpacity: 0.6,
      radius: 8,
    }
  }

  return (
    <GeoJSON
      data={features}
      pointToLayer={(feature, latlng) => {
        const category = feature.properties.category?.toLowerCase() || 'default'
        const color = CATEGORY_COLORS[category] || CATEGORY_COLORS.default

        return new (window as any).L.CircleMarker(latlng, {
          radius: 8,
          fillColor: color,
          color: '#fff',
          weight: 2,
          opacity: 1,
          fillOpacity: 0.7,
        })
      }}
      onEachFeature={(feature, layer) => {
        const popupContent = `
          <div class="p-2">
            <h3 class="font-semibold text-gray-900">${feature.properties.name}</h3>
            <p class="text-sm text-gray-500">${feature.properties.category}</p>
            ${Object.entries(feature.properties.tags || {})
              .map(([k, v]) => `<div class="text-xs"><span class="font-medium">${k}:</span> ${v}</div>`)
              .join('')}
          </div>
        `
        layer.bindPopup(popupContent)
        layer.on('click', () => onPoiClick?.(feature.properties))
      }}
    />
  )
}
