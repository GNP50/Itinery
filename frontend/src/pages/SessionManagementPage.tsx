import React, { useState, useMemo, useEffect } from 'react'
import { ArrowLeft, Trash2, User, Clock, CheckSquare, Square, XCircle, ChevronLeft, ChevronRight, UserPlus } from 'lucide-react'
import { toast } from 'react-hot-toast'
import { useAuth } from '../contexts/AuthContext'
import { useRouterStore } from '../store/routerStore'
import axios from 'axios'

const ITEMS_PER_PAGE = 10

interface SessionItineraryCount {
  [sessionId: string]: {
    count: number
    loading: boolean
  }
}

export function SessionManagementPage() {
  const { anonymousSessions, currentAnonymousSession, switchAnonymousSession, deleteAnonymousSession, clearAllAnonymous, createAnonymous } = useAuth()
  const { back } = useRouterStore()
  
  const [selectedSessions, setSelectedSessions] = useState<Set<string>>(new Set())
  const [currentPage, setCurrentPage] = useState(0)
  const [showDeleteAllConfirm, setShowDeleteAllConfirm] = useState(false)
  const [showDeleteSelectedConfirm, setShowDeleteSelectedConfirm] = useState(false)
  const [isCreatingSession, setIsCreatingSession] = useState(false)
  const [sessionCounts, setSessionCounts] = useState<SessionItineraryCount>({})

  // Fetch itinerary counts for each session from backend
  useEffect(() => {
    const fetchSessionCounts = async () => {
      const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
      
      for (const session of anonymousSessions) {
        // Skip if already loaded
        if (sessionCounts[session.id]) continue
        
        // Set loading state
        setSessionCounts(prev => ({
          ...prev,
          [session.id]: { count: 0, loading: true }
        }))
        
        try {
          // Create axios instance with this session's JWT
          const api = axios.create({
            baseURL,
            headers: {
              'Content-Type': 'application/json',
              'Authorization': `Bearer ${session.jwt}`
            },
          })
          
          // Fetch itineraries for this session
          const response = await api.get('/api/v1/itineraries')
          const count = response.data.itineraries?.length || 0
          
          setSessionCounts(prev => ({
            ...prev,
            [session.id]: { count, loading: false }
          }))
        } catch (error) {
          console.error(`Failed to fetch itineraries for session ${session.id}:`, error)
          setSessionCounts(prev => ({
            ...prev,
            [session.id]: { count: 0, loading: false }
          }))
        }
      }
    }
    
    if (anonymousSessions.length > 0) {
      fetchSessionCounts()
    }
  }, [anonymousSessions.length]) // Only re-run when number of sessions changes

  // Paginazione
  const totalPages = Math.ceil(anonymousSessions.length / ITEMS_PER_PAGE)
  const paginatedSessions = useMemo(() => {
    const start = currentPage * ITEMS_PER_PAGE
    return anonymousSessions.slice(start, start + ITEMS_PER_PAGE)
  }, [anonymousSessions, currentPage])

  const formatDate = (isoDate: string) => {
    const date = new Date(isoDate)
    return date.toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  }

  const handleSelectAll = () => {
    if (selectedSessions.size === paginatedSessions.length) {
      setSelectedSessions(new Set())
    } else {
      setSelectedSessions(new Set(paginatedSessions.map(s => s.id)))
    }
  }

  const handleToggleSession = (sessionId: string) => {
    const newSelected = new Set(selectedSessions)
    if (newSelected.has(sessionId)) {
      newSelected.delete(sessionId)
    } else {
      newSelected.add(sessionId)
    }
    setSelectedSessions(newSelected)
  }

  const handleDeleteSelected = () => {
    if (selectedSessions.size === 0) return
    
    selectedSessions.forEach(sessionId => {
      deleteAnonymousSession(sessionId)
    })
    
    setSelectedSessions(new Set())
    setShowDeleteSelectedConfirm(false)
    toast.success(`${selectedSessions.size} session${selectedSessions.size > 1 ? 's' : ''} deleted`)
  }

  const handleDeleteAll = () => {
    clearAllAnonymous()
    setSelectedSessions(new Set())
    setShowDeleteAllConfirm(false)
    toast.success('All sessions deleted')
  }

  const handleSwitchSession = (sessionId: string) => {
    switchAnonymousSession(sessionId)
    toast.success('Session switched')
  }

  const handleCreateNewSession = async () => {
    setIsCreatingSession(true)
    try {
      await createAnonymous()
      toast.success('New guest session created and activated')
    } catch (error) {
      toast.error('Failed to create new session')
      console.error('Error creating session:', error)
    } finally {
      setIsCreatingSession(false)
    }
  }

  const allSelected = paginatedSessions.length > 0 && selectedSessions.size === paginatedSessions.length

  return (
    <div className="page-container animate-fade-in">
      {/* Header */}
      <div className="mb-6 animate-fade-in-down">
        <button onClick={back} className="btn btn-ghost btn-sm mb-4 -ml-2 group">
          <ArrowLeft className="h-4 w-4 group-hover:-translate-x-1 transition-transform" />
          Back
        </button>
        <div className="flex items-start justify-between flex-wrap gap-4">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-3 mb-2">
              <h1 className="section-title">Guest Sessions</h1>
              <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-primary/10 text-primary">
                {anonymousSessions.length} {anonymousSessions.length === 1 ? 'session' : 'sessions'}
              </span>
            </div>
            <p className="section-subtitle">
              Manage your anonymous sessions and switch between them
            </p>
          </div>
          <div className="flex items-center gap-2 flex-wrap">
            <button
              onClick={handleCreateNewSession}
              disabled={isCreatingSession}
              className="btn btn-primary btn-sm"
            >
              <UserPlus className="h-4 w-4" />
              {isCreatingSession ? 'Creating...' : 'New Guest Session'}
            </button>
            {selectedSessions.size > 0 && (
              <button
                onClick={() => setShowDeleteSelectedConfirm(true)}
                className="btn btn-danger btn-sm"
              >
                <Trash2 className="h-4 w-4" />
                Delete Selected ({selectedSessions.size})
              </button>
            )}
            <button
              onClick={() => setShowDeleteAllConfirm(true)}
              disabled={anonymousSessions.length === 0}
              className="btn btn-ghost btn-sm text-red-500 hover:bg-red-50"
            >
              <XCircle className="h-4 w-4" />
              Delete All
            </button>
          </div>
        </div>
      </div>

      {/* Empty state */}
      {anonymousSessions.length === 0 ? (
        <div className="card animate-scale-in">
          <div className="empty-state">
            <div className="empty-state-icon">
              <User className="h-8 w-8 text-muted-foreground" />
            </div>
            <p className="empty-state-title">No sessions yet</p>
            <p className="empty-state-body">
              Create a new guest session to get started
            </p>
            <button
              onClick={handleCreateNewSession}
              disabled={isCreatingSession}
              className="btn btn-primary mt-4"
            >
              <UserPlus className="h-5 w-5" />
              {isCreatingSession ? 'Creating...' : 'Create Guest Session'}
            </button>
          </div>
        </div>
      ) : (
        <>
          {/* Table */}
          <div className="card overflow-hidden animate-fade-in-up" style={{ animationDelay: '100ms' }}>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-muted/30 border-b border-border">
                  <tr>
                    <th className="px-6 py-4 text-left w-12">
                      <button
                        onClick={handleSelectAll}
                        className="flex items-center justify-center hover:bg-accent rounded-lg p-1 transition-colors"
                        title={allSelected ? 'Deselect all' : 'Select all'}
                      >
                        {allSelected ? (
                          <CheckSquare className="h-5 w-5 text-primary" />
                        ) : (
                          <Square className="h-5 w-5 text-muted-foreground" />
                        )}
                      </button>
                    </th>
                    <th className="px-6 py-4 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                      Session
                    </th>
                    <th className="px-6 py-4 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                      Itineraries
                    </th>
                    <th className="px-6 py-4 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                      Created
                    </th>
                    <th className="px-6 py-4 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                      Last Used
                    </th>
                    <th className="px-6 py-4 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                      Status
                    </th>
                    <th className="px-6 py-4 text-right text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                      Actions
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {paginatedSessions.map((session, index) => {
                    const isSelected = selectedSessions.has(session.id)
                    const isCurrent = session.id === currentAnonymousSession?.id
                    
                    return (
                      <tr
                        key={session.id}
                        className={`transition-colors hover:bg-muted/50 ${
                          isCurrent ? 'bg-primary/5' : ''
                        } animate-fade-in-up`}
                        style={{ animationDelay: `${Math.min(index * 30, 200)}ms` }}
                      >
                        <td className="px-6 py-4">
                          <button
                            onClick={() => handleToggleSession(session.id)}
                            className="flex items-center justify-center hover:bg-accent rounded-lg p-1 transition-colors"
                            disabled={isCurrent}
                          >
                            {isSelected ? (
                              <CheckSquare className="h-5 w-5 text-primary" />
                            ) : (
                              <Square className="h-5 w-5 text-muted-foreground" />
                            )}
                          </button>
                        </td>
                        <td className="px-6 py-4">
                          <div className="flex items-center gap-3">
                            <div className="h-10 w-10 rounded-full bg-muted flex items-center justify-center flex-shrink-0">
                              <User className="h-5 w-5 text-muted-foreground" />
                            </div>
                            <div className="min-w-0">
                              <p className="text-sm font-medium text-foreground truncate">
                                {session.label || `Session ${session.id.slice(5, 13)}`}
                              </p>
                              <p className="text-xs text-muted-foreground truncate">
                                {session.userId.slice(0, 16)}...
                              </p>
                            </div>
                          </div>
                        </td>
                        <td className="px-6 py-4">
                          <div className="flex items-center gap-2">
                            {sessionCounts[session.id]?.loading ? (
                              <div className="flex items-center gap-2 text-muted-foreground">
                                <div className="h-4 w-4 border-2 border-muted-foreground/30 border-t-muted-foreground rounded-full animate-spin" />
                                <span className="text-xs">Loading...</span>
                              </div>
                            ) : (
                              <>
                                <span className="text-sm font-medium text-foreground">
                                  {sessionCounts[session.id]?.count ?? 0}
                                </span>
                                <span className="text-xs text-muted-foreground">
                                  trip{(sessionCounts[session.id]?.count ?? 0) !== 1 ? 's' : ''}
                                </span>
                              </>
                            )}
                          </div>
                        </td>
                        <td className="px-6 py-4">
                          <p className="text-sm text-muted-foreground">
                            {formatDate(session.createdAt)}
                          </p>
                        </td>
                        <td className="px-6 py-4">
                          <div className="flex items-center gap-2">
                            <Clock className="h-4 w-4 text-muted-foreground" />
                            <p className="text-sm text-muted-foreground">
                              {formatDate(session.lastUsedAt)}
                            </p>
                          </div>
                        </td>
                        <td className="px-6 py-4">
                          {isCurrent ? (
                            <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-primary/10 text-primary">
                              <span className="h-2 w-2 rounded-full bg-primary animate-pulse" />
                              Active
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-muted text-muted-foreground">
                              Inactive
                            </span>
                          )}
                        </td>
                        <td className="px-6 py-4">
                          <div className="flex items-center justify-end gap-2">
                            {!isCurrent && (
                              <button
                                onClick={() => handleSwitchSession(session.id)}
                                className="px-3 py-1.5 text-xs font-medium text-primary hover:bg-primary/10 rounded-lg transition-colors"
                              >
                                Switch
                              </button>
                            )}
                            {!isCurrent && (
                              <button
                                onClick={() => {
                                  if (confirm('Delete this session? All saved itineraries will be lost.')) {
                                    deleteAnonymousSession(session.id)
                                    toast.success('Session deleted')
                                  }
                                }}
                                className="p-1.5 text-muted-foreground hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                                title="Delete session"
                              >
                                <Trash2 className="h-4 w-4" />
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="px-6 py-4 border-t border-border flex items-center justify-between flex-wrap gap-4">
                <p className="text-sm text-muted-foreground">
                  Showing {currentPage * ITEMS_PER_PAGE + 1} to{' '}
                  {Math.min((currentPage + 1) * ITEMS_PER_PAGE, anonymousSessions.length)} of{' '}
                  {anonymousSessions.length} sessions
                </p>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setCurrentPage(Math.max(0, currentPage - 1))}
                    disabled={currentPage === 0}
                    className="btn btn-ghost btn-sm"
                  >
                    <ChevronLeft className="h-4 w-4" />
                    Previous
                  </button>
                  <div className="flex items-center gap-1">
                    {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => {
                      let pageNum = i
                      if (totalPages > 5) {
                        if (currentPage < 3) {
                          pageNum = i
                        } else if (currentPage > totalPages - 3) {
                          pageNum = totalPages - 5 + i
                        } else {
                          pageNum = currentPage - 2 + i
                        }
                      }
                      return (
                        <button
                          key={pageNum}
                          onClick={() => setCurrentPage(pageNum)}
                          className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                            currentPage === pageNum
                              ? 'bg-primary text-primary-foreground'
                              : 'text-muted-foreground hover:bg-muted'
                          }`}
                        >
                          {pageNum + 1}
                        </button>
                      )
                    })}
                  </div>
                  <button
                    onClick={() => setCurrentPage(Math.min(totalPages - 1, currentPage + 1))}
                    disabled={currentPage === totalPages - 1}
                    className="btn btn-ghost btn-sm"
                  >
                    Next
                    <ChevronRight className="h-4 w-4" />
                  </button>
                </div>
              </div>
            )}
          </div>
        </>
      )}

      {/* Delete Selected Confirmation Modal */}
      {showDeleteSelectedConfirm && (
        <div className="modal-backdrop" onClick={() => setShowDeleteSelectedConfirm(false)}>
          <div className="modal-panel animate-scale-in max-w-md" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="text-lg font-semibold text-foreground">Delete Selected Sessions</h3>
            </div>
            <div className="modal-body">
              <p className="text-sm text-muted-foreground">
                Are you sure you want to delete {selectedSessions.size} session{selectedSessions.size > 1 ? 's' : ''}?
                All associated itineraries will be permanently lost.
              </p>
            </div>
            <div className="modal-footer">
              <button
                onClick={() => setShowDeleteSelectedConfirm(false)}
                className="btn btn-ghost"
              >
                Cancel
              </button>
              <button
                onClick={handleDeleteSelected}
                className="btn btn-danger"
              >
                <Trash2 className="h-4 w-4" />
                Delete {selectedSessions.size}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete All Confirmation Modal */}
      {showDeleteAllConfirm && (
        <div className="modal-backdrop" onClick={() => setShowDeleteAllConfirm(false)}>
          <div className="modal-panel animate-scale-in max-w-md" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="text-lg font-semibold text-foreground">Delete All Sessions</h3>
            </div>
            <div className="modal-body">
              <p className="text-sm text-muted-foreground mb-3">
                Are you sure you want to delete <strong>all {anonymousSessions.length} sessions</strong>?
              </p>
              <div className="p-3 rounded-lg bg-destructive/10 border border-destructive/20">
                <p className="text-sm font-medium text-destructive">
                  ⚠️ This action cannot be undone. All itineraries in all sessions will be permanently lost.
                </p>
              </div>
            </div>
            <div className="modal-footer">
              <button
                onClick={() => setShowDeleteAllConfirm(false)}
                className="btn btn-ghost"
              >
                Cancel
              </button>
              <button
                onClick={handleDeleteAll}
                className="btn btn-danger"
              >
                <Trash2 className="h-4 w-4" />
                Delete All
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
