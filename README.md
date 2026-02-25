# Spuffle 🎵

A personal Android app that provides true shuffle for your Spotify playlists. Instead of relying on Spotify's built-in shuffle (which has known biases), Spuffle fetches your entire playlist, randomly selects 100 unique songs, and sends them directly to Spotify to play in that exact order.

## Features

- True random shuffle across any of your Spotify playlists
- Supports Liked Songs and all personal playlists
- Smart caching using Spotify's `snapshot_id` — repeat shuffles are nearly instant if your playlist hasn't changed
- Home screen widget for one-tap shuffling
- Handles large playlists (4000+ songs) efficiently using parallel fetching
- Automatic rate limit handling with Retry-After support

## Requirements

- Android 8.0 (API 26) or higher
- Spotify Premium account
- Your own Spotify Developer app (free — instructions below)

---

## Step 1: Install Android Studio

1. Go to [developer.android.com/studio](https://developer.android.com/studio) and download Android Studio
2. Run the installer and follow the setup wizard
3. When prompted, install the **Android SDK** (the wizard handles this automatically)
4. Once open, go to **Tools → SDK Manager** and make sure **Android 8.0 or higher** is installed

---

## Step 2: Register a Spotify Developer App

You need your own free Spotify Developer app to get a Client ID.

1. Go to [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard)
2. Log in with your Spotify Premium account
3. Click **Create App** and fill in:
   - App name: `Spuffle` (or anything you like)
   - App description: `Personal shuffle app`
   - Redirect URI: `spuffle://callback`
   - APIs used: **Web API**
4. After creation, copy your **Client ID** from the app dashboard
5. Go to **User Management** and add your own Spotify account email address — this is required for Development Mode apps

---

## Step 3: Clone and Configure the Project

```bash
git clone https://github.com/yourusername/spuffle.git
cd spuffle
```

Open the project in Android Studio via **File → Open**, then navigate to the cloned folder.

Once open, find `Constants.kt` in the project and replace the placeholder with your Client ID:

```kotlin
const val CLIENT_ID = "YOUR_CLIENT_ID_HERE"
```

---

## Step 4: Run on Your Device

1. Enable **Developer Options** on your Android phone:
   - Go to **Settings → About Phone**
   - Tap **Build Number** 7 times until you see "You are now a developer"
2. Go to **Settings → Developer Options** and enable **USB Debugging**
3. Connect your phone via USB and tap **Trust** when prompted on your phone
4. In Android Studio, select your device in the toolbar at the top
5. Click the green **Run** button (▶)

The app will build and install directly to your phone.

---

## Step 5: Export a Standalone APK (Optional)

If you want a standalone APK file you can install without Android Studio:

1. In Android Studio go to **Build → Generate Signed App Bundle / APK**
2. Choose **APK** and click **Next**
3. Click **Create New** to generate a keystore file and fill in the form:

| Field | What to enter |
|---|---|
| Key store path | Choose a safe location and name the file `spuffle.jks` |
| Password | Create a password and write it down — you'll need it for future builds |
| Confirm | Same password |
| Alias | `spuffle` |
| Key Password | Can be the same as your keystore password |
| Validity (years) | Leave as 25 |
| First and Last Name | Your name |
| Country Code | Your two-letter country code e.g. `US` |

> ⚠️ Keep your `.jks` file and password safe. You need both to update the app in future. Never commit the `.jks` file to GitHub.

4. Click **OK**, then **Next**
5. Select the **release** build variant and click **Finish**
6. Android Studio will show a notification when done — click **locate** to find the APK
7. Transfer the APK to your phone (via USB, email, or cloud storage) and open it to install
8. If prompted, allow **Install from unknown sources** in your phone settings

---

## First Launch

1. Open Spuffle and tap **Login with Spotify**
2. Approve the permissions in the browser that opens
3. You'll be redirected back to the app
4. Tap **Spuffle!** to choose a playlist and start shuffling

> Make sure the Spotify app is open and playing (or recently paused) on your device before tapping Spuffle — the playback API requires an active Spotify session.

---

## Home Screen Widget

1. Long press on your home screen and tap **Widgets**
2. Find **Spuffle** in the list and drag it to your home screen
3. Resize it horizontally between 2 and 4 columns by long pressing and dragging the edges
4. The widget uses your last used playlist — you need to Spuffle at least once from the app before the widget will work

---

## Caching Behaviour

Spuffle caches your playlist tracks locally on your device to avoid re-fetching every time:

- **Regular playlists** — cached using Spotify's `snapshot_id`. The cache is used as long as the playlist hasn't changed. If you add or remove songs, the next Spuffle automatically detects the change and fetches fresh data.
- **Liked Songs** — cached for 24 hours since Liked Songs doesn't have a `snapshot_id`. Long press the **Logout** button to force a cache clear if you want fresh data sooner.

---

## Troubleshooting

**"Make sure Spotify is open on your device"**
The Spotify app must be open and active before tapping Spuffle. Open Spotify, play or pause any song, then try again.

**"Spotify has temporarily blocked requests"**
You've hit Spotify's rate limit. The error message will tell you how many minutes to wait. This typically happens if you tap Spuffle repeatedly in quick succession.

**Playlists showing 0 songs**
This is normal for playlists you haven't Spuffled yet — Spotify doesn't always return track counts in the playlist list API. The count will update after your first Spuffle of that playlist.

**Legacy playlists not showing (Starred, Liked from Radio, Windows Media Player)**
These are old Spotify-generated playlists that the API no longer allows access to. They are filtered out automatically.

---

## Tech Stack

- **Language:** Kotlin
- **Networking:** Retrofit + OkHttp
- **Async:** Kotlin Coroutines
- **Auth:** Spotify OAuth 2.0 with PKCE
- **Architecture:** ViewModel + StateFlow
- **Min SDK:** API 26 (Android 8.0)

---

## License

This project is for personal use. If you distribute it publicly, make sure you comply with [Spotify's Developer Terms of Service](https://developer.spotify.com/terms).
