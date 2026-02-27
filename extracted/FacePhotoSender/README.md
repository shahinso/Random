# 📸 Face Photo Sender

An Android app that automatically finds photos of your friends/family in your last 50 gallery photos using **ML Kit face recognition**, then sends them via **WhatsApp** — no manual photo selection needed.

---

## ✨ Features

| Feature | Details |
|---|---|
| 🔍 Auto-scan | Scans the last 50 photos from your gallery |
| 🤖 Face Detection | Google ML Kit on-device face detection (works offline, privacy-preserving) |
| 👤 Multi-contact | Register unlimited contacts with their face |
| 📤 WhatsApp send | Sends matched photos directly via WhatsApp |
| ✅ Review before send | See matched photos, deselect unwanted ones before sending |
| 🔒 Privacy-first | All face processing is done **on-device** — no cloud upload |

---

## 🏗️ Architecture

```
app/
├── data/
│   ├── Models.kt           → Room entities (Contact, MatchedPhoto)
│   ├── DatabaseProvider.kt → Singleton Room DB
│   └── PhotoRepository.kt  → MediaStore query for last 50 photos
├── face/
│   └── FaceRecognitionEngine.kt  → ML Kit face detection + embedding
├── whatsapp/
│   └── WhatsAppSender.kt   → WhatsApp Intent integration
├── adapter/
│   └── ContactCardAdapter.kt
├── MainActivity.kt         → Main scan + contact list UI
├── ContactSetupActivity.kt → Add/edit contact + enrol face
├── FaceMatchResultActivity.kt → Review matched photos
└── MainViewModel.kt        → Business logic + coroutines
```

---

## 📱 How to Use

### Step 1 — Add a Contact
1. Tap the **+** FAB button
2. Enter the person's **name**
3. Enter their **WhatsApp phone number** in E.164 format: `+14155552671`
4. Tap **"Pick Reference Face Photo"** and select a clear photo of their face
5. Wait for "✅ Face enrolled successfully"
6. Tap **Save Contact**

### Step 2 — Scan Photos
1. On the main screen, tap **"🔍 Scan Last 50 Photos"**
2. Grant storage permission if prompted
3. The app scans all 50 photos, detecting faces and comparing to enrolled contacts
4. Results appear as cards — e.g. "3 photos found of Alice"

### Step 3 — Send via WhatsApp
1. Tap **"📤 Send via WhatsApp"** on any contact card
2. WhatsApp opens with the matched photos pre-attached
3. Select the chat / confirm send

---

## ⚙️ Setup & Build

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 26+
- Kotlin 1.9+

### Build
```bash
git clone <repo>
cd FacePhotoSender
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Install
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 🧠 Face Recognition Technical Notes

The app uses **Google ML Kit Face Detection** with:

- **Landmark detection** — eyes, nose, mouth, ears, cheeks
- **Geometric embedding** — 22-dimensional descriptor derived from normalised landmark positions (scale & position invariant)
- **Cosine similarity** matching — threshold: `0.72` (tunable in `FaceRecognitionEngine.kt`)

### Accuracy Tips
- Use a **clear, well-lit, front-facing** photo for enrolment
- Photos with the person looking directly at the camera work best
- If you get false positives/negatives, adjust `SIMILARITY_THRESHOLD` in `FaceRecognitionEngine.kt`

### Upgrading to FaceNet (Optional)
For production-grade recognition, replace the geometric embedding with a **TFLite FaceNet model**:
1. Download `facenet.tflite` (e.g. from [MTCNN + FaceNet on TF Hub](https://tfhub.dev))
2. Add to `app/src/main/assets/`
3. Replace `extractEmbedding()` in `FaceRecognitionEngine.kt` with TFLite inference
4. Update embedding size from 22 → 128 (FaceNet embedding dimension)

---

## 🔒 Permissions

| Permission | Why |
|---|---|
| `READ_MEDIA_IMAGES` (API 33+) | Read photos from gallery |
| `READ_EXTERNAL_STORAGE` (API ≤32) | Read photos from gallery |

No internet permission is needed for face detection — it's fully on-device.

---

## 📞 WhatsApp Integration

The app uses Android's `Intent.ACTION_SEND` with `package = "com.whatsapp"` to open WhatsApp directly with the photo pre-attached. The `jid` extra pre-selects the contact by phone number.

**Note:** WhatsApp must be installed. The app checks for installation and shows a Toast if not found.

---

## 🛠️ Troubleshooting

| Problem | Solution |
|---|---|
| "No face detected" | Use a clearer selfie; face must be ≥10% of image |
| No matches found | Lower `SIMILARITY_THRESHOLD` to `0.60` |
| Too many false matches | Raise threshold to `0.80` |
| WhatsApp not opening | Ensure WhatsApp is installed; check phone number format |
| Storage permission denied | Go to Settings → Apps → Face Photo Sender → Permissions |
