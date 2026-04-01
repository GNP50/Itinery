import React, { useState } from 'react'
import { X } from 'lucide-react'
import { toast } from 'react-hot-toast'
import { CreateItineraryForm } from './CreateItineraryForm'
import { useClient } from '../../contexts/ClientContext'
import { mapResponseToItinerary } from '../../utils/mappers'
import type { Itinerary } from '../../types/itinerary'

interface EditItineraryModalProps {
  isOpen: boolean
  onClose: () => void
  itinerary: Itinerary
  onSaved: (updated: Itinerary) => void
}

export function EditItineraryModal({
  isOpen,
  onClose,
  itinerary,
  onSaved,
}: EditItineraryModalProps) {
  const client = useClient()
  const [loading, setLoading] = useState(false)

  if (!isOpen) return null

  const initialValues = {
    title: itinerary.title,
    description: itinerary.description ?? '',
    travelMode: itinerary.travelMode as any,
    steps: itinerary.steps.map((s) => ({
      placeName: s.placeName,
      notes: s.notes ?? '',
      arrivalDate: s.arrivalDate ?? '',
      preferences: s.preferences,
    })),
    interests: itinerary.preferences?.interests ?? [],
    avoidHighways: itinerary.preferences?.avoidHighways ?? false,
    generateAiTips: itinerary.preferences?.generateAiTips ?? true,
  }

  const handleSubmit = async (data: any) => {
    setLoading(true)
    try {
      const res = await client.updateItinerary(itinerary.id, itinerary.accessToken, {
        title: data.title,
        description: data.description,
        travelMode: data.travelMode,
        steps: data.steps,
        preferences: {
          interests: data.interests,
          avoidHighways: data.avoidHighways,
          generateAiTips: data.generateAiTips,
        },
      })
      onSaved(mapResponseToItinerary(res))
      toast.success('Itinerary updated!')
      onClose()
    } catch (err: any) {
      toast.error(err?.response?.data?.message ?? 'Failed to update itinerary')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal-panel animate-scale-in"
        onClick={(e) => e.stopPropagation()}
        style={{ maxHeight: '90vh', display: 'flex', flexDirection: 'column' }}
      >
        <div className="modal-header">
          <h2 className="text-lg font-semibold text-gray-900">Edit Itinerary</h2>
          <button onClick={onClose} className="btn-icon btn-ghost">
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="modal-body overflow-y-auto flex-1 scrollbar-thin">
          <CreateItineraryForm
            onSubmit={handleSubmit}
            loading={loading}
            initialValues={initialValues}
            submitLabel="Save Changes"
          />
        </div>
      </div>
    </div>
  )
}
