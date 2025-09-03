# SDK

This is the SDK for [PenumbraOS](https://github.com/PenumbraOS/), the full development platform for the late Humane Ai Pin.

> [!CAUTION]
> This is extremely experimental and currently is usable by developers only. See [Installation](#installation) for in-progress instructions on how to set it up.

## Current functionality

The PenumbraOS SDK exposes the following restricted interfaces on the Ai Pin:

- [DNS](sdk/src/main/java/com/penumbraos/sdk/api/DnsClient.kt) - Custom API implementation
- [HTTP](sdk/src/main/java/com/penumbraos/sdk/api/HttpClient.kt) - Custom API implementation. Hopefully will add `OkHttp` handler soon
- [WebSocket](sdk/src/main/java/com/penumbraos/sdk/api/WebSocketClient.kt) - Custom API implementation. Hopefully will add `OkHttp` handler soon
- [Touchpad](sdk/src/main/java/com/penumbraos/sdk/api/TouchpadClient.kt)
- [Hand Gestures](sdk/src/main/java/com/penumbraos/sdk/api/HandGestureClient.kt)
- [Speech Recognition](sdk/src/main/java/com/penumbraos/sdk/api/SttClient.kt)
- [Shell Tunnel](sdk/src/main/java/com/penumbraos/sdk/api/ShellClient.kt)
- [eSIM Configuration](sdk/src/main/java/com/penumbraos/sdk/api/EsimClient.kt)
- [Settings Management](bridge-settings/src/main/java/com/penumbraos/bridge_settings/) - System and app settings with dynamic web UI

Additionally some experimental interfaces are provided:

- [Hand Tracking](sdk/src/main/java/com/penumbraos/sdk/api/HandTrackingClient.kt)
- [Notification (Side) LED](sdk/src/main/java/com/penumbraos/sdk/api/HandTrackingClient.kt)

## Architecture

Due to the locked down nature of the Humane Ai Pin, actually achieving access to "privileged" operations is very convoluted (`untrusted_app` cannot even access the network). The PenumbraOS SDK is designed to mitigate the setup issues and make a repeatable solution suitable for end users. The general spawn capabilities are provided by the [`pinitd`](https://github.com/PenumbraOS/pinitd/) init system.

### Embedded SDK

This is the actual exposed API surface to developers, run from within your `untrusted_app`. The SDK maintains the multiplexed connection to the `bridge` service, making a clean developer experience for the underlying callback-based Binder service. Located in `/sdk`.

### Bridge Service

Quite literally just a bridge between the SDK and the privileged world. `untrusted_app` on the Pin is restricted to making binder connections to exclusively the `nfc` and `radio` SELinux domains. Since `radio` is everything having to do with cellular which is always in use, `nfc` becomes the obvious choice. [`pinitd`](https://github.com/PenumbraOS/pinitd/) is used to spawn a process as the `nfc` user and domain, and `app_process` is used to set up the JVM and run the actual service. Located in `/bridge`.

### Bridge System Service

The gateway to all actual privileged operations. Currently, all operations are exclusively things that can run in the `system` domain, so `bridge-system` also runs in `system`. Communicates with `bridge-core` over Binder. Located in `/bridge-system`.

### Bridge Settings Service

The management system for all PenumbraOS settings, and notably provides the embedded web server used for development and consumers like [MABL](https://github.com/PenumbraOS/mabl) to provide a settings UI. Runs as `bridge-settings` in the `system` domain, and communicates with `bridge-core` over Binder. Located in `/bridge-settings`.

### Bridge Shell Service

Both a worker in the `shell` domain and a service for proxying actual `sh` commands, this service allows PenumbraOS pieces to perform actions that are otherwise restricted to direct ADB access. Runs as `bridge-shell` in the `shell` domain, and communicates with `bridge-core` over Binder. Located in `/bridge-shell`.

## CLI

A command-line interface is available at `/data/local/tmp/penumbra` for managing system settings and executing module actions. Notably, this can be used to configure the eSIM.

```bash
# List available settings and actions
penumbra settings list

# Get/set system settings
penumbra settings system audio.volume
penumbra settings system audio.volume 75

# Execute module actions
penumbra settings esim getProfiles
penumbra settings esim enableProfile --iccid 89012345678901234567
penumbra settings esim downloadAndEnableProfile --activationCode LPA:1\$rsp.truphone.com\$QRF-SPEEDTEST
```

## Installation

This is an active work in progress and may be difficult to set up. Please reach out to [@agg23](https://github.com/agg23) for questions or help.

> [!NOTE]  
> These steps are chosen for active development of PenumbraOS and do not represent what the end user experience should be like. In a normal environment, `pinitd` is already running and the `bridge` and `bridge-system` services are set to run on boot and automatically restart on error.

For general installation, see https://github.com/PenumbraOS/installer. If you want to develop locally, running `build.sh` from a Bash-compatible shell will set up the entire environment for Pin for you on the actual device.
