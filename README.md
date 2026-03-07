# Cloudflared Android

An unofficial Android application to run [Cloudflared](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/) (Cloudflare Tunnel) directly on your Android device. This application provides a simple UI to manage and run the Cloudflared daemon, allowing you to expose local services or network routing securely from your phone.

## ⚠️ Requirements

*   **Root Access:** This application *requires root access* (e.g., via Magisk or KernelSU) to execute the Cloudflared Linux binary properly on the Android system and manage network interfaces.
*   **Android Version:** Works on Android 9.0 (API level 28) and above.

## ✨ Features

*   **Manage Cloudflare Tunnels:** Start and stop your Cloudflare tunnel directly from the app.
*   **Custom Token Input:** Easily paste and save your Cloudflare Tunnel token.
*   **Persistent Execution:** Runs the tunnel in the background using Android Services.
*   **Boot Auto-Start:** Automatically starts the tunnel when your device boots (configurable).
*   **Integrated Binary:** Downloads and manages the `cloudflared` binary for ARM64 architecture seamlessly.

## 🚀 How to Use

1.  **Install the APK:** Download the latest `cloudflared-wrapper.apk` from the [Releases](https://github.com/roufsweb/cloudflared-android/releases) page and install it on your rooted Android device.
2.  **Grant Root Permissions:** Open the app and grant Superuser (Root) permissions when prompted.
3.  **Enter your Tunnel Token:** Paste your Cloudflare Tunnel token into the input field and tap "Save".
4.  **Start the Tunnel:** Tap the "Start Cloudflared" button. The status icon will indicate when the tunnel is active and running.

### How to get a Cloudflare Tunnel Token

1.  Log in to your [Cloudflare Zero Trust Dashboard](https://one.dash.cloudflare.com/).
2.  Navigate to **Networks > Tunnels**.
3.  Click **Create a tunnel**. Provide a name and choose the Cloudflared connection type.
4.  On the installation page, under the "Choose your environment" section, select any OS (like Debian/Ubuntu).
5.  Look at the provided installation command in the code box. It will look something like this:
    `cloudflared.exe service install eyJhIjoiOT...<very-long-string>...XDIifQ==`
6.  The token is the **very long string of alphanumeric characters at the end of the command**. In the example above, it is the part starting with `eyJhIjoi...`.
7.  **Copy ONLY this long token string** and paste it into the Cloudflared Android app. Do not copy the `cloudflared.exe service install` part.

## 🛠️ Building the APK

This project uses Gradle. To build the APK from source, follow these steps:

1.  Clone this repository:
    ```bash
    git clone https://github.com/roufsweb/cloudflared-android.git
    cd cloudflared-android
    ```
2.  Ensure you have the Android SDK installed and configured.
3.  Build the release APK using the Gradle wrapper:
    ```bash
    # On Windows
    .\gradlew.bat assembleRelease

    # On Linux/macOS
    ./gradlew assembleRelease
    ```
4.  The generated APK will be located in `app/build/outputs/apk/release/`. By default, the unsigned debug APK is in `app/build/outputs/apk/debug/app-debug.apk`. 

*Note: For official releases, the APK should be signed with a release keystore before distribution.*

## 📱 Tested Devices

*   Tested on ARM64-v8a devices running Android 9.0 through Android 14.
*   Confirmed working with Magisk root solutions.

## 📜 License

This project is not officially affiliated with Cloudflare. The `cloudflared` binary is licensed under the Apache License 2.0 by Cloudflare, Inc. The Android wrapper is provided as-is.

---

## ✨ Credits & Acknowledgements

This application was developed by **[Rouf](https://rouf.top)** and proudly programmed with the assistance of **[Google DeepMind's Antigravity AI](https://deepmind.google/technologies/gemini/)**.

### Connect with Rouf:
*   🌐 **Website:** [rouf.top](https://rouf.top)
*   🐙 **GitHub:** [@roufsweb](https://github.com/roufsweb)
*   ✈️ **Telegram:** [@roufsweb](https://t.me/roufsweb)
