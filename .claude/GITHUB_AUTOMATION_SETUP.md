# GitHub Actions Automation: Complete Setup Guide for SplitCruiser

This guide walks through automating the Google Cloud, Play Store, and Firebase setup for SplitCruiser using GitHub Actions. The app is being rebranded from Split Cruiser (com.aistudio.splitcruiser.krqmzb) to SplitCruiser (com.splitcruiser.app).

## Overview of What Gets Automated

| Component | Method | Effort |
|-----------|--------|--------|
| Google Cloud Project | `gcloud` CLI (automated) | Automated ✓ |
| Service Account | `gcloud` CLI (automated) | Automated ✓ |
| Play Developer API | `gcloud services enable` (automated) | Automated ✓ |
| Firebase Project | `gcloud` CLI (automated) | Automated ✓ |
| Firebase Authentication | Firebase CLI (automated) | Automated ✓ |
| Firebase Firestore | Firebase CLI (automated) | Automated ✓ |
| **Play Store App Listing** | **Play Console (manual)** | **Manual (5 min)** ⚠ |
| Android Signing Key | `keytool` (one-time) | One-time setup |
| GitHub Secrets | Manual paste | Manual paste |

---

## Phase 1: Bootstrap Service Account (One-Time, ~10 minutes)

This creates a "bootstrap" service account that can create new GCP projects. You only do this once.

### Step 1.1: Create Bootstrap GCP Project

1. Go to https://console.cloud.google.com/
2. Sign in with your Google account (personal or Workspace)
3. Click the **project dropdown** at the top (where it says "My First Project" or similar)
4. Click **New Project**
   - **Project name**: `gcp-bootstrap`
   - **Organization**: Select your organization (or leave as personal)
5. Click **Create**
6. Wait for the project to be created (~30 seconds)

### Step 1.2: Enable Required APIs

In the `gcp-bootstrap` project:

1. Go to **APIs & Services** → **Library** (left sidebar)
2. Search for **Cloud Resource Manager API**
   - Click it → Click **Enable**
3. Search for **Service Usage API**
   - Click it → Click **Enable**
4. Search for **Billing Account API** (only if you have a paid Google Cloud account)
   - Click it → Click **Enable**

### Step 1.3: Create Bootstrap Service Account

1. Go to **APIs & Services** → **Credentials** (left sidebar)
2. Click **+ Create Credentials** → **Service Account**
3. Fill in:
   - **Service account name**: `gcp-project-creator`
   - **Service account ID**: `gcp-project-creator` (auto-filled)
   - **Description**: (optional) `Creates new GCP projects for CI/CD`
4. Click **Create and Continue**
5. **Grant roles**: Click **+ Grant Role** and add:
   - **Project Creator** (search for it)
   - **Billing Account User** (only if you have a paid billing account)
6. Click **Continue** → **Done**

### Step 1.4: Create and Download Service Account Key

