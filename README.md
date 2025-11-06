# osp-android

## Overview
OSP (Open Source Panopticon) Android client is a **truth verification mobile application** that enables users to capture and upload **cryptographically verifiable media** (images/videos) with trusted metadata to combat misinformation. The Android app provides hardware-backed signature generation, real-time metadata capture, and seamless integration with the OSP backend.

Built with **Jetpack Compose** and **Kotlin Coroutines**, this production-ready client features:
- **Hardware-backed cryptographic signing** using Android Keystore
- **ECDSA signature generation** with SHA-256 hashing
- **Hardware attestation** with optional StrongBox support
- **Merkle tree hashing** for efficient video verification
- **Real-time metadata capture** (GPS, orientation, timestamps)
- **Google Sign-In authentication** with JWT token management
- **Automatic token refresh** with retry logic
- **CameraX integration** for photo and video capture

---

## Features

### Core Functionality
- ✅ **Cryptographic Media Signing**: ECDSA signature generation with SHA-256/Merkle tree hashing
- ✅ **Hardware-Backed Keys**: Android Keystore integration with optional StrongBox support
- ✅ **Attestation Chain Export**: Full X.509 certificate chain included for server verification
- ✅ **Real-Time Metadata Capture**: GPS coordinates, device orientation (azimuth/pitch/roll), timestamps
- ✅ **Video & Photo Support**: Unified capture flow with format-specific hashing (Merkle for video, SHA-256 for images)
- ✅ **Google OAuth Integration**: Secure sign-in with JWT token lifecycle management
- ✅ **Automatic Token Refresh**: Transparent token renewal with upload retry logic
- ✅ **Upload Progress Tracking**: Real-time upload status with progress indicators
- ✅ **Trust Score Display**: Server-calculated trust scores shown immediately after upload

### Security & Cryptography
- 🔐 **Android Keystore**: Keys generated and stored in hardware-backed Trusted Execution Environment (TEE)
- 🔐 **StrongBox Support**: Automatic detection and use of dedicated security hardware when available
- 🔐 **ECDSA P-256**: Elliptic curve cryptography with secp256r1 curve
- 🔐 **Attestation Validation**: Certificate chain exported for server-side hardware attestation verification
- 🔐 **Canonical JSON Serialization**: Deterministic metadata hashing prevents tampering
- 🔐 **Merkle Tree Construction**: 1MB chunk size for efficient large video file verification
- 🔐 **Secure Token Storage**: EncryptedSharedPreferences for JWT access/refresh tokens

### Technical Highlights
- 🛡️ **JWT Lifecycle**: Automatic access token refresh with refresh token rotation
- 📸 **CameraX Integration**: Modern camera API with lifecycle awareness
- 🗄️ **Encrypted Storage**: EncryptedSharedPreferences via AndroidX Security library
- 🌍 **Fused Location Provider**: High-accuracy GPS with Google Play Services
- 🎥 **Video Recording**: MP4 capture with metadata synchronization
- 🔄 **Coroutine Architecture**: Async/await patterns with structured concurrency
- 🎨 **Jetpack Compose UI**: Modern declarative UI with Material 3
- 🔧 **Retrofit Networking**: Type-safe HTTP client with automatic token injection
- 📦 **Multipart Upload**: Efficient binary data transmission with progress tracking

---

## Architecture

### Stack
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3
- **Language**: Kotlin 2.0+ with Coroutines
- **Camera**: CameraX with Camera2 interop
- **Networking**: Retrofit 2 + OkHttp 3 with interceptors
- **Authentication**: Google Identity Services + Credential Manager
- **Security**:
  - Android Keystore for key generation and storage
  - ECDSA signature generation (SHA256withECDSA)
  - EncryptedSharedPreferences for token storage
  - Hardware attestation with optional StrongBox
- **Location**: Google Play Services Fused Location Provider
- **Serialization**: Kotlinx Serialization + Gson
- **Dependency Injection**: Manual singleton pattern with object declarations
- **Build System**: Gradle with Kotlin DSL

### Project Structure
```
osp-android/
├── OSP/
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/doublethinksolutions/osp/
│   │   │   │   ├── MainActivity.kt                    # App entry point and navigation
│   │   │   │   ├── MyApplication.kt                   # Application class for initialization
│   │   │   │   ├── network/
│   │   │   │   │   ├── AuthService.kt                 # Authentication service
│   │   │   │   │   ├── AuthApiService.kt              # Auth API endpoints
│   │   │   │   │   ├── MediaApiService.kt             # Media upload API endpoints
│   │   │   │   │   ├── NetworkClient.kt               # Retrofit client configuration
│   │   │   │   │   ├── AuthInterceptor.kt             # JWT token injection
│   │   │   │   │   └── TokenAuthenticator.kt          # Automatic token refresh
│   │   │   │   ├── signing/
│   │   │   │   │   ├── MediaSigner.kt                 # Keystore key generation and signing
│   │   │   │   │   └── MerkleTree.kt                  # Video Merkle tree hashing
│   │   │   │   ├── upload/
│   │   │   │   │   └── UploadManager.kt               # Multipart upload with retry logic
│   │   │   │   ├── managers/
│   │   │   │   │   ├── SessionManager.kt              # JWT token storage/retrieval
│   │   │   │   │   └── LocationPermissionsManager.kt  # Location permission handling
│   │   │   │   ├── tasks/
│   │   │   │   │   ├── LocationProvider.kt            # GPS coordinate capture
│   │   │   │   │   ├── OrientationProvider.kt         # Device orientation sensors
│   │   │   │   │   └── MetadataCollectionTask.kt      # Unified metadata capture
│   │   │   │   ├── data/
│   │   │   │   │   ├── PhotoMetadata.kt               # Media metadata models
│   │   │   │   │   ├── SerializableMetadata.kt        # Kotlinx serialization models
│   │   │   │   │   └── DeviceOrientation.kt           # Orientation data classes
│   │   │   │   ├── broadcast/
│   │   │   │   │   └── AuthEvent.kt                   # Authentication event bus
│   │   │   │   └── ui/
│   │   │   │       ├── SignInScreen.kt                # Google Sign-In UI
│   │   │   │       ├── OnboardingScreen.kt            # Welcome flow
│   │   │   │       ├── CameraScreen.kt                # Photo/video capture UI
│   │   │   │       ├── UploadStatusUI.kt              # Upload progress and results
│   │   │   │       ├── UploadViewModel.kt             # Upload state management
│   │   │   │       └── theme/                         # Material 3 theming
│   │   │   ├── res/                                    # Resources (layouts, strings, etc.)
│   │   │   └── AndroidManifest.xml                    # App permissions and components
│   │   └── build.gradle.kts                            # App-level Gradle config
│   ├── gradle/                                         # Gradle wrapper
│   └── build.gradle.kts                                # Project-level Gradle config
├── README.md                                           # This file
└── LICENSE
```

---

## Cryptographic Implementation

### Signature Generation Flow
1. **Key Generation** (on first use):
   - Generate ECDSA P-256 key pair in Android Keystore
   - Enable hardware backing (TEE) and StrongBox if available
   - Attach optional attestation challenge with metadata
   - Store private key permanently in Keystore (never exported)

2. **Media Hashing**:
   - **Images (JPEG/PNG)**: Calculate SHA-256 hash of entire file
   - **Videos (MP4)**: Build Merkle tree with 1MB chunks, use root hash
   - **Parallel Processing**: Merkle tree construction uses Kotlin coroutines for performance

3. **Metadata Hashing**:
   - Serialize metadata to canonical JSON using Kotlinx Serialization
   - Calculate SHA-256 hash of JSON string
   - Ensures consistent hash regardless of field ordering

4. **Signature Generation**:
   - Concatenate: `media_hash || metadata_hash` (64 bytes total)
   - Sign with Android Keystore private key (SHA256withECDSA)
   - Export public key in DER format
   - Export attestation certificate chain if hardware-backed

5. **Upload Package**:
   - Send to backend: file, metadata JSON, signature, public key, hashes (hex), attestation chain

### Merkle Tree Implementation
For video files, a binary Merkle tree is constructed for efficient verification:

```kotlin
// 1. Split video into 1MB chunks
val chunks = splitFile(videoFile, chunkSize = 1024 * 1024)

// 2. Hash each chunk in parallel
val leafHashes = chunks.mapAsync { chunk -> sha256(chunk) }

// 3. Build tree bottom-up
while (leafHashes.size > 1) {
    leafHashes = leafHashes.chunked(2).map { pair ->
        sha256(pair[0] + pair[1])
    }
}

// 4. Root hash becomes media_hash
val rootHash = leafHashes.first()
```

### Attestation Chain
When hardware attestation is available, the full X.509 certificate chain is exported:

```
Google Root CA
    ↓
Intermediate CA(s)
    ↓
Attestation Certificate (leaf)
    ├── Public Key (matches signing key)
    ├── Attestation Extension (TEE level, timestamp, challenge)
    └── Device Identifiers (optional)
```

**Key Security Properties**:
- Private key generated in TEE, never leaves hardware
- Attestation proves key was created in secure hardware
- Certificate chain validated against Google roots on server
- StrongBox provides dedicated security chip isolation

---

## Authentication Flow

### Google Sign-In
1. **User Initiates Sign-In**:
   - Tap "Sign in with Google" button
   - Android Credential Manager presents Google account picker
   - User selects account and authorizes app

2. **Token Exchange**:
   ```kotlin
   // Get Google ID token from Credential Manager
   val googleIdToken = credentialManager.getGoogleIdToken()

   // Send to OSP backend
   POST /api/v1/auth/signin
   {
       "provider": "google",
       "token": "eyJhbGciOiJSUzI1NiIs..."
   }

   // Receive JWT tokens
   Response:
   {
       "access_token": "eyJhbGciOiJIUzI1NiIs...",
       "refresh_token": "eyJhbGciOiJIUzI1NiIs...",
       "user_id": "uuid-..."
   }
   ```

3. **Token Storage**:
   - Access token (15 min): Stored in EncryptedSharedPreferences
   - Refresh token (7 days): Stored in EncryptedSharedPreferences
   - Auto-injected via `AuthInterceptor` on all API requests

4. **Automatic Refresh**:
   - `TokenAuthenticator` intercepts 401 responses
   - Calls `/api/v1/auth/refresh` with refresh token
   - Updates stored access token
   - Retries original request with new token
   - Broadcasts `TokenRefreshed` event for pending uploads

---

## Media Capture & Upload

### Capture Flow
1. **Camera Screen**:
   - CameraX preview with real-time viewfinder
   - Toggle between photo and video mode
   - Record button for video, capture button for photos
   - Auto-focus and exposure control

2. **Metadata Collection**:
   - **Timestamp**: ISO 8601 format (`2025-01-15T14:30:00Z`)
   - **GPS Location**: Fused Location Provider (high accuracy)
     - Latitude: -90 to +90
     - Longitude: -180 to +180
   - **Device Orientation**:
     - Azimuth: 0-360° (compass heading)
     - Pitch: -180 to +180° (forward/backward tilt)
     - Roll: -90 to +90° (left/right tilt)
   - **Capture Time**: Precise moment of capture (before compression)

3. **Local Storage**:
   - Save media to app-specific cache directory
   - UUID-based filenames prevent collisions
   - Metadata stored alongside in memory

