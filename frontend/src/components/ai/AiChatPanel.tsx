import React, { useState, useRef, useEffect } from 'react'
import { Send, X, Sparkles, Bot, User, ChevronDown, ChevronUp } from 'lucide-react'
import { toast } from 'react-hot-toast'

interface Message {
  role: 'user' | 'assistant'
  content: string
  timestamp: Date
}

interface AiChatPanelProps {
  itineraryId?: string
  onSend?: (message: string, history: Message[]) => Promise<string>
  inline?: boolean
}

export function AiChatPanel({ itineraryId, onSend, inline = false }: AiChatPanelProps) {
  const [messages, setMessages] = useState<Message[]>([
    {
      role: 'assistant',
      content: "Hello! I'm your travel assistant. I can help you with suggestions, tips, and answering questions about your itinerary.",
      timestamp: new Date(),
    },
  ])
  const [input, setInput] = useState('')
  const [isTyping, setIsTyping] = useState(false)
  const [isExpanded, setIsExpanded] = useState(true)
  const messagesEndRef = useRef<HTMLDivElement>(null)

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages, isTyping])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!input.trim()) return

    const userMessage: Message = {
      role: 'user',
      content: input,
      timestamp: new Date(),
    }

    setMessages((prev) => [...prev, userMessage])
    setInput('')
    setIsTyping(true)

    try {
      const response = await onSend?.(input, messages) || "I'm just a frontend demo! Connect me to the backend API for real AI responses."
      const assistantMessage: Message = {
        role: 'assistant',
        content: response,
        timestamp: new Date(),
      }
      setMessages((prev) => [...prev, assistantMessage])
    } catch (error) {
      toast.error('Failed to get AI response')
    } finally {
      setIsTyping(false)
    }
  }

  const quickPrompts = [
    'What should I see in this city?',
    'Best restaurants nearby?',
    'How much time should I spend here?',
    'Hidden gems I shouldn\'t miss?',
  ]

  return (
    <div className={`${inline ? 'relative w-full' : 'fixed bottom-4 right-4 z-50 w-full max-w-md'} rounded-xl bg-white shadow-2xl border border-gray-200 flex flex-col overflow-hidden animate-slide-in`}>
      {/* Header */}
      <div className="flex items-center justify-between bg-gradient-to-r from-indigo-600 to-purple-600 p-4 text-white">
        <div className="flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-white/20">
            <Sparkles className="h-5 w-5" />
          </div>
          <div>
            <h3 className="font-semibold">AI Travel Assistant</h3>
            <p className="text-xs text-indigo-100">
              {itineraryId ? 'Context: Your itinerary' : 'No itinerary selected'}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={() => setIsExpanded(!isExpanded)}
            className="rounded-lg p-1.5 hover:bg-white/20"
          >
            {isExpanded ? (
              <ChevronUp className="h-5 w-5" />
            ) : (
              <ChevronDown className="h-5 w-5" />
            )}
          </button>
          <button className="rounded-lg p-1.5 hover:bg-white/20">
            <X className="h-5 w-5" />
          </button>
        </div>
      </div>

      {/* Messages */}
      {isExpanded && (
        <div className="flex-1 overflow-y-auto p-4 space-y-4 bg-gray-50/50 max-h-[400px]">
          {messages.map((message, index) => (
            <div
              key={index}
              className={`flex gap-3 ${message.role === 'user' ? 'flex-row-reverse' : ''}`}
            >
              <div className={`flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full ${message.role === 'user' ? 'bg-gray-200' : 'bg-indigo-600'}`}>
                {message.role === 'user' ? (
                  <User className="h-4 w-4 text-gray-600" />
                ) : (
                  <Bot className="h-4 w-4 text-white" />
                )}
              </div>
              <div
                className={`rounded-2xl px-4 py-2.5 max-w-[85%] text-sm ${
                  message.role === 'user'
                    ? 'bg-gray-600 text-white rounded-tr-sm'
                    : 'bg-white text-gray-900 border border-gray-200 rounded-tl-sm'
                }`}
              >
                {message.content}
              </div>
            </div>
          ))}

          {isTyping && (
            <div className="flex gap-3">
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-indigo-600">
                <Bot className="h-4 w-4 text-white" />
              </div>
              <div className="flex items-center gap-1 bg-white rounded-2xl rounded-tl-sm px-4 py-3 border border-gray-200">
                <div className="h-2 w-2 rounded-full bg-gray-400 animate-bounce" style={{ animationDelay: '0ms' }} />
                <div className="h-2 w-2 rounded-full bg-gray-400 animate-bounce" style={{ animationDelay: '150ms' }} />
                <div className="h-2 w-2 rounded-full bg-gray-400 animate-bounce" style={{ animationDelay: '300ms' }} />
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>
      )}

      {/* Quick Prompts */}
      {isExpanded && messages.length < 3 && (
        <div className="flex gap-2 overflow-x-auto px-4 pb-2">
          {quickPrompts.map((prompt, i) => (
            <button
              key={i}
              onClick={() => {
                setInput(prompt)
              }}
              className="flex-shrink-0 rounded-full bg-indigo-50 px-3 py-1.5 text-xs font-medium text-indigo-700 hover:bg-indigo-100"
            >
              {prompt}
            </button>
          ))}
        </div>
      )}

      {/* Input */}
      {isExpanded && (
        <form onSubmit={handleSubmit} className="border-t p-4 bg-white">
          <div className="flex items-center gap-2">
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Ask something about your itinerary..."
              className="flex-1 rounded-full border border-gray-300 px-4 py-2.5 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              disabled={isTyping}
            />
            <button
              type="submit"
              disabled={!input.trim() || isTyping}
              className="flex h-10 w-10 items-center justify-center rounded-full bg-indigo-600 text-white hover:bg-indigo-700 disabled:opacity-50"
            >
              <Send className="h-5 w-5" />
            </button>
          </div>
        </form>
      )}
    </div>
  )
}
