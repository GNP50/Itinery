import React, { createContext, useContext, useMemo } from 'react'
import { createClient, ApiClient } from '../api/client'

const ClientContext = createContext<ApiClient | null>(null)

interface ClientProviderProps {
  children: React.ReactNode
  baseURL: string
}

export function ClientProvider({ children, baseURL }: ClientProviderProps) {
  const client = useMemo(() => createClient(baseURL), [baseURL])
  return (
    <ClientContext.Provider value={client}>
      {children}
    </ClientContext.Provider>
  )
}

export function useClient(): ApiClient {
  const ctx = useContext(ClientContext)
  if (!ctx) throw new Error('useClient must be used inside <ClientProvider>')
  return ctx
}
