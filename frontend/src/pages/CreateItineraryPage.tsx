import React, { useState } from 'react'
import { ArrowLeft } from 'lucide-react'
import { toast } from 'react-hot-toast'
import { CreateItineraryForm } from '../components/itinerary/CreateItineraryForm'
import { useClient } from '../contexts/ClientContext'
import { useRouterStore } from '../store/routerStore'
import { useAuth } from '../contexts/AuthContext'

export function CreateItineraryPage() {
  const client = useClient()
  const { navigate, back } = useRouterStore()
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (data: any) => {
    setLoading(true)
    try {
      const res = await client.createItinerary({
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

      toast.success('Itinerary created! Processing in queue…')
      navigate({ page: 'itinerary-tracking', id: res.id, accessToken: res.accessToken })
    } catch (err: any) {
      const msg = err?.response?.data?.message ?? 'Failed to create itinerary'
      if (err?.response?.status === 429) {
        toast.error('⏱️ Rate limit exceeded. You can create up to 5 itineraries per hour. Please try again later.')
      } else {
        toast.error(msg)
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="page-container-narrow animate-fade-in">
      <div className="mb-6 animate-fade-in-down">
        <button onClick={back} className="btn btn-ghost btn-sm mb-4 -ml-2 group">
          <ArrowLeft className="h-4 w-4 group-hover:-translate-x-1 transition-transform" />
          Back
        </button>
        <h1 className="section-title">New Itinerary</h1>
        <p className="section-subtitle">
          Add your destinations and we'll plan the route, enrich with AI tips, and find nearby points of interest.
        </p>
      </div>

      <div className="card card-body animate-fade-in-up" style={{ animationDelay: '100ms' }}>
        <CreateItineraryForm onSubmit={handleSubmit} loading={loading} />
      </div>
    </div>
  )
}
