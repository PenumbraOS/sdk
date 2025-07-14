import { useState, useEffect } from 'react';
import { useWebSocketMessages } from './useWebSocket';

export interface MABLConversationStatus {
  state: 'idle' | 'transcribing' | 'aiThinking' | 'aiResponding' | 'error';
  partialText?: string;
  userMessage?: string;
  streamingToken?: string;
  lastResponse?: string;
  errorMessage?: string;
}

export interface MABLEvent {
  id: string;
  timestamp: number;
  type: 'userMessage' | 'aiResponse' | 'touchpadTap' | 'sttError' | 'llmError';
  data: Record<string, unknown>;
}

export function useMABLStatus() {
  const [conversationStatus, setConversationStatus] = useState<MABLConversationStatus>({
    state: 'idle'
  });
  const [recentEvents, setRecentEvents] = useState<MABLEvent[]>([]);
  const [isActive, setIsActive] = useState(false);
  
  const { lastMessage } = useWebSocketMessages();

  useEffect(() => {
    if (!lastMessage) return;

    console.log('MABL: Received WebSocket message:', lastMessage);

    // Handle MABL app status updates
    if (lastMessage.type === 'appStatusUpdate' && lastMessage.appId === 'com.penumbraos.mabl') {
      console.log('MABL: Received status update for component:', lastMessage.component, 'data:', lastMessage.data);
      if (lastMessage.component === 'conversation') {
        setConversationStatus(prev => ({
          ...prev,
          ...lastMessage.data
        }));
        setIsActive(true);
      }
    }

    // Handle MABL events
    if (lastMessage.type === 'appEvent' && lastMessage.appId === 'com.penumbraos.mabl') {
      console.log('MABL: Received event:', lastMessage.eventType, 'payload:', lastMessage.payload);
      const newEvent: MABLEvent = {
        id: `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        timestamp: Date.now(),
        type: lastMessage.eventType as MABLEvent['type'],
        data: lastMessage.payload
      };

      setRecentEvents(prev => {
        const updated = [newEvent, ...prev].slice(0, 50); // Keep last 50 events
        return updated;
      });
      setIsActive(true);
    }
  }, [lastMessage]);

  const clearEvents = () => {
    setRecentEvents([]);
  };

  const getEventsByType = (type: MABLEvent['type']) => {
    return recentEvents.filter(event => event.type === type);
  };

  const getRecentEventsByType = (type: MABLEvent['type'], limit: number = 10) => {
    return recentEvents.filter(event => event.type === type).slice(0, limit);
  };

  return {
    conversationStatus,
    recentEvents,
    isActive,
    clearEvents,
    getEventsByType,
    getRecentEventsByType
  };
}