import { useEffect, useState } from 'react';
import { websocketService } from '../services/websocketService';
import { ConnectionState, StatusMessage } from '../types/settings';

export function useWebSocket() {
  const [connectionState, setConnectionState] = useState<ConnectionState>(
    websocketService.getConnectionState()
  );

  useEffect(() => {
    const unsubscribe = websocketService.onConnectionStateChange(setConnectionState);
    
    // Connect if not already connected
    if (!connectionState.connected && !connectionState.connecting) {
      websocketService.connect();
    }
    
    return unsubscribe;
  }, []);

  return {
    connectionState,
    send: websocketService.send.bind(websocketService),
    updateSetting: websocketService.updateSetting.bind(websocketService),
    registerForUpdates: websocketService.registerForUpdates.bind(websocketService),
    connect: websocketService.connect.bind(websocketService),
    disconnect: websocketService.disconnect.bind(websocketService)
  };
}

export function useWebSocketMessages() {
  const [messages, setMessages] = useState<StatusMessage[]>([]);
  const [lastMessage, setLastMessage] = useState<StatusMessage | null>(null);

  useEffect(() => {
    const unsubscribe = websocketService.onMessage((message) => {
      setLastMessage(message);
      setMessages(prev => [...prev.slice(-99), message]); // Keep last 100 messages
    });
    
    return unsubscribe;
  }, []);

  return {
    messages,
    lastMessage
  };
}