# PenumbraOS Settings Dashboard

A React-based web dashboard for managing PenumbraOS settings with real-time WebSocket communication.

## Features

- Real-time WebSocket communication with Kotlin backend
- System settings management (brightness, volume, WiFi, etc.)
- App-specific settings display and control
- Live system status monitoring (battery, charging, etc.)
- Responsive UI with modern design

## Development

### Prerequisites

- Node.js 16+ and npm
- The bridge-settings service running on port 8080

### Getting Started

```bash
# Install dependencies
npm install

# Start development server (with proxy to backend)
npm run dev
```

The development server will start on `http://localhost:5173` with automatic proxy to the Kotlin backend on port 8080.

### Building for Android

```bash
# Build for production and copy to Android assets
npm run build:android
```

This will:
1. Build the React app for production
2. Create optimized static files in `dist/`
3. Copy the build files to `../src/main/resources/react-build/`

The Kotlin web server will automatically serve these files from the APK when the React app is built.

## Architecture

### WebSocket Communication

The frontend communicates with the Kotlin backend using WebSocket messages:

**Outgoing (React → Kotlin):**
- `GetAllSettings` - Request current settings
- `UpdateSetting` - Change a setting value
- `RegisterForUpdates` - Subscribe to setting categories

**Incoming (Kotlin → React):**
- `AllSettings` - Complete settings data
- `SettingChanged` - Notification of setting change
- `StatusUpdate` - System status updates (battery, etc.)
- `Error` - Error messages

### Components

- **App** - Main application component
- **ConnectionStatus** - WebSocket connection indicator
- **SystemStatus** - Real-time system information display
- **SystemSettings** - System-level setting controls
- **AppSettings** - App-specific setting controls
- **ToggleSwitch** - Boolean setting control
- **Slider** - Numeric setting control

### Hooks

- **useWebSocket** - WebSocket connection management
- **useWebSocketMessages** - Message handling
- **useSettings** - Settings state management

## File Structure

```
src/
├── components/        # React components
├── hooks/            # Custom React hooks  
├── services/         # WebSocket service
├── types/           # TypeScript type definitions
├── styles/          # CSS styles
└── main.tsx         # Application entry point
```

## Deployment

The built React app is served by the Kotlin Ktor server directly from the APK resources. When `npm run build:android` is executed, the files are automatically placed in the resources directory and packaged into the APK. The server extracts and serves these files at runtime.

The React build is required for the web interface to work - there is no fallback UI.