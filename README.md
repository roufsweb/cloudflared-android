# Cloudflared Android

A high-performance, non-root Cloudflare Tunnel client for Android. This application allows you to securely expose local services to the internet or create private tunnels using Cloudflare Zero Trust.

## 🚀 Features

- **Non-Root Execution**: Runs `cloudflared` without requiring superuser permissions.
- **Direct Boot Support**: Automatically starts your tunnel after a reboot, even before the device is first unlocked.
- **Auto-Patching Engine**: Automatically patches official `cloudflared` binaries to support Android DNS resolution.
- **Real-Time Status**: Live foreground notification with connection status updates.
- **Background Stability**: Integrated WakeLock and battery optimization handling to prevent the system from killing the tunnel.
- **Seamless Updates**: Download and apply the latest official Cloudflare releases with a single tap.

## 🛠 Installation

1. Download the latest APK from the [Releases](https://github.com/roufsweb/cloudflared-android/releases) section.
2. Install the APK on your Android device.
3. Open the app and grant the **Post Notifications** permission.
4. Follow the prompt to **Ignore Battery Optimizations** for consistent background performance.

## 📖 Usage

1. Obtain a **Tunnel Token** from the Cloudflare Zero Trust dashboard.
2. Paste the token into the app.
3. Click **Connect Tunnel**.
4. (Optional) Enable **Start on Boot** to ensure your tunnel is always active.

## 🛡 Security & Privacy

- **Device Protected Storage**: Sensitive configuration (like your tunnel token) is stored in device-protected storage, enabling safe boot-start functionality.
- **Sandboxed Execution**: The `cloudflared` binary runs within the app's private data directory.

## 🔧 Technical Details

- **Binary Patching**: The app replaces `/etc/resolv.conf` references in the Go binary with `//proc/self/fd/0` at runtime to facilitate DNS resolution via stdin redirection.
- **Foreground Service**: Uses a high-priority foreground service with `dataSync` type for reliable execution on Android 12, 13, and 14.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
