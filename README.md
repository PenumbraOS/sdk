# SDK

This is the SDK for [PenumbraOS](https://github.com/PenumbraOS/), the full development platform for the late Humane Ai Pin.

## Architecture

Due to the locked down nature of the Humane Ai Pin, actually achieving access to "privileged" operations is very convoluted (`untrusted_app` cannot even access the network). The PenumbraOS SDK is designed to mitigate the setup issues and make a repeatable solution suitable for end users. The general spawn capabilities are provided by the [`pinitd`](https://github.com/PenumbraOS/pinitd/) init system.

### Embedded SDK

This is the actual exposed API surface to developers, run from within your `untrusted_app`. The SDK maintains the multiplexed connection to the `bridge` service, making a clean developer experience for the underlying callback-based Binder service. Located in `/sdk`.

### Bridge Service

Quite literally just a bridge between the SDK and the privileged world. `untrusted_app` on the Pin is restricted to making binder connections to exclusively the `nfc` and `radio` SELinux domains. Since `radio` is everything having to do with cellular which is always in use, `nfc` becomes the obvious choice. [`pinitd`](https://github.com/PenumbraOS/pinitd/) is used to spawn a process as the `nfc` user and domain, and `app_process` is used to set up the JVM and run the actual service. Located in `/bridge`.

### Bridge Privileged Daemon

The gateway to all actual privileged operations. Currently, all operations are exclusively things that can run in the `shell` domain (which is where the `pinitd` controller operates), so `bridge_priv_rs` also runs in `shell`. Spawns a TCP server for access from Bridge backed by a simple Protobuf protocol. Future optimization may necessitate direct TCP streams forwarded through Binder, but that would add complexity that is unnecessary at this time. Located in `/bridge_priv_rs`.

## Installation

This is an active work in progress and may be difficult to set up. Please reach out to [@agg23](https://github.com/agg23) for questions or help.
