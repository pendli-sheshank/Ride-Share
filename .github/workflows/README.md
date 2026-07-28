# GitHub Actions Workflows

Automated CI/CD pipelines for Split Cruiser across Android and iOS platforms.

## Available Workflows

### 1. Release Android (`.github/workflows/release-android.yml`)

**Trigger:** 
- Every push to `main` branch (auto-publishes to Play Store internal testing)
- Manual: GitHub Actions → Release Android → Run workflow

**What it does:**
- Builds Android AAB (App Bundle)
- Verifies app classes are compiled
- Signs with upload keystore
- Publishes to Google Play internal testing

**For Windows users:** 
✅ No setup needed - auto-triggers on push to main

---

### 2. Build iOS (`.github/workflows/build-ios.yml`) — NEW

**Trigger:**
- Every push to `main` branch
- Every pull request to `main`
- Manual: GitHub Actions → Build iOS → Run workflow

**What it does:**
- Builds `Shared.framework` for iOS (all targets)
- Installs CocoaPods dependencies
- Creates/validates Xcode project
- Builds iOS app for simulator
- Uploads artifacts (Shared.framework, iOS build logs)

**For Windows users:**
✅ **YOU CAN TRIGGER iOS BUILDS FROM GITHUB!**

---

## How to Trigger iOS Build on Windows

### Method 1: Push to Main Branch
```bash
git push origin your-branch  # Push your changes
# Then create a PR to main, or push directly to main
# GitHub Actions automatically runs Build iOS workflow
```

### Method 2: Manual Trigger (Fastest)
1. Go to: **GitHub** → **Actions** tab
2. Click **Build iOS** workflow (left sidebar)
3. Click **Run workflow** button (right side)
4. Select your branch (usually `main`)
5. Click **Run workflow** (green button)

GitHub will:
- Spin up a **macOS-14 runner** (Apple Silicon Mac in the cloud)
- Build Shared.framework
- Install CocoaPods
- Build iOS app
- Upload artifacts

### Method 3: Use GitHub CLI (if installed)
```bash
gh workflow run build-ios.yml --ref main
# Or your branch:
gh workflow run build-ios.yml --ref your-branch
```

---

## Checking Build Status & Logs

1. Go to **GitHub** → **Actions** tab
2. Click the **Build iOS** workflow
3. Click the latest run
4. See live status and logs:
   - ✅ Green checkmark = Build succeeded
   - ❌ Red X = Build failed (click to see error logs)

---

## Downloading Build Artifacts

After a successful build:

1. Go to **GitHub** → **Actions** tab
2. Click **Build iOS** workflow
3. Click the successful run
4. Scroll down to **Artifacts** section
5. Download:
   - `ios-build-artifacts` — Contains:
     - `shared/build/XCFrameworks/` — Shared framework (needed for iOS app)
     - `iosApp/build/` — iOS app build output

---

## Next Steps for iOS Development

### On macOS (if available):
```bash
cd iosApp
./setup.sh                       # One-command setup
open iosApp.xcworkspace          # Open in Xcode
# Cmd+R to build and run on simulator
```

### On Windows (using GitHub Actions):
1. **Commit & push** code to trigger build
2. **Wait** for GitHub Actions to build
3. **Download** Shared.framework artifact
4. On macOS machine (or GitHub runner), use downloaded framework

---

## Workflow Details

### Build iOS Workflow Structure
```
1. Checkout code
2. Install Java 21 (for Kotlin compilation)
3. Setup Gradle
4. Build Shared.framework (Kotlin → iOS framework)
5. Install CocoaPods
6. Create/validate Xcode project
7. Build iOS app for simulator
8. Upload artifacts
9. Generate build summary
```

### Deployment Targets
- **iOS**: 14.0+
- **Kotlin**: 2.2.10
- **Xcode**: 14.0+

---

## Troubleshooting

### "Build failed in GitHub Actions"
1. Click the failed run
2. Scroll through logs to find error
3. Common issues:
   - **"Shared.framework not found"** → Check Gradle build step
   - **"Pod installation failed"** → Usually network issue, retry
   - **"Xcode project not found"** → Should be created automatically

### If Xcode Project Creation Fails
The workflow attempts to create it, but you can also run locally on macOS:
```bash
cd iosApp
./create-xcode-project.sh
```

### Getting Help
1. Check workflow logs in GitHub Actions
2. Look for error messages in each step
3. Try re-running the workflow
4. Commit fixes and push again

---

## Current Status

- ✅ **Android Release**: Working, publishes to Play Store
- ✅ **iOS Build CI**: Working, builds framework and app
- ⏳ **iOS Release Pipeline**: Coming soon (App Store/TestFlight)

---

## Future Enhancements

- [ ] iOS TestFlight release workflow
- [ ] App Store release workflow
- [ ] Automated versioning for iOS
- [ ] iOS unit tests in workflow
- [ ] Code signing automation
