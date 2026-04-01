import React, { useState, useEffect } from 'react'
import { MapPin, Map, Search, X, Navigation } from 'lucide-react'
import { toast } from 'react-hot-toast'

interface Poi {
  osmId: number
  name: string
  category: string
  lat: number
  lon: number
  tags: Record<string, string>
}

interface PoiExplorerProps {
  centerLat: number
  centerLon: number
  radius?: number
  category?: string
  onPoiSelect?: (poi: Poi) => void
}

const CATEGORIES = [
  { value: 'all', label: 'All POIs' },
  { value: 'tourism', label: 'Tourism' },
  { value: 'amenity', label: 'Amenities' },
  { value: 'historic', label: 'Historic' },
  { value: 'shopping', label: 'Shopping' },
  { value: 'food', label: 'Food & Dining' },
]

export function PoiExplorer({
  centerLat,
  centerLon,
  radius = 2000,
  category = 'all',
  onPoiSelect,
}: PoiExplorerProps) {
  const [pois, setPois] = useState<Poi[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [selectedCategory, setSelectedCategory] = useState(category)

  useEffect(() => {
    if (centerLat && centerLon) {
      fetchPois(centerLat, centerLon, radius, selectedCategory)
    }
  }, [centerLat, centerLon, radius, selectedCategory])

  const fetchPois = async (lat: number, lon: number, r: number, cat: string) => {
    setIsLoading(true)

    try {
      let query = `[out:json][timeout:10];(node(around:${r},${lat},${lon})`

      if (cat === 'all') {
        query += `["tourism"~"museum|attraction|viewpoint|hotel|hostel"]["amenity"~"restaurant|cafe|bar|pub"]["historic"~"monument|castle|ruins"];`
      } else if (cat === 'tourism') {
        query += `["tourism"~"museum|attraction|viewpoint|hotel|hostel"];`
      } else if (cat === 'amenity') {
        query += `["amenity"~"restaurant|cafe|bar|pub|fast_food"];`
      } else if (cat === 'historic') {
        query += `["historic"~"monument|castle|ruins|memorial"];`
      } else if (cat === 'shopping') {
        query += `["shop"~"supermarket|bakery|clothes|shoes|gift"];`
      } else if (cat === 'food') {
        query += `["amenity"~"restaurant|cafe|bar|pub|fast_food|food_court"];`
      }

      query += `);out body;`

      const response = await fetch('https://overpass-api.de/api/interpreter', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ data: query }),
      })

      if (!response.ok) {
        throw new Error('Failed to fetch POIs')
      }

      const data = await response.json()
      const parsedPois: Poi[] = data.elements
        .filter((el: any) => el.lat && el.lon)
        .map((el: any) => ({
          osmId: el.id,
          name: el.tags.name || 'Unknown',
          category: Object.entries(el.tags).find(([k]) => ['tourism', 'amenity', 'historic', 'shop'].includes(k))?.[1] || 'other',
          lat: el.lat,
          lon: el.lon,
          tags: el.tags || {},
        }))

      setPois(parsedPois)
    } catch (error) {
      toast.error('Failed to load POIs')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
      <div className="border-b p-4">
        <div className="flex items-center gap-2 mb-3">
          <MapPin className="h-5 w-5 text-blue-600" />
          <h3 className="font-semibold text-gray-900">Nearby POIs</h3>
        </div>

        <div className="flex flex-wrap gap-2">
          {CATEGORIES.map((cat) => (
            <button
              key={cat.value}
              onClick={() => setSelectedCategory(cat.value)}
              className={`
                rounded-full px-3 py-1 text-xs font-medium transition-colors
                ${selectedCategory === cat.value
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }
              `}
            >
              {cat.label}
            </button>
          ))}
        </div>
      </div>

      <div className="max-h-[300px] overflow-y-auto p-2">
        {isLoading ? (
          <div className="flex items-center justify-center p-8">
            <div className="h-6 w-6 animate-spin rounded-full border-2 border-blue-600 border-t-transparent" />
          </div>
        ) : pois.length === 0 ? (
          <div className="flex items-center justify-center p-8 text-gray-500">
            <Map className="h-8 w-8 mb-2 opacity-50" />
            <p>No POIs found in this area</p>
          </div>
        ) : (
          <div className="space-y-1">
            {pois.slice(0, 10).map((poi) => (
              <div
                key={poi.osmId}
                onClick={() => onPoiSelect?.(poi)}
                className="flex items-center gap-3 rounded-lg px-3 py-2 hover:bg-gray-50 cursor-pointer"
              >
                <div className="flex h-8 w-8 items-center justify-center rounded-full bg-blue-100 text-blue-600">
                  <MapPin className="h-4 w-4" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900 truncate">{poi.name}</p>
                  <p className="text-xs text-gray-500 capitalize">{poi.category}</p>
                </div>
                <a
                  href={`https://www.openstreetmap.org/node/${poi.osmId}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="rounded-md bg-gray-100 px-2 py-1 text-xs font-medium text-gray-600 hover:bg-gray-200"
                >
                  View
                </a>
              </div>
            ))}
            {pois.length > 10 && (
              <p className="text-center text-xs text-gray-500 mt-2">
                Showing first 10 of {pois.length} POIs
              </p>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
