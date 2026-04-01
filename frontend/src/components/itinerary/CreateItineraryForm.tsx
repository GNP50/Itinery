import React, { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { MapPin, Navigation, Clock, User, ChevronRight, Plus, X, Eye, EyeOff } from 'lucide-react'
import { PlaceAutocomplete, GeoSearchResult } from '../geo/PlaceAutocomplete'
import { MapContainer } from '../map/MapContainer'
import { Marker, Popup } from 'react-leaflet'

// Schema
const stepSchema = z.object({
  placeName: z.string().min(1, 'Place name is required'),
  notes: z.string().optional(),
  arrivalDate: z.string().optional(),
  preferences: z.object({
    interests: z.array(z.string()).optional(),
    avoidHighways: z.boolean().optional(),
    generateAiTips: z.boolean().optional(),
  }).optional(),
})

const formSchema = z.object({
  title: z.string().min(1, 'Title is required'),
  description: z.string().optional(),
  travelMode: z.enum(['CAR', 'BIKE', 'WALK', 'TRANSIT'], {
    required_error: 'Travel mode is required',
  }),
  steps: z.array(stepSchema),
  interests: z.array(z.string()).optional(),
  avoidHighways: z.boolean().optional(),
  generateAiTips: z.boolean().optional(),
})

type FormValues = z.infer<typeof formSchema>

const INTEREST_OPTIONS = [
  { value: 'art', label: 'Art & Museums' },
  { value: 'food', label: 'Food & Dining' },
  { value: 'history', label: 'History' },
  { value: 'nature', label: 'Nature' },
  { value: 'shopping', label: 'Shopping' },
  { value: 'nightlife', label: 'Nightlife' },
  { value: 'sports', label: 'Sports' },
  { value: 'religion', label: 'Religious Sites' },
]

const TRAVEL_MODES = [
  { value: 'CAR', label: 'By Car', icon: Navigation },
  { value: 'BIKE', label: 'By Bike', icon: MapPin },
  { value: 'WALK', label: 'On Foot', icon: User },
  { value: 'TRANSIT', label: 'Public Transit', icon: Clock },
]

interface CreateItineraryFormProps {
  onSubmit: (data: FormValues) => Promise<void>
  loading?: boolean
  initialValues?: Partial<FormValues>
  submitLabel?: string
}

export function CreateItineraryForm({ onSubmit, loading, initialValues, submitLabel = 'Create Itinerary' }: CreateItineraryFormProps) {
  const [interests, setInterests] = useState<string[]>(initialValues?.interests ?? [])
  const [showPreviewMap, setShowPreviewMap] = useState(false)
  const [showStepPreferences, setShowStepPreferences] = useState<Record<number, boolean>>(() => {
    // Show step preferences section if the step has any preferences defined
    const initial: Record<number, boolean> = {}
    initialValues?.steps?.forEach((step, index) => {
      if (step.preferences && (
        (step.preferences.interests && step.preferences.interests.length > 0) ||
        step.preferences.avoidHighways !== undefined ||
        step.preferences.generateAiTips !== undefined
      )) {
        initial[index] = true
      }
    })
    return initial
  })
  const [stepCoords, setStepCoords] = useState<Array<{ lat: number; lon: number; name: string } | null>>(
    () => (initialValues?.steps ?? [{ placeName: '' }]).map(() => null)
  )

  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      title: initialValues?.title ?? '',
      description: initialValues?.description ?? '',
      travelMode: initialValues?.travelMode ?? 'CAR',
      steps: initialValues?.steps ?? [{ placeName: '', notes: '', arrivalDate: '', preferences: undefined }],
      interests: initialValues?.interests ?? [],
      avoidHighways: initialValues?.avoidHighways ?? false,
      generateAiTips: initialValues?.generateAiTips ?? true,
    },
  })

  const { control, register, handleSubmit, setValue, watch, formState } = form
  const steps = watch('steps')

  const addStep = () => {
    const currentSteps = watch('steps') || []
    setValue('steps', [...currentSteps, { placeName: '', notes: '', arrivalDate: '', preferences: undefined }])
    setStepCoords((prev) => [...prev, null])
  }

  const removeStep = (index: number) => {
    const currentSteps = watch('steps') || []
    if (currentSteps.length > 1) {
      setValue('steps', currentSteps.filter((_, i) => i !== index))
      setStepCoords((prev) => prev.filter((_, i) => i !== index))
    }
  }

  const handlePlaceSelect = (index: number, place: GeoSearchResult) => {
    setStepCoords((prev) => {
      const next = [...prev]
      next[index] = { lat: place.lat, lon: place.lon, name: place.displayName }
      return next
    })
  }

  const handlePlaceChange = (index: number, value: string | null) => {
    updateStep(index, 'placeName', value || '')
    if (!value) {
      setStepCoords((prev) => {
        const next = [...prev]
        next[index] = null
        return next
      })
    }
  }

  const updateStep = (index: number, field: keyof typeof steps[0], value: string) => {
    const currentSteps = watch('steps') || []
    const updatedSteps = currentSteps.map((step, i) =>
      i === index ? { ...step, [field]: value } : step
    )
    setValue('steps', updatedSteps)
  }

  const toggleInterest = (interest: string) => {
    setInterests((prev) =>
      prev.includes(interest) ? prev.filter((i) => i !== interest) : [...prev, interest]
    )
  }

  const toggleStepPreferences = (index: number) => {
    setShowStepPreferences((prev) => ({
      ...prev,
      [index]: !prev[index],
    }))
  }

  const toggleStepInterest = (stepIndex: number, interest: string) => {
    const currentSteps = watch('steps') || []
    const step = currentSteps[stepIndex]
    const currentInterests = step.preferences?.interests || []
    
    const updatedInterests = currentInterests.includes(interest)
      ? currentInterests.filter((i) => i !== interest)
      : [...currentInterests, interest]
    
    const updatedSteps = currentSteps.map((s, i) =>
      i === stepIndex
        ? {
            ...s,
            preferences: {
              ...s.preferences,
              interests: updatedInterests,
            },
          }
        : s
    )
    setValue('steps', updatedSteps)
  }

  const updateStepPreference = (stepIndex: number, field: 'avoidHighways' | 'generateAiTips', value: boolean) => {
    const currentSteps = watch('steps') || []
    const updatedSteps = currentSteps.map((step, i) =>
      i === stepIndex
        ? {
            ...step,
            preferences: {
              ...step.preferences,
              [field]: value,
            },
          }
        : step
    )
    setValue('steps', updatedSteps)
  }

  const onSubmitForm = (data: FormValues) => {
    onSubmit({ ...data, interests })
  }

  return (
    <form onSubmit={handleSubmit(onSubmitForm)} className="space-y-6">
      {/* Basic Info */}
      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700">Title</label>
          <input
            {...register('title')}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            placeholder="e.g., Tuscany Road Trip"
          />
          {formState.errors.title && (
            <p className="mt-1 text-sm text-red-600">{formState.errors.title.message}</p>
          )}
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700">Description</label>
          <textarea
            {...register('description')}
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            rows={3}
            placeholder="Brief description of your trip..."
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700">Travel Mode</label>
          <div className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-4">
            {TRAVEL_MODES.map((mode) => (
              <label
                key={mode.value}
                className={`
                  flex cursor-pointer flex-col items-center gap-2 rounded-lg border-2 p-3 transition-colors
                  ${watch('travelMode') === mode.value
                    ? 'border-blue-600 bg-blue-50'
                    : 'border-gray-200 hover:border-gray-300'
                  }
                `}
              >
                <input
                  type="radio"
                  value={mode.value}
                  className="hidden"
                  {...register('travelMode')}
                />
                <mode.icon className="h-6 w-6 text-gray-600" />
                <span className="text-sm font-medium text-gray-900">{mode.label}</span>
              </label>
            ))}
          </div>
          {formState.errors.travelMode && (
            <p className="mt-1 text-sm text-red-600">{formState.errors.travelMode.message}</p>
          )}
        </div>
      </div>

      {/* Steps */}
      <div>
        <div className="flex items-center justify-between mb-4">
          <label className="block text-sm font-medium text-gray-700">Stops / Steps</label>
          <button
            type="button"
            onClick={addStep}
            className="flex items-center gap-1 rounded-md bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700"
          >
            <Plus className="h-4 w-4" />
            Add Stop
          </button>
        </div>

        <div className="space-y-3">
          {steps?.map((step, index) => (
            <div key={index} className="rounded-lg border border-gray-200 p-4 shadow-sm">
              <div className="flex items-center gap-2 mb-3">
                <div className="flex h-6 w-6 items-center justify-center rounded-full bg-gray-200 text-xs font-medium text-gray-600">
                  {index + 1}
                </div>
                <h3 className="font-medium text-gray-900">Stop {index + 1}</h3>
                {steps.length > 1 && (
                  <button
                    type="button"
                    onClick={() => removeStep(index)}
                    className="ml-auto text-gray-400 hover:text-red-600"
                  >
                    <X className="h-5 w-5" />
                  </button>
                )}
              </div>
              <div className="space-y-3">
                <PlaceAutocomplete
                  value={step.placeName}
                  onChange={(value) => handlePlaceChange(index, value)}
                  onSelect={(place) => handlePlaceSelect(index, place)}
                  placeholder="Enter a destination..."
                />
                <div>
                  <label className="block text-xs font-medium text-gray-700">Notes</label>
                  <input
                    type="text"
                    value={step.notes || ''}
                    onChange={(e) => updateStep(index, 'notes', e.target.value)}
                    className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                    placeholder="Any specific notes..."
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-700">Arrival Date (Optional)</label>
                  <input
                    type="date"
                    value={step.arrivalDate || ''}
                    onChange={(e) => updateStep(index, 'arrivalDate', e.target.value)}
                    className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  />
                </div>

                {/* Step Preferences Override */}
                <div className="mt-3 border-t pt-3">
                  <button
                    type="button"
                    onClick={() => toggleStepPreferences(index)}
                    className="text-sm text-blue-600 hover:text-blue-700 font-medium"
                  >
                    {showStepPreferences[index] ? '− Hide custom preferences' : '+ Override preferences for this step'}
                  </button>

                  {showStepPreferences[index] && (
                    <div className="mt-3 pl-4 border-l-2 border-blue-200 space-y-3">
                      <p className="text-xs text-gray-500">
                        These preferences will override trip-level settings for this step only.
                      </p>

                      {/* Step-level Interests */}
                      <div>
                        <label className="block text-xs font-medium text-gray-700 mb-2">Interests</label>
                        <div className="flex flex-wrap gap-2">
                          {INTEREST_OPTIONS.map((option) => (
                            <button
                              key={option.value}
                              type="button"
                              onClick={() => toggleStepInterest(index, option.value)}
                              className={`
                                rounded-full px-2.5 py-1 text-xs font-medium transition-colors
                                ${(step.preferences?.interests || []).includes(option.value)
                                  ? 'bg-purple-600 text-white'
                                  : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                                }
                              `}
                            >
                              {option.label}
                            </button>
                          ))}
                        </div>
                      </div>

                      {/* Step-level Generate AI Tips */}
                      <label className="flex items-center gap-2">
                        <input
                          type="checkbox"
                          checked={step.preferences?.generateAiTips ?? false}
                          onChange={(e) => updateStepPreference(index, 'generateAiTips', e.target.checked)}
                          className="h-4 w-4 rounded text-blue-600 focus:ring-blue-500"
                        />
                        <span className="text-xs text-gray-700">Generate AI tips for this step</span>
                      </label>
                    </div>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Preferences */}
      <div className="rounded-lg border border-gray-200 p-4 shadow-sm">
        <h3 className="font-medium text-gray-900 mb-3">Preferences</h3>

        <div className="mb-4">
          <label className="block text-sm font-medium text-gray-700 mb-2">Interests</label>
          <div className="flex flex-wrap gap-2">
            {INTEREST_OPTIONS.map((option) => (
              <button
                key={option.value}
                type="button"
                onClick={() => toggleInterest(option.value)}
                className={`
                  rounded-full px-3 py-1 text-xs font-medium transition-colors
                  ${interests.includes(option.value)
                    ? 'bg-blue-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }
                `}
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>

        <div className="flex items-center gap-4">
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={watch('avoidHighways') || false}
              onChange={(e) => setValue('avoidHighways', e.target.checked)}
              className="h-4 w-4 rounded text-blue-600 focus:ring-blue-500"
            />
            <span className="text-sm text-gray-700">Avoid highways</span>
          </label>
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={watch('generateAiTips') ?? true}
              onChange={(e) => setValue('generateAiTips', e.target.checked)}
              className="h-4 w-4 rounded text-blue-600 focus:ring-blue-500"
            />
            <span className="text-sm text-gray-700">Generate AI tips</span>
          </label>
        </div>
      </div>

      {/* Preview Toggle + Map */}
      {stepCoords.some((c) => c !== null) && (
        <div className="space-y-3">
          <button
            type="button"
            onClick={() => setShowPreviewMap((v) => !v)}
            className="btn btn-secondary btn-sm w-full"
          >
            {showPreviewMap ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            {showPreviewMap ? 'Hide Map Preview' : 'Show Map Preview'}
          </button>

          {showPreviewMap && (() => {
            const validCoords = stepCoords.filter((c): c is NonNullable<typeof c> => c !== null)
            const center: [number, number] = [validCoords[0].lat, validCoords[0].lon]
            return (
              <div className="rounded-xl overflow-hidden border border-border">
                <MapContainer center={center} zoom={6} className="h-64">
                  {validCoords.map((coord, i) => (
                    <Marker key={i} position={[coord.lat, coord.lon]}>
                      <Popup>
                        <span className="text-sm font-medium">{coord.name}</span>
                      </Popup>
                    </Marker>
                  ))}
                </MapContainer>
              </div>
            )
          })()}
        </div>
      )}

      {/* Submit */}
      <div className="flex justify-end pt-4">
        <button
          type="submit"
          disabled={loading || formState.isSubmitting}
          className="btn btn-primary btn-lg"
        >
          {loading || formState.isSubmitting ? (
            <>
              <div className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
              Saving…
            </>
          ) : (
            <>
              {submitLabel}
              <ChevronRight className="h-4 w-4" />
            </>
          )}
        </button>
      </div>
    </form>
  )
}