### Upload Flow
1. **Signing Phase**:
   ```kotlin
   // 1. Serialize metadata to canonical JSON
   val metadataJson = Json.encodeToString(metadata)

   // 2. Hash media file (image SHA-256 or video Merkle root)
   val mediaHash = if (isVideo) {
       MerkleTree.calculateRootHash(file)
   } else {
       sha256(file)
   }

   // 3. Hash metadata JSON
   val metadataHash = sha256(metadataJson.toByteArray())

   // 4. Sign concatenated hashes
   val signature = keystore.sign(mediaHash + metadataHash)
   ```

2. **Multipart Upload**:
   ```http
   POST /api/v1/media/upload
   Authorization: Bearer <access_token>
   Content-Type: multipart/form-data

   Parts:
   - file: [binary media file]
   - metadata: [JSON string]
   - signature: [binary ECDSA signature]
   - public_key: [DER-encoded public key]
   - media_hash: [hex string]
   - metadata_hash: [hex string]
   - attestation_chain: [comma-separated hex-encoded certs]
   ```

3. **Response Handling**:
   ```kotlin
   // Success response
   {
       "id": "uuid-...",
       "trust_score": 95,
       "capture_time": "2025-01-15T14:30:00Z",
       "verification_status": "verified",
       "user_id": "uuid-...",
       "file_path": "media/uuid-xxx.jpg"
   }
   ```

4. **Retry Logic**:
   - 401 Unauthorized: Wait for token refresh, retry once
   - Network errors: Show error, allow manual retry
   - 5-second timeout for token refresh event

---

## Trust Score Display

The backend calculates a trust score based on upload timeliness:

```
trust_score = max(0, 100 - (upload_time - capture_time).total_seconds() / 60)
```

**Interpretation**:
| Delay | Score | UI Indicator |
|-------|-------|--------------|
| 0-5 min | 95-100 | 🟢 Excellent |
| 5-30 min | 70-95 | 🟡 Good |
| 30-60 min | 40-70 | 🟠 Fair |
| 60-100 min | 1-40 | 🔴 Poor |
| >100 min | 0 | ⚫ Unreliable |

The app displays this score immediately after successful upload on the `UploadStatusUI` screen.

---

## Development Setup

### Prerequisites
- **Android Studio**: Hedgehog (2023.1.1) or later
- **JDK**: Java 11 or higher
- **Android SDK**:
  - Minimum API 24 (Android 7.0)
  - Target API 35 (Android 15)
  - Compile API 35
- **Physical Device**: Required for camera, GPS, and hardware Keystore testing
  - Emulator lacks hardware attestation and may have unreliable sensors
- **Google Play Services**: For Google Sign-In and Location Services
- **Backend**: OSP backend running locally or on a server

### Environment Configuration
Create `local.properties` in the project root (automatically gitignored):

```properties
# Backend URL
# For Android emulator: use 10.0.2.2 to access host machine's localhost
# For physical device: use your machine's local IP or public URL
backend.url=http://10.0.2.2:8000

# Google OAuth Client ID
# Get from Google Cloud Console > APIs & Services > Credentials
# Must be Android OAuth client with your app's SHA-1 fingerprint
google.client.id=123456789012-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.com
```

### Google OAuth Setup
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing
3. Enable **Google Sign-In API**
4. Create credentials:
   - Type: **OAuth 2.0 Client ID**
   - Application type: **Android**
   - Package name: `com.doublethinksolutions.osp`
   - SHA-1 fingerprint: Get from Android Studio or Gradle:
     ```bash
     ./gradlew signingReport
     ```
5. Copy the **Client ID** to `local.properties`

### Build & Run
```bash
# 1. Clone repository
git clone https://github.com/your-org/osp-android.git
cd osp-android/OSP

# 2. Create local.properties (see Environment Configuration above)
# Add backend.url and google.client.id

# 3. Sync Gradle dependencies
./gradlew build

# 4. Connect physical Android device via USB
# Enable Developer Options and USB Debugging

# 5. Install on device
./gradlew installDebug

# 6. Launch app
adb shell am start -n com.doublethinksolutions.osp/.MainActivity
```

**Alternative (Android Studio)**:
1. Open project: `File > Open > osp-android/OSP`
2. Wait for Gradle sync to complete
3. Connect device via USB
4. Click **Run** (green play button) or press `Shift + F10`

### Troubleshooting
- **"Unable to sign in"**: Check `google.client.id` and SHA-1 fingerprint match Google Cloud Console
- **"Network error"**: Verify `backend.url` is accessible from device
- **"Location unavailable"**: Enable GPS on device and grant location permission
- **"Camera error"**: Grant camera permission in system settings
- **"Signing failed"**: Check device supports hardware Keystore (API 23+)

---

## API Integration

### Base URL
Set in `local.properties`:
```
backend.url=https://api.osp.example.com
```

### Endpoints Used by App

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/v1/auth/signin` | Exchange Google ID token for JWT | ❌ |
| POST | `/api/v1/auth/refresh` | Renew access token | ✅ (Refresh Token) |
| POST | `/api/v1/media/upload` | Upload signed media | ✅ (Access Token) |
| DELETE | `/api/v1/auth/delete-account` | Delete user account | ✅ (Access Token) |

### Request Examples

#### Sign In
```http
POST /api/v1/auth/signin
Content-Type: application/json

{
    "provider": "google",
    "token": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjllYmE4..."
}

Response 200:
{
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

#### Upload Media
```http
POST /api/v1/media/upload
Authorization: Bearer <access_token>
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary

------WebKitFormBoundary
Content-Disposition: form-data; name="file"; filename="photo.jpg"
Content-Type: image/jpeg

[binary image data]
------WebKitFormBoundary
Content-Disposition: form-data; name="metadata"
Content-Type: application/json

{
    "capture_time": "2025-01-15T14:30:00Z",
    "location": {"latitude": 37.7749, "longitude": -122.4194},
    "orientation": {"azimuth": 45.0, "pitch": 10.0, "roll": 0.0}
}
------WebKitFormBoundary
Content-Disposition: form-data; name="signature"
Content-Type: application/octet-stream

[binary ECDSA signature]
------WebKitFormBoundary
Content-Disposition: form-data; name="public_key"
Content-Type: application/octet-stream

[DER-encoded public key]
------WebKitFormBoundary
Content-Disposition: form-data; name="media_hash"
Content-Type: text/plain

a1b2c3d4e5f6789012345678901234567890abcdef1234567890abcdef123456
------WebKitFormBoundary
Content-Disposition: form-data; name="metadata_hash"
Content-Type: text/plain

9876543210fedcba098765432109876543210fedcba098765432109876543210
------WebKitFormBoundary
Content-Disposition: form-data; name="attestation_chain"
Content-Type: text/plain

308201a,308203b,...
------WebKitFormBoundary--

Response 200:
{
    "id": "f1e2d3c4-b5a6-4789-0123-456789abcdef",
    "capture_time": "2025-01-15T14:30:00Z",
    "lat": 37.7749,
    "lng": -122.4194,
    "orientation": {"azimuth": 45.0, "pitch": 10.0, "roll": 0.0},
    "trust_score": 95,
    "user_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "file_path": "media/f1e2d3c4-b5a6-4789-0123-456789abcdef.jpg",
    "verification_status": "verified"
}
```

---

## Security Considerations

### Key Security Properties
1. **Hardware-Backed Keys**:
   - Private keys generated in Android Keystore
   - Never exported or accessible to app code
   - Protected by device lock screen (optional)
   - Destroyed on factory reset

2. **Attestation Validation**:
   - Certificate chain proves key origin
   - Server validates against Google root CAs
   - Detects software-emulated keys vs. hardware
   - Binds keys to specific device

3. **Token Security**:
   - JWT tokens stored in EncryptedSharedPreferences
   - AES-256-GCM encryption with Keystore master key
   - Short-lived access tokens (15 min)
   - Refresh tokens rotated on use

4. **Metadata Integrity**:
   - Canonical JSON prevents hash manipulation
   - Signature covers both media and metadata
   - Tampering detection on server side

### Permissions
Required Android permissions (declared in `AndroidManifest.xml`):

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

**Runtime Permission Handling**:
- Requested on first access to camera/location
- Graceful degradation if denied
- Settings link for post-denial re-enablement

---

## Testing

### Unit Tests
Located in `app/src/test/`:

```bash
# Run all unit tests
./gradlew test

# Run with coverage
./gradlew testDebugUnitTest jacocoTestReport
```

**Test Coverage**:
- ✅ MediaSigner key generation and signing
- ✅ MerkleTree hash calculation
- ✅ Metadata serialization and hashing
- ✅ AuthService token exchange
- ✅ SessionManager token storage/retrieval

### Instrumented Tests
Located in `app/src/androidTest/`:

```bash
# Run on connected device
./gradlew connectedAndroidTest
```

**Test Coverage**:
- ✅ Keystore integration
- ✅ EncryptedSharedPreferences
- ✅ CameraX integration
- ✅ Location provider
- ✅ Orientation sensors

### Manual Testing Checklist
- [ ] Sign in with Google account
- [ ] Capture photo with GPS enabled
- [ ] Capture video with GPS enabled
- [ ] Upload with immediate timing (expect score ~100)
- [ ] Upload with 30-minute delay (expect score ~70)
- [ ] Verify attestation chain in server logs
- [ ] Test token refresh on 401 error
- [ ] Test offline mode error handling
- [ ] Test permission denial flows
- [ ] Test camera error recovery

---

## Performance Optimizations

### Implemented Optimizations
1. **Parallel Merkle Tree Construction**:
   - Video chunks hashed concurrently using coroutines
   - 4-8x faster than sequential processing on multi-core devices
   - Configurable dispatcher (default: `Dispatchers.IO`)

2. **Streaming Uploads**:
   - Large files uploaded without loading into memory
   - OkHttp streaming with progress callbacks
   - Prevents OutOfMemoryError on large videos

3. **Lazy Key Generation**:
   - Keystore key generated only once on first use
   - Subsequent launches reuse existing key
   - Reduces cold start time

4. **Efficient Location Acquisition**:
   - Single location update per capture (not continuous)
   - High-accuracy mode with timeout
   - Cached location used if recent (<30 seconds)

5. **Coroutine-Based Networking**:
   - All network calls use `suspend` functions
   - Non-blocking main thread
   - Structured concurrency prevents leaks

---

## Known Limitations

### Current Constraints
1. **Video Processing**:
   - No client-side transcoding (H.264/AAC encoding on backend)
   - Large videos may take time to upload on slow connections
   - No resume support for interrupted uploads

2. **Offline Support**:
   - Media upload requires internet connection
   - No local queue for offline captures
   - Failed uploads must be retried manually

3. **Attestation**:
   - Hardware attestation not available on all devices
   - Emulators cannot generate valid attestation
   - StrongBox limited to high-end devices (Pixel 3+, etc.)

4. **Platform Support**:
   - Android only (iOS app in separate repository)
   - Minimum API 24 (covers ~95% of devices as of 2025)
   - CameraX requires Camera2 API (API 21+)

5. **Authentication**:
   - Google Sign-In only (Apple Sign-In not implemented on Android)
   - No anonymous mode or email/password auth

---

## Roadmap

### Completed Features
- ✅ Hardware-backed ECDSA signing
- ✅ Merkle tree video hashing
- ✅ Hardware attestation export
- ✅ Google Sign-In integration
- ✅ Automatic token refresh
- ✅ CameraX photo and video capture
- ✅ Real-time metadata capture
- ✅ Upload progress tracking
- ✅ Trust score display

### Planned Features
- 🔲 Offline upload queue with background sync
- 🔲 Video transcoding on-device (reduce upload size)
- 🔲 Multi-part resumable uploads
- 🔲 User profile management
- 🔲 Media history and deletion
- 🔲 Comment viewing and posting
- 🔲 Map view of user's uploads
- 🔲 Push notifications for comments/replies
- 🔲 Dark mode support
- 🔲 Localization (i18n) for multiple languages

---

## Dependencies

### Key Libraries
| Library | Version | Purpose |
|---------|---------|---------|
| Jetpack Compose | 2024.12.01 (BOM) | Declarative UI framework |
| Kotlin Coroutines | 1.7.3 | Asynchronous programming |
| Retrofit | 2.9.0 | HTTP client |
| OkHttp | 4.12.0 | Networking layer |
| CameraX | 1.4.1 | Camera API |
| Google Play Services Auth | 21.2.0 | Google Sign-In |
| Google Play Services Location | 21.3.0 | GPS location |
| AndroidX Security Crypto | 1.1.0-alpha06 | Encrypted storage |
| AndroidX Credentials | 1.5.0 | Credential Manager |
| Kotlinx Serialization | 1.7.3 | JSON serialization |
| Gson | 2.10.1 | Retrofit JSON converter |
| Material3 | 1.3.1 | Material Design 3 |
| Accompanist Permissions | 0.36.0 | Permission handling |

### Build Configuration
```kotlin
// app/build.gradle.kts
android {
    compileSdk = 35
    defaultConfig {
        minSdk = 24
        targetSdk = 35
        versionCode = 7
        versionName = "1.0"
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}
```

---

## Build Variants

### Debug Build
- **Signature**: Debug keystore (auto-generated)
- **Backend**: Local development server
- **Logging**: Verbose (all network traffic logged)
- **Minification**: Disabled

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Release Build
- **Signature**: Production keystore (requires signing config)
- **Backend**: Production API server
- **Logging**: Errors only
- **Minification**: Enabled with ProGuard

```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

**ProGuard Configuration**:
- Keep Retrofit service interfaces
- Keep Gson models
- Keep Keystore classes
- Obfuscate application logic

---

## Contributing

We welcome contributions to the OSP Android app! Please follow these guidelines:

### Development Workflow
1. **Fork** the repository
2. **Create feature branch**: `git checkout -b feature/your-feature-name`
3. **Make changes** with clear commit messages:
   - `feat: Add offline upload queue`
   - `fix: Resolve token refresh race condition`
   - `refactor: Extract signing logic to repository`
4. **Write tests** for new functionality
5. **Run tests**: `./gradlew test connectedAndroidTest`
6. **Format code**: Use Android Studio's auto-format (Ctrl+Alt+L)
7. **Submit pull request** with description of changes

### Code Style
- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use descriptive variable names (`captureTimestamp` not `ts`)
- Document public APIs with KDoc
- Keep functions focused and under 50 lines
- Use `suspend` for all async operations (no callbacks)

### Testing Requirements
- Unit tests for all business logic
- Instrumented tests for Android APIs (Keystore, Camera, Location)
- No regressions in existing tests
- >80% code coverage for new code

---

## License
MIT License - see LICENSE file for details

---

## Contact & Support

- **GitHub Issues**: [Report bugs or request features](https://github.com/your-org/osp-android/issues)
- **Backend Repository**: [osp-backend](https://github.com/your-org/osp-backend)
- **iOS Repository**: [osp-ios](https://github.com/your-org/osp-ios)
- **Documentation**: See inline KDoc comments and backend README

---

## Acknowledgments

- **Android Keystore**: Hardware-backed key storage on Android
- **CameraX**: Modern camera API by Google
- **Jetpack Compose**: Declarative UI toolkit
- **Kotlin Coroutines**: Structured concurrency library
- **Retrofit**: Type-safe HTTP client
- **Google Identity Services**: Modern authentication APIs

---

**OSP Android** – Capturing verifiable truth through cryptographic evidence and hardware-backed security.
