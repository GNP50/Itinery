export interface GeoSearchResult {
  placeId: string
  displayName: string
  lat: number
  lon: number
  osmId: number
  type: string
  city?: string
  region?: string
  country?: string
  countryCode?: string
}

export interface Poi {
  osmId: number
  name: string
  category: string
  lat: number
  lon: number
  tags: Record<string, string>
}
