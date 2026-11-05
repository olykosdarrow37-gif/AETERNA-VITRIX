# AETERNA VITRIX (Android)

WebView wrapper around `app/src/main/assets/index.html`.

## Build the APK on GitHub
1. Create a new GitHub repo and upload everything in this folder (keep the `.github` folder).
2. Open the **Actions** tab -> **Build APK** -> it runs on every push (or press *Run workflow*).
3. When it finishes, open the run and download the **AETERNA-VITRIX-apk** artifact (zip containing `app-debug.apk`).
4. Unzip, copy the APK to your phone, and install it (allow "install unknown apps" if asked).

## Updating the app
Replace `app/src/main/assets/index.html` and push. Icon sources: `app/src/main/res/mipmap-*` and `drawable/ic_launcher_foreground.png`.

Links and `window.open` go to Brave if installed (else your default browser). A small ad-host blocklist is in `MainActivity.java`.