1. In **APIs & Services** → **Credentials**, find the service account you just created
2. Click on the **service account email** (e.g., `gcp-project-creator@gcp-bootstrap.iam.gserviceaccount.com`)
3. Go to the **Keys** tab
4. Click **Add Key** → **Create new key**
5. Choose **JSON** → **Create**
6. **Save the JSON file** to your computer (you'll need it in step 2 below)

### Step 1.5: Get Your Billing Account ID (Optional, but Recommended)

If you have a paid Google Cloud billing account:

1. Go to **Billing** (left sidebar of Cloud Console, or https://console.cloud.google.com/billing)
2. Click on your billing account
3. Go to **Account settings**
4. Copy the **Billing Account ID** (format: `XXXXXX-XXXXXX-XXXXXX`)

---

## Phase 2: Add Bootstrap Credentials to GitHub

1. Go to your GitHub repository: **Settings** → **Secrets and variables** → **Actions**
2. Click **New repository secret**

Add these two secrets:

| Secret Name | Value | From Where |
|---|---|---|
| `SETUP_GCP_KEY_JSON` | The entire JSON file content | Step 1.4 above |
| `SETUP_GCP_PROJECT_ID` | `gcp-bootstrap` | Step 1.1 above |

(Optional) If you have a billing account, also add:

| Secret Name | Value |
|---|---|
| `SETUP_BILLING_ACCOUNT_ID` | From Step 1.5 above |

---

## Phase 3: Run the Automation Workflow

### Step 3.1: Trigger the Setup Workflow

1. Go to your GitHub repo → **Actions** tab
2. Find the workflow named **"One-Time Setup — Google Cloud Project"**
3. Click on it
4. Click the **Run workflow** button (right side, under the "This workflow has a workflow_dispatch trigger" message)
5. Fill in the inputs:
   - **app_project_id**: `splitcruiser-prod-2026` (or any unique ID you prefer)
   - **app_name**: `SplitCruiser`
6. Click **Run workflow**

### Step 3.2: Wait for Completion

The workflow will:
1. Create a new GCP project with your chosen ID
2. Enable the Play Developer API and Firebase APIs
3. Create a service account named `play-store-ci`
4. Grant the service account the necessary roles
5. Create and output a JSON key for the service account

**Expected duration**: 2–3 minutes

### Step 3.3: Copy the Service Account JSON

Once the workflow completes:

1. Click on the workflow run to view details
2. Look for the step titled **"Create Service Account Key"**
3. Expand it and copy the JSON output (starts with `{` and ends with `}`)

---

## Phase 4: Create Android Signing Key (One-Time)

This creates the keystore that signs your APK for Play Store release.

### Option A: Generate Locally (if you have keytool)

```bash
keytool -genkey -v -keystore upload-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias upload \
  -storepass splitcruiser_store_password \
  -keypass splitcruiser_key_password \
  -dname "CN=SplitCruiser,O=SplitCruiser Inc,L=San Francisco,S=CA,C=US"
```

Then convert to base64:
```bash
cat upload-key.jks | base64 -w0 > keystore.base64
cat keystore.base64
```

**Save the output.**

### Option B: Generate via GitHub Actions (if keytool not available locally)

Create a temporary workflow `.github/workflows/generate-keystore.yml`:

```yaml
name: Generate Keystore

on:
  workflow_dispatch:

jobs:
  generate:
    runs-on: ubuntu-latest
    steps:
      - name: Generate keystore
        run: |
          keytool -genkey -v -keystore upload-key.jks \
            -keyalg RSA -keysize 2048 -validity 10000 \
            -alias upload \
            -storepass splitcruiser_store_password \
            -keypass splitcruiser_key_password \
            -dname "CN=SplitCruiser,O=SplitCruiser Inc,L=San Francisco,S=CA,C=US"
          
          base64 -w0 < upload-key.jks
      
      - name: Upload keystore
        uses: actions/upload-artifact@v4
        with:
          name: upload-key
          path: upload-key.jks
```

Run it via **Actions** → **Generate Keystore** → **Run workflow**, then download the artifact.

---

## Phase 5: Create Play Store App Listing (Manual, 5 Minutes)

The Play Developer API **does not** support creating new app listings. You must do this manually in Play Console once.

### Step 5.1: Go to Play Console

1. Open https://play.google.com/console/
2. Sign in with your Google Play Developer account (the one with the paid membership)

### Step 5.2: Create the App

1. Click **Create app**
2. Fill in:
   - **App name**: `SplitCruiser`
   - **Default language**: English
   - **App or game**: App
   - **Paid or free**: Free
   - **Declarations**: Accept the policy
3. Click **Create app**

### Step 5.3: Note the App ID

Once created, you'll see the app dashboard. The app listing is now ready to receive your first build.

**That's it!** You can publish builds to the internal testing track via GitHub Actions now (you don't need to manually upload anything in Play Console again).

---

## Phase 6: Firebase Setup (Partially Automated)

### Step 6.1: Initialize Firebase in the GCP Project

1. Go to https://firebase.google.com/
2. Click **Go to console**
3. Click **+ Add project**
4. Select the GCP project you created in Phase 3 (`splitcruiser-prod-2026`)
5. Click **Continue**
6. Enable Google Analytics (optional but recommended) → **Continue**
7. Wait for Firebase to initialize (~1–2 minutes)

### Step 6.2: Create an Android App in Firebase

1. In the Firebase console, click **+ Add app** → **Android**
2. Fill in:
   - **Android package name**: `com.splitcruiser.app`
   - **App nickname**: `SplitCruiser Android`
   - **App ID** (optional): Leave blank or use `com.splitcruiser.app`
3. Click **Register app**
4. **Download `google-services.json`** to your computer (you'll upload this in GitHub)
5. Skip the gradle setup (you already have it configured)

### Step 6.3: Create Firestore Database

1. In Firebase console, go to **Firestore Database** (left sidebar)
2. Click **Create database**
3. Choose:
   - **Location**: `us-central1` (or your preferred region)
   - **Security rules**: Start in **Test mode** (you'll add proper rules later)
4. Click **Create**

### Step 6.4: Enable Authentication

1. In Firebase console, go to **Authentication** (left sidebar)
2. Click **Get started**
3. Click on **Email/Password** → **Enable** → **Save**

### Step 6.5: Create Storage Bucket

1. In Firebase console, go to **Storage** (left sidebar)
2. Click **Get started**
3. Choose a region and click **Done**

### Step 6.6: Get Firebase Configuration Values

1. In Firebase console, click the **settings icon** (⚙) → **Project settings**
2. Go to **Your apps** → Click your Android app → Copy these values:
   - **Web API Key** → `FIREBASE_API_KEY`
   - **App ID** → `FIREBASE_APP_ID`
   - **Project ID** → `FIREBASE_PROJECT_ID`
   - **Storage Bucket** (from the Storage section) → `FIREBASE_STORAGE_BUCKET`

---

## Phase 7: Add All Secrets to GitHub

1. Go to GitHub repo → **Settings** → **Secrets and variables** → **Actions**
2. Add each secret below:

### Android Signing Secrets

| Secret Name | Value | From Where |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | Your keystore in base64 (from Phase 4) | keytool output or artifact |
| `ANDROID_KEYSTORE_PASSWORD` | `splitcruiser_store_password` | Phase 4 above |
| `ANDROID_KEY_PASSWORD` | `splitcruiser_key_password` | Phase 4 above |
| `ANDROID_KEY_ALIAS` | `upload` | Phase 4 above |

### Play Store Secrets

| Secret Name | Value | From Where |
|---|---|---|
| `PLAY_SERVICE_ACCOUNT_JSON` | Service account JSON from Phase 3 | Workflow output |

### Firebase Secrets

| Secret Name | Value | From Where |
|---|---|---|
| `GOOGLE_SERVICES_JSON` | `google-services.json` base64 | Phase 6 step 2 |
| `FIREBASE_API_KEY` | Web API Key | Phase 6 step 6 |
| `FIREBASE_APP_ID` | App ID | Phase 6 step 6 |
| `FIREBASE_PROJECT_ID` | `splitcruiser-prod-2026` | Phase 3 |
| `FIREBASE_STORAGE_BUCKET` | Storage bucket name | Phase 6 step 6 |

### (Optional) Gemini API Key

If your app uses Gemini features:

| Secret Name | Value | From Where |
|---|---|---|
| `GEMINI_API_KEY` | Your API key | Google AI Studio |

---

## Phase 8: Test the CI/CD Pipeline

### Test 1: Run CI on a Feature Branch

1. Create a feature branch: `git checkout -b test/ci`
2. Make a dummy change (e.g., update a comment)
3. Commit and push: `git push -u origin test/ci`
4. Go to GitHub → **Pull requests** → Create a PR
5. Verify the **CI** workflow completes successfully (builds and tests)

### Test 2: Test Release Workflow (Without Publishing)

1. Go to **Actions** → **Release Android** workflow
2. Click **Run workflow** → Fill in the inputs:
   - No special inputs needed; defaults will work
3. Click **Run workflow**
4. Wait for it to complete (~3–4 minutes)
5. **Verify**: The workflow should:
   - Build the AAB successfully
   - Upload it as an artifact (even if publish step is skipped)
   - Report whether publish was skipped (if secrets were missing or it was a dispatch run)

### Test 3: Test Release to Play Internal Testing

Once all secrets are in place and the workflow is successful:

1. Merge your changes to `main`
2. Go to **Actions** → wait for the workflow to run automatically
3. Once complete, verify a new build appeared in **Play Console** → **Testing** → **Internal testing**

---

## Troubleshooting

### Symptom: Workflow fails with "secrets context forbidden"

**Cause**: You used `${{ secrets.X }}` in an `if:` condition.

**Fix**: Use job-level `env` to check secrets (already done in the workflows).

### Symptom: "keytool: command not found"

**Cause**: Java is not installed or keytool is not on PATH.

**Fix**: Use Option B (GitHub Actions workflow) in Phase 4.

### Symptom: Play Console shows "No apps found"

**Cause**: You skipped Phase 5 (Play Store app creation).

**Fix**: Create the app listing manually in Play Console.

### Symptom: Build fails with "Variant.all() API was removed"

**Cause**: AGP version incompatibility.

**Fix**: Verify `AGP 8.8.0` and `Kotlin 2.2.10` in `gradle/libs.versions.toml`.

### Symptom: "android.permission.INTERNET" error at runtime

**Cause**: Permission not declared in AndroidManifest.xml.

**Fix**: Already added; verify `app/src/main/AndroidManifest.xml` includes it.

---

## Verification Checklist

After setup, verify:

- [ ] Gradle wrapper works: `./gradlew --version`
- [ ] Android debug build compiles: `./gradlew :app:assembleDebug`
- [ ] Tests pass: `./gradlew :app:testDebugUnitTest`
- [ ] CI workflow runs on PR branches
- [ ] Release workflow builds AAB successfully
- [ ] New build appears in Play Console internal testing track
- [ ] All 11 GitHub secrets are set (check in Settings → Secrets)

---

## Reference: Complete Secrets List

**Total secrets to add: 11**

1. `ANDROID_KEYSTORE_BASE64`
2. `ANDROID_KEYSTORE_PASSWORD`
3. `ANDROID_KEY_PASSWORD`
4. `ANDROID_KEY_ALIAS`
5. `PLAY_SERVICE_ACCOUNT_JSON`
6. `GOOGLE_SERVICES_JSON`
7. `FIREBASE_API_KEY`
8. `FIREBASE_APP_ID`
9. `FIREBASE_PROJECT_ID`
10. `FIREBASE_STORAGE_BUCKET`
11. `GEMINI_API_KEY` (optional)

Plus 3 temporary bootstrap secrets (can be deleted after Phase 3):
- `SETUP_GCP_KEY_JSON`
- `SETUP_GCP_PROJECT_ID`
- `SETUP_BILLING_ACCOUNT_ID` (optional)

---

## Next Steps After Setup

1. ✓ Complete Phases 1–7 above
2. ✓ Verify all 11 secrets are set
3. ✓ Run CI tests on a feature branch (Test 1)
4. ✓ Merge to `main` to trigger automated release
5. Once Android is stable → Begin Compose Multiplatform iOS migration (Phase 2–5 of the main plan)

For detailed release pipeline info, refer to `.claude/skills/release-pipeline/SKILL.md`.
