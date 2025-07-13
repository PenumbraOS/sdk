import React from 'react';
import { useWebSocket } from '../hooks/useWebSocket';

const ConnectionStatus: React.FC = () => {
  const { connectionState } = useWebSocket();

  const getStatusText = () => {
    if (connectionState.connected) return 'Connected';
    if (connectionState.connecting) return 'Connecting...';
    if (connectionState.error) return `Error: ${connectionState.error}`;
    return 'Disconnected';
  };

  const getStatusClass = () => {
    if (connectionState.connected) return 'status-connected';
    if (connectionState.connecting) return 'status-connecting';
    return 'status-error';
  };

  return (
    <div className="connection-status">
      <div className={`status-indicator ${getStatusClass()}`} />
      <span>{getStatusText()}</span>
    </div>
  );
};

export default ConnectionStatus;