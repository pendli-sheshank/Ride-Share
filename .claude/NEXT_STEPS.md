# Next Steps for SplitCruiser CI/CD Setup

## Summary of Completed Work

✅ **Package Rebranding**: Updated entire codebase from `com.example.*` → `com.splitcruiser.app`
  - 30+ files renamed with package declarations updated
  - R imports, BuildConfig references, and data class references fixed
  - Shared module updated for new package structure

✅ **GitHub Actions Automation Workflow**: Created `.github/workflows/setup-gcloud.yml`
  - Automates Google Cloud project creation
  - Automates service account creation with Play API + Firebase permissions
  - Generates and outputs service account JSON for use as GitHub secret

✅ **Comprehensive Setup Guide**: Created `.claude/GITHUB_AUTOMATION_SETUP.md`
  - 8 phases covering complete automation setup
  - Detailed instructions for bootstrap service account creation
  - Step-by-step guide for Play Store, Firebase, and signing key setup
  - Verification checklist to confirm all pieces are in place

✅ **Setup Workflow for GCP**: `.github/workflows/setup-gcloud.yml` ready for use

---

## Immediate Next Steps (Do These in Order)

### Phase 1: Bootstrap Setup (10 minutes)

Follow phases 1–2 in `.claude/GITHUB_AUTOMATION_SETUP.md`:

1. **Create `gcp-bootstrap` GCP project**
   - Go to https://console.cloud.google.com/
   - Click **New Project**, name it `gcp-bootstrap`
   - Enable Cloud Resource Manager API, Service Usage API, and (optionally) Billing Account API

2. **Create bootstrap service account**
   - Go to **APIs & Services** → **Credentials**
   - **Create Service Account** → name it `gcp-project-creator`
   - Grant roles: **Project Creator**, **Billing Account User** (if you have paid billing)
   - Create a JSON key

3. **Add bootstrap secrets to GitHub**
   - GitHub repo → **Settings** → **Secrets and variables** → **Actions**
   - Add `SETUP_GCP_KEY_JSON` (the bootstrap service account JSON)
   - Add `SETUP_GCP_PROJECT_ID` (set to `gcp-bootstrap`)
   - (Optional) Add `SETUP_BILLING_ACCOUNT_ID` if you have a paid billing account

### Phase 2: Run Automation Workflow (3 minutes)

1. Go to your repo → **Actions** tab
2. Find **"One-Time Setup — Google Cloud Project"** workflow
3. Click **Run workflow**
4. Fill in:
   - **app_project_id**: `splitcruiser-prod-2026`
   - **app_name**: `SplitCruiser`
5. **Copy the service account JSON output** when complete

### Phase 3: Create Play Store App Listing (5 minutes)

**This is the ONLY manual step that cannot be automated.**

1. Go to https://play.google.com/console/
2. Click **Create app**
3. Fill in:
   - **App name**: `SplitCruiser`
   - **Default language**: English
   - **App or game**: App
   - **Paid or free**: Free
4. Click **Create app**

---

## Adding Secrets to GitHub (Follow This Checklist)

Once you have all the values, add them to **GitHub** → **Settings** → **Secrets and variables** → **Actions**:

**Android Signing (generate locally or in GitHub, see Phase 4 in setup guide):**
- [ ] `ANDROID_KEYSTORE_BASE64` 
- [ ] `ANDROID_KEYSTORE_PASSWORD`
- [ ] `ANDROID_KEY_PASSWORD`
- [ ] `ANDROID_KEY_ALIAS` = `upload`

**Play Store:**
- [ ] `PLAY_SERVICE_ACCOUNT_JSON` (from Phase 2 above)

**Firebase (from Phase 6 in setup guide):**
- [ ] `GOOGLE_SERVICES_JSON`
- [ ] `FIREBASE_API_KEY`
- [ ] `FIREBASE_APP_ID`
- [ ] `FIREBASE_PROJECT_ID` = `splitcruiser-prod-2026`
- [ ] `FIREBASE_STORAGE_BUCKET`

**Optional:**
- [ ] `GEMINI_API_KEY` (if your app uses Gemini)

**Temporary (can delete after Phase 3):**
- [ ] `SETUP_GCP_KEY_JSON` (can delete after running setup workflow)
- [ ] `SETUP_GCP_PROJECT_ID` (can delete after running setup workflow)
- [ ] `SETUP_BILLING_ACCOUNT_ID` (can delete after running setup workflow)

---

## Testing the CI/CD Pipeline

### Test 1: CI on a Feature Branch (Recommended First)

```bash
git checkout -b test/ci
git commit --allow-empty -m "Test CI"
git push -u origin test/ci
# Go to GitHub → create PR → watch CI workflow run
```

### Test 2: Release Workflow Without Publishing

1. Go to **Actions** → **Release Android**
2. Click **Run workflow** → **Run workflow**
3. Wait for it to complete
4. **Verify**: Workflow should build AAB successfully (even if publish is skipped)

### Test 3: Release to Play Internal Testing

Once all 11 secrets are set and test 2 succeeds:

```bash
git push origin main
# Watch the Release Android workflow run automatically
# After completion, check Play Console → Internal testing track for new build
```

---

## Build Status

A clean build of `:app:assembleDebug` is currently running in the background to verify the package rebranding didn't break the app. This should complete shortly. If there are any remaining compilation errors, they'll need to be fixed before the CI workflow will pass.

**To check build status manually:**
```bash
./gradlew :app:assembleDebug
```

---

## Files Added/Modified This Session

**New files:**
- `.github/workflows/setup-gcloud.yml` — Automates GCP + service account creation
- `.claude/GITHUB_AUTOMATION_SETUP.md` — Complete setup guide (8 phases)
- `.claude/NEXTST EPS.md` — This file

**Modified files:**
- `app/build.gradle.kts` — Updated namespace and applicationId
- `app/src/main/AndroidManifest.xml` — Updated activity reference
- `shared/build.gradle.kts` — Updated package references
- All `.claude/*.md` docs — Updated package references

**Renamed directories:**
- `app/src/main/java/com/example/` → `app/src/main/java/com/splitcruiser/app/`
- `shared/src/commonMain/kotlin/com/example/` → `shared/src/commonMain/kotlin/com/splitcruiser/app/`
- Test files updated similarly

---

## Key Points to Remember

1. **The one-time setup workflow (`setup-gcloud.yml`) is automated** — just run it once with your bootstrap credentials
2. **Play Store app listing CANNOT be automated** — you must create it once manually (5 minutes)
3. **All other setup can happen in GitHub Actions** — Google Cloud, service accounts, Firebase configuration
4. **Secrets should be treated as sensitive** — never commit them to the repo
5. **After all secrets are added**, pushing to `main` will automatically trigger releases to Play internal testing

---

## Need Help?

- For detailed setup instructions: See `.claude/GITHUB_AUTOMATION_SETUP.md`
- For release pipeline troubleshooting: See `.claude/skills/release-pipeline/SKILL.md`
- For Firebase configuration: See `.claude/FIREBASE_CONFIGURATION_GUIDE.md`

Good luck! 🚀
