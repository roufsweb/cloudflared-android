# Cloudflared Android

An unofficial Android application to run [Cloudflared](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/) (Cloudflare Tunnel) directly on your Android device. This application provides a simple UI to manage and run the Cloudflared daemon, allowing you to expose local services or network routing securely from your phone.

## 📱 Versions

This app is available in two versions:

*   **Rooted Version:** Uses root access to run the binary from system directories and setup proper DNS overrides. Recommended for the most stable experience.
*   **Non-Root Version:** Runs the binary using app-specific storage and basic redirection. Ideal for devices without root access, though it may have some limitations on certain Android versions.

## ⚠️ Requirements

*   **Android Version:** Works on Android 9.0 (API level 28) and above.
*   **Privileges:** 
    *   **Rooted APK:** Requires root access (e.g., via Magisk or KernelSU).
    *   **Non-Root APK:** No special privileges required.

## ✨ Features

*   **Manage Cloudflare Tunnels:** Start and stop your Cloudflare tunnel directly from the app.
*   **Custom Token Input:** Easily paste and save your Cloudflare Tunnel token.
*   **Persistent Execution:** Runs the tunnel in the background using Android Services.
*   **Boot Auto-Start:** Automatically starts the tunnel when your device boots (configurable).
*   **Integrated Binary:** Manages the `cloudflared` binary for ARM64 architecture seamlessly.

## 🚀 How to Use

1.  **Install the APK:** Download the appropriate APK (`cloudflared-rooted.apk` or `cloudflared-non-rooted.apk`) from the [Releases](https://github.com/roufsweb/cloudflared-android/releases) page.
2.  **Permissions:** 
    *   For the **Rooted** version, grant Superuser (Root) permissions when prompted.
    *   For the **Non-Root** version, ensure the app is excluded from battery optimization if you use "Start on Boot".
3.  **Enter your Tunnel Token:** Paste your Cloudflare Tunnel token into the input field and tap "Save".
4.  **Start the Tunnel:** Tap the "Connect Tunnel" button. The status icon will indicate when the tunnel is active and running.

### How to get a Cloudflare Tunnel Token

1.  Log in to your [Cloudflare Zero Trust Dashboard](https://one.dash.cloudflare.com/).
2.  Navigate to **Networks > Tunnels**.
3.  Click **Create a tunnel**. Provide a name and choose the Cloudflared connection type.
4.  On the installation page, under the "Choose your environment" section, select any OS (like Debian/Ubuntu).
5.  Look at the provided installation command in the code box. It will look something like this:
    `cloudflared.exe service install eyJhIjoiOT...<very-long-string>...XDIifQ==`
6.  The token is the **very long string of alphanumeric characters at the end of the command**.
7.  **Copy ONLY this long token string** and paste it into the Cloudflared Android app.

## 🛠️ Building the APK

This project uses Gradle. To build the APK from source, follow these steps:

1.  Clone this repository:
    ```bash
    git clone https://github.com/roufsweb/cloudflared-android.git
    cd cloudflared-android
    ```
2.  Build the release APKs:
    ```bash
    # On Windows
    .\gradlew.bat assembleRelease

    # On Linux/macOS
    ./gradlew assembleRelease
    ```
3.  The generated APKs will be located in `app/build/outputs/apk/root/release/` and `app/build/outputs/apk/nonroot/release/`.

## 📜 License

This project is not officially affiliated with Cloudflare. The `cloudflared` binary is licensed under the Apache License 2.0 by Cloudflare, Inc. The Android wrapper is provided as-is.

---

### Connect with Rouf:
*   🌐 **Website:** [rouf.top](https://rouf.top)
*   🐙 **GitHub:** [@roufsweb](https://github.com/roufsweb)
*   ✈️ **Telegram:** [@roufsweb](https://t.me/roufsweb)
