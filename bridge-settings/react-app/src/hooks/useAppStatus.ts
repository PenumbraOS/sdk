import { useState, useEffect } from 'react';
import { useWebSocketMessages } from './useWebSocket';

export interface AppStatus {
  [component: string]: Record<string, unknown>;
}

export interface AppEvent {
  id: string;
  appId: string;
  eventType: string;
  payload: Record<string, unknown>;
  timestamp: number;
}

export function useAppStatus(appId?: string) {
  const [appStatuses, setAppStatuses] = useState<Record<string, AppStatus>>({});
  const [appEvents, setAppEvents] = useState<AppEvent[]>([]);
  const { lastMessage } = useWebSocketMessages();

  useEffect(() => {
    if (!lastMessage) return;

    if (lastMessage.type === 'appStatusUpdate') {
      const { appId: messageAppId, component, data } = lastMessage;
      
      // If filtering by appId, only update for that app
      if (appId && messageAppId !== appId) return;
      
      setAppStatuses(prev => ({
        ...prev,
        [messageAppId]: {
          ...prev[messageAppId],
          [component]: data
        }
      }));
    }
    
    if (lastMessage.type === 'appEvent') {
      const { appId: messageAppId, eventType, payload } = lastMessage;
      
      // If filtering by appId, only show events for that app
      if (appId && messageAppId !== appId) return;
      
      const event: AppEvent = {
        id: `${messageAppId}-${eventType}-${Date.now()}-${Math.random()}`,
        appId: messageAppId,
        eventType,
        payload,
        timestamp: Date.now()
      };
      
      setAppEvents(prev => [event, ...prev].slice(0, 100)); // Keep last 100 events
    }
  }, [lastMessage, appId]);

  const getAppStatus = (targetAppId: string): AppStatus => {
    return appStatuses[targetAppId] || {};
  };

  const getAppEvents = (targetAppId?: string): AppEvent[] => {
    if (targetAppId) {
      return appEvents.filter(event => event.appId === targetAppId);
    }
    return appEvents;
  };

  const clearEvents = (targetAppId?: string) => {
    if (targetAppId) {
      setAppEvents(prev => prev.filter(event => event.appId !== targetAppId));
    } else {
      setAppEvents([]);
    }
  };

  return {
    appStatuses,
    appEvents,
    getAppStatus,
    getAppEvents,
    clearEvents
  };
}