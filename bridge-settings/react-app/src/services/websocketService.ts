import {
  SettingsMessage,
  StatusMessage,
  ConnectionState,
} from "../types/settings";

export class WebSocketService {
  private ws: WebSocket | null = null;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 1000;
  private messageQueue: SettingsMessage[] = [];

  private connectionStateListeners: ((state: ConnectionState) => void)[] = [];
  private messageListeners: ((message: StatusMessage) => void)[] = [];

  private connectionState: ConnectionState = {
    connected: false,
    connecting: false,
    error: null,
  };

  constructor(
    private url: string = `ws://${location.hostname}:8080/ws/settings`
  ) {}

  connect(): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      return;
    }

    this.updateConnectionState({
      ...this.connectionState,
      connecting: true,
      error: null,
    });

    try {
      this.ws = new WebSocket(this.url);

      this.ws.onopen = () => {
        console.log("WebSocket connected");
        this.reconnectAttempts = 0;
        this.updateConnectionState({
          connected: true,
          connecting: false,
          error: null,
        });

        // Send queued messages
        this.flushMessageQueue();

        // Request initial settings
        this.send({ type: "getAllSettings" });
      };

      this.ws.onclose = () => {
        console.log("WebSocket disconnected");
        this.updateConnectionState({
          connected: false,
          connecting: false,
          error: "Connection closed",
        });

        this.scheduleReconnect();
      };

      this.ws.onerror = (error) => {
        console.error("WebSocket error:", error);
        this.updateConnectionState({
          connected: false,
          connecting: false,
          error: "Connection error",
        });
      };

      this.ws.onmessage = (event) => {
        try {
          const message: StatusMessage = JSON.parse(event.data);
          this.notifyMessageListeners(message);
        } catch (error) {
          console.error("Failed to parse WebSocket message:", error);
        }
      };
    } catch (error) {
      console.error("Failed to create WebSocket:", error);
      this.updateConnectionState({
        connected: false,
        connecting: false,
        error: "Failed to connect",
      });
    }
  }

  disconnect(): void {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    this.updateConnectionState({
      connected: false,
      connecting: false,
      error: null,
    });
  }

  send(message: SettingsMessage): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message));
    } else {
      // Queue message for when connection is restored
      this.messageQueue.push(message);

      // Try to connect if not already connecting
      if (!this.connectionState.connecting) {
        this.connect();
      }
    }
  }

  updateSetting(category: string, key: string, value: unknown): void {
    this.send({
      type: "updateSetting",
      category,
      key,
      value,
    });
  }

  registerForUpdates(categories: string[]): void {
    this.send({
      type: "registerForUpdates",
      categories,
    });
  }

  onConnectionStateChange(
    listener: (state: ConnectionState) => void
  ): () => void {
    this.connectionStateListeners.push(listener);

    // Immediately notify with current state
    listener(this.connectionState);

    // Return unsubscribe function
    return () => {
      const index = this.connectionStateListeners.indexOf(listener);
      if (index > -1) {
        this.connectionStateListeners.splice(index, 1);
      }
    };
  }

  onMessage(listener: (message: StatusMessage) => void): () => void {
    this.messageListeners.push(listener);

    // Return unsubscribe function
    return () => {
      const index = this.messageListeners.indexOf(listener);
      if (index > -1) {
        this.messageListeners.splice(index, 1);
      }
    };
  }

  private updateConnectionState(newState: ConnectionState): void {
    this.connectionState = newState;
    this.connectionStateListeners.forEach((listener) => listener(newState));
  }

  private notifyMessageListeners(message: StatusMessage): void {
    this.messageListeners.forEach((listener) => listener(message));
  }

  private flushMessageQueue(): void {
    while (this.messageQueue.length > 0) {
      const message = this.messageQueue.shift();
      if (message && this.ws?.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify(message));
      }
    }
  }

  private scheduleReconnect(): void {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      const delay =
        this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1);

      console.log(
        `Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`
      );

      setTimeout(() => {
        if (
          !this.connectionState.connected &&
          !this.connectionState.connecting
        ) {
          this.connect();
        }
      }, delay);
    } else {
      console.error("Max reconnection attempts reached");
      this.updateConnectionState({
        connected: false,
        connecting: false,
        error: "Max reconnection attempts reached",
      });
    }
  }

  getConnectionState(): ConnectionState {
    return this.connectionState;
  }
}

// Global instance
export const websocketService = new WebSocketService();
