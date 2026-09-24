# Cloudflared for Android
## Native Cloudflare Zero Trust Tunnel Client (Root & Non-Root)

[![Android Version](https://img.shields.io/badge/Android-5.0%2B%20(API%2021%2B)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Architecture](https://img.shields.io/badge/Architecture-ARM64--v8a-blue)](https://github.com/cloudflare/cloudflared)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Latest Release](https://img.shields.io/badge/Release-v1.2.0-0366d6.svg)](https://github.com/roufsweb/cloudflared-android/releases/tag/v1.2.0)
[![Downloads](https://img.shields.io/badge/Downloads-200%2B-success.svg)](https://github.com/roufsweb/cloudflared-android/releases)

A high-performance, native implementation of Cloudflare Tunnel (cloudflared) engineered for Android devices. This client allows you to securely expose local web applications, APIs, Termux environments, and IoT services to the public internet using Cloudflare Zero Trust without requiring root privileges, dynamic DNS, or router port-forwarding.

---

## Direct APK Downloads

Pre-built application packages are published under [Releases](https://github.com/roufsweb/cloudflared-android/releases):

| Build Variant | Target Device | Package File | Direct Download |
|:---|:---|:---:|:---:|
| **Standard (Non-Root)** | All Android devices (stock / unrooted) | cloudflared-non-rooted.apk | [Download APK](https://github.com/roufsweb/cloudflared-android/releases/download/v1.2.0/cloudflared-non-rooted.apk) |
| **Rooted (Magisk)** | Rooted devices requiring boot scripts | cloudflared-rooted.apk | [Download APK](https://github.com/roufsweb/cloudflared-android/releases/download/v1.2.0/cloudflared-rooted.apk) |

*Tested on Android 10, 11, 12, 13, and 14 (ARM64).*

---

## Technical Features

* **Non-Root Sandboxed Execution:** Operates entirely within the standard Android application sandbox without requiring elevated superuser privileges.
* **Kernel Stdin DNS Patching:** Resolves Android's native absence of /etc/resolv.conf by redirecting the Go runtime pure-Go DNS resolver via //proc/self/fd/0 and pre-configured upstream resolvers (1.1.1.1, 8.8.8.8).
* **Direct Boot Compatibility:** Uses LOCKED_BOOT_COMPLETED receivers to automatically initialize tunnels after a device reboot before the first user unlock.
* **Foreground Service Persistence:** Runs as a high-priority foreground service (dataSync type) with partial WakeLock management to withstand aggressive Android background task termination.
* **Zero Configuration Routing:** Bypasses Carrier-Grade NAT (CGNAT), firewall restrictions, and ISP inbound port blocks using outbound QUIC/HTTP2 tunnel connections.

---

## Architecture & How It Works

Standard Go binaries (such as official cloudflared releases) fail on Android because Android's Bionic C library does not implement /etc/resolv.conf. The native runtime cannot locate DNS nameservers, preventing connection to Cloudflare's edge infrastructure.

`
[Local Service / Termux] (e.g. localhost:8080)
            |
            v
[Cloudflared Native Library (libcloudflared.so)]
            |
            +--> DNS Redirection: Stdin Pipe -> //proc/self/fd/0 (1.1.1.1, 8.8.8.8)
            |
            v  (Outbound HTTPS / QUIC on Port 7844 / 443)
[Cloudflare Edge Anycast Network]
            |
            v
[Public Internet / Custom Domain] (e.g. https://service.yourdomain.com)
`

1. The official cloudflared binary is compiled and packaged as a native shared library (libcloudflared.so) located in the application directory.
2. During initialization, the wrapper generates an internal resolver configuration containing Cloudflare (1.1.1.1) and Google (8.8.8.8) DNS endpoints.
3. The process is started with stdin bound to this configuration file, satisfying the patched //proc/self/fd/0 resolver path.
4. The client establishes outbound persistent connections to the nearest Cloudflare Edge data centers.

---

## Quick Start Setup

### Step 1: Generate a Tunnel Token
1. Log in to the [Cloudflare Zero Trust Dashboard](https://one.dash.cloudflare.com/).
2. Navigate to **Networks** > **Tunnels**.
3. Click **Add a tunnel**, select **Cloudflared**, and enter a tunnel name.
4. Under **Install and run a connector**, select any environment and copy the --token string from the displayed command line:
   `	ext
   eyJhIjoiYmNm... (Your Base64 Token)
   `

### Step 2: Configure the Android App
1. Install and launch cloudflared-non-rooted.apk.
2. Paste your copied token into the **Tunnel Token** input field.
3. Tap **Connect Tunnel**.
4. Grant the requested **Notification Permission** and select **Allow** when prompted to ignore battery optimizations.

### Step 3: Route Inbound Traffic
In the Cloudflare dashboard under your tunnel's **Public Hostnames** tab, map your public domain to the service running on your device:
* **Subdomain:** myapp
* **Domain:** yourdomain.com
* **Type:** HTTP
* **URL:** localhost:8080 (or whichever port your local application uses)

Traffic sent to https://myapp.yourdomain.com is now securely routed directly to your Android device.

---

## Common Use Cases

### 1. Exposing Termux Servers
Run web servers, APIs, or scripts in Termux and make them accessible worldwide without port forwarding:
`ash
# In Termux: Start a simple Python web server on port 8080
python3 -m http.server 8080

# In Cloudflare Dashboard: Route public hostname to localhost:8080
`

### 2. Repurposing Old Android Phones as Homelab Nodes
Turn unused Android hardware into dedicated, low-power micro-servers hosting:
* Node-RED automations
* Home Assistant instances
* Lightweight SQLite or PocketBase backends
* RTSP camera feeds or webhook endpoints

### 3. Ngrok Alternative for Mobile Development
Develop and test webhooks (e.g. Stripe, PayPal, GitHub webhooks) directly against your mobile environment with zero recurring costs and custom domain support.

---

## Reliability & Background Execution

Android vendors often implement aggressive OEM background killers. To ensure 24/7 tunnel reliability:

| Device Brand | Recommended Action |
|:---|:---|
| **Stock Android / Pixel** | Settings > Apps > Cloudflared > App battery usage > Set to **Unrestricted** |
| **Samsung (One UI)** | Settings > Battery > Background usage limits > Add Cloudflared to **Never sleeping apps** |
| **Xiaomi (MIUI / HyperOS)** | App Info > Battery saver > Select **No restrictions**, and enable **Autostart** |
| **OnePlus / Realme / Oppo** | App Info > Battery usage > Enable **Allow background activity** & **Allow auto-launch** |

---

## Building from Source

### Prerequisites
* Android Studio Iguana (2023.2.1) or newer
* Android SDK 34
* JDK 17
* Android NDK 25.1.8937393 or newer

### Build Command
`ash
# Build standard non-rooted APK
./gradlew assembleNonrootRelease

# Build rooted Magisk variant
./gradlew assembleRootRelease
`

Compiled APKs will be located at:
* pp/build/outputs/apk/nonroot/release/cloudflared-non-rooted.apk
* pp/build/outputs/apk/root/release/cloudflared-rooted.apk

---

## Disclaimer
Cloudflare, Cloudflare Tunnel, and Cloudflare Zero Trust are trademarks of Cloudflare, Inc. This project is an independent open-source client and is not affiliated with or endorsed by Cloudflare.
