# iOS App Release Pipeline & App Store Distribution

## Overview
This document outlines the complete process for building, testing, and releasing the SawaariShare iOS app on the App Store.

## Prerequisites

### Apple Developer Account Setup
1. **Enroll in Apple Developer Program** ($99/year)
   - Visit [developer.apple.com/enroll](https://developer.apple.com/enroll)
   - Requires Apple ID and valid payment method
   - Approval typically takes 24-48 hours

2. **Create App Store Connect Account**
   - Login to [appstoreconnect.apple.com](https://appstoreconnect.apple.com)
   - Add new app with details below

3. **App Information**
   - **App Name:** SawaariShare
   - **Bundle ID:** com.splitcruiser.app.ios
   - **Category:** Transportation
   - **Minimum iOS Version:** 14.0+
   - **Supported Devices:** iPhone & iPad

### Required Certificates & Profiles

#### Code Signing
- **Development Certificate:** For local testing
- **Distribution Certificate:** For App Store submission
- **Provisioning Profiles:** Tied to certificates and bundle ID

#### Installation Steps
1. Open Xcode → Preferences → Accounts
2. Select Apple Developer account
3. Click "Manage Certificates"
4. Create/download:
   - iOS Development
   - iOS Distribution
5. Download provisioning profiles from App Store Connect

### Local Setup
```bash
# Install required tools
brew install xcodes
xcodes install 14.3.1  # or latest stable

# Setup Git credentials for secure storage
git config --global credential.helper osxkeychain
```

## Build Configuration

### Xcode Project Settings

**General Tab:**
- Bundle Identifier: `com.splitcruiser.app.ios`
- Minimum Deployment Target: `iOS 14.0`
- Team: [Your Developer Team]
- Signing Certificate: Select distribution certificate

**Build Settings:**
```
Code Sign Identity: "iPhone Developer" (Debug), "iPhone Distribution" (Release)
Code Sign Style: Automatic (managed by Xcode)
Provisioning Profile: SawaariShare AppStore
```

**App Icons & Launch Screen:**
- Icons must be 1024×1024 PNG
- LaunchScreen.storyboard configured with app name & subtitle
- Already included in Assets.xcassets/AppIcon.appiconset

### Version Management

Update in Xcode or directly in `Info.plist`:
```xml
<key>CFBundleShortVersionString</key>
<string>1.0.0</string>  <!-- Marketing version (1.0.0 = Major.Minor.Patch) -->

<key>CFBundleVersion</key>
<string>1</string>  <!-- Build number (increment for each submission) -->
```

**Versioning Strategy:**
- Market version (1.0.0): Shown to users
- Build number (1, 2, 3...): Increment for each App Store submission
- Each TestFlight build increments build number

Example progression:
```
Version 1.0.0, Build 1   → Internal testing
Version 1.0.0, Build 2   → TestFlight beta
Version 1.0.0, Build 3   → App Store release
Version 1.0.1, Build 4   → Bug fix release
Version 1.1.0, Build 5   → Feature release
```

## Release Process

### Phase 1: Pre-Release Preparation

1. **Update Version Numbers**
   - Set marketing version (1.0.0)
   - Set build number (1, 2, 3...)

2. **Create Release Commit**
   ```bash
   git commit -m "Release: Version 1.0.0 (Build 1) for TestFlight"
   git tag -a v1.0.0-b1 -m "Beta build 1"
   git push origin main --tags
   ```

3. **Verify Build Settings**
   ```bash
   cd iosApp
   
   # List signing identities
   security find-identity -v -p codesigning
   
   # Verify provisioning profiles
   ls ~/Library/MobileDevice/Provisioning\ Profiles/
   ```

### Phase 2: Build Archive

**Manual Build in Xcode:**
1. Select "Any iOS Device (arm64)"
2. Product → Archive
3. Organizer window opens automatically
4. Select latest archive
5. Click "Distribute App"

**Command Line Build:**
```bash
cd iosApp

# Build archive
xcodebuild archive \
  -scheme iosApp \
  -configuration Release \
  -archivePath "./build/iosApp.xcarchive" \
  -derivedDataPath "./build/DerivedData" \
  -allowProvisioningUpdates

# Verify archive
xcodebuild -exportArchive \
  -archivePath "./build/iosApp.xcarchive" \
  -exportPath "./build/export" \
  -exportOptionsPlist "./exportOptions.plist"
```

**exportOptions.plist Configuration:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key>
    <string>app-store</string>
    <key>signingStyle</key>
    <string>automatic</string>
    <key>stripSwiftSymbols</key>
    <true/>
    <key>teamID</key>
    <string>YOUR_TEAM_ID</string>
    <key>thinning</key>
    <string>&lt;none&gt;</string>
</dict>
</plist>
```

### Phase 3: TestFlight Beta Testing

**Upload to TestFlight:**
1. Xcode Organizer → Select Archive → Distribute → TestFlight Beta Review
2. Provide beta review information:
   - Test account credentials
   - Demo video link (optional)
   - Beta notes describing changes

**Invite Testers:**
1. App Store Connect → TestFlight → Testers
2. Create Internal Test Group
3. Add developer & QA team emails
4. Build automatically notified when app ready

**Monitoring Beta:**
- Check crash reports in Xcode Organizer
- Review feedback from testers
- Monitor analytics in App Store Connect
- Fix issues and increment build number for next submission

### Phase 4: App Store Submission

**App Store Connect Setup:**

1. **App Information**
   - App Name: SawaariShare
   - Subtitle: US Desi Student Carpools
   - Primary Language: English
   - Privacy Policy: [Link to privacy policy]
   - Contact Email: [support email]

2. **Pricing & Availability**
   - Free app, no in-app purchases
   - Available in all markets (or select regions)
   - Age Rating: Set through questionnaire
   - Export Compliance: Not encryption-restricted

3. **Screenshots (Required)**
   - 2 sets minimum (iPhone 6.7", iPhone 5.5")
   - Each set: 2-10 screenshots
   - 1242×2688 pixels (max file size: 5MB)
   - Already have iPhone mockups in design files

4. **Preview Video (Optional)**
   - Max 30 seconds
   - Showcases key features
   - 1920×1080 or 1080×1920

5. **Description**
```
SawaariShare connects US Desi students to safe, affordable carpools.

Features:
• Post and find rides with other students
• Real-time ride matching and messaging
• Trusted community with verified profiles
• Affordable shared transportation
• Schedule rides in advance or find instant rides

Perfect for commuting to campus, airport runs, or road trips!
```

6. **Keywords** (5 fields)
- Carpool, Rideshare, Student Transport
- Travel, Commute
- Community, Safe, Affordable

7. **Support URL & Privacy Policy**
- Support: https://sawaariapp.com/support
- Privacy: https://sawaariapp.com/privacy

**Submit for Review:**
1. Build version selected
2. Review all sections complete
3. Save as draft or submit for review
4. Review typically takes 24-48 hours
5. Status updates via email and App Store Connect

### Phase 5: Post-Release

**After Approval:**
1. Release immediately or schedule release date
2. Monitor crash reports and reviews
3. Respond to user reviews
4. Track usage analytics

**Version Updates:**
1. Increment build number for each release
2. Update release notes for new features
3. Follow same process (Archive → TestFlight → App Store)

## GitHub Actions Workflow

**File:** `.github/workflows/ios-release.yml`

```yaml
name: iOS App Release Pipeline

on:
  workflow_dispatch:
    inputs:
      release_type:
        description: 'Release type'
        required: true
        default: 'beta'
        type: choice
        options:
          - beta
          - production

env:
  DEVELOPER_DIR: /Applications/Xcode.app/contents/developer

jobs:
  build-and-release:
    runs-on: macos-14
    steps:
      - uses: actions/checkout@v4

      - name: Setup Xcode
        run: sudo xcode-select -s /Applications/Xcode.app/Contents/Developer

      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build Shared Framework
        run: |
          ./gradlew :shared:linkReleaseFrameworkIosFat -x test --stacktrace

      - name: Install Dependencies
        run: |
          cd iosApp
          pod repo update
          pod install

      - name: Create Archive
        run: |
          cd iosApp
          xcodebuild archive \
            -scheme iosApp \
            -configuration Release \
            -archivePath "./build/iosApp.xcarchive" \
            -derivedDataPath "./build/DerivedData" \
            -allowProvisioningUpdates

      - name: Export App
        run: |
          cd iosApp
          xcodebuild -exportArchive \
            -archivePath "./build/iosApp.xcarchive" \
            -exportPath "./build/export" \
            -exportOptionsPlist "./exportOptions.plist"

      - name: Upload to TestFlight
        if: github.event.inputs.release_type == 'beta'
        run: |
          xcrun altool --upload-app \
            -f "iosApp/build/export/iosApp.ipa" \
            -t ios \
            -u "${{ secrets.APPSTORE_USERNAME }}" \
            -p "${{ secrets.APPSTORE_PASSWORD }}"

      - name: Submit to App Store
        if: github.event.inputs.release_type == 'production'
        run: |
          xcrun altool --upload-app \
            -f "iosApp/build/export/iosApp.ipa" \
            -t ios \
            -u "${{ secrets.APPSTORE_USERNAME }}" \
            -p "${{ secrets.APPSTORE_PASSWORD }}"

      - name: Upload Build Artifacts
        uses: actions/upload-artifact@v4
        with:
          name: ios-release-build
          path: iosApp/build/export/iosApp.ipa

  notify-release:
    needs: build-and-release
    runs-on: ubuntu-latest
    steps:
      - name: Notify Slack
        run: |
          # TODO: Send Slack notification with release status
```

**GitHub Secrets Required:**
- `APPSTORE_USERNAME` - Apple ID email
- `APPSTORE_PASSWORD` - App-specific password (not regular password!)
  - Generate at [appleid.apple.com/account/security](https://appleid.apple.com/account/security)
- `DEVELOPER_TEAM_ID` - Team ID from Apple Developer
- `CODESIGN_CERTIFICATE` - Base64-encoded distribution certificate
- `CODESIGN_CERTIFICATE_PASSWORD` - Certificate password

## Troubleshooting

### Common Build Issues

**"No provisioning profile matches"**
- Verify bundle ID matches profile
- Update provisioning profile in Xcode
- Refresh signing credentials in Xcode preferences

**"Code Signing Error: No codesigningidentity found"**
```bash
# List available identities
security find-identity -v -p codesigning

# Install missing certificate
# Xcode → Preferences → Accounts → Download Manual Profiles
```

**"App contains unsigned code"**
- Ensure all dependencies are signed
- Check that Shared.framework is properly linked
- Clean build: `Cmd+Shift+K`, rebuild

**"Build failed with symbols not found"**
- Rebuild Shared.framework with matching architectures
- Verify iOS deployment target matches shared library
- Check Framework Search Paths in build settings

### TestFlight Issues

**"Binary rejected: Cryptography issues"**
- Ensure export compliance is correctly set
- If using encryption, provide proper export documentation

**"Binary not yet processed"**
- Wait for automatic processing (usually 10-30 minutes)
- Check for warnings in build logs

### App Store Review Rejection

**Common Rejection Reasons:**
1. "Guideline 2.1 - Information Needed"
   - Provide clear explanation of app functionality
   - Include test account if login required

2. "Guideline 4.3 - Spam"
   - Ensure app provides real value
   - Remove duplicate or misleading content

3. "Guideline 5.1 - Legal"
   - Ensure all required legal agreements present
   - Verify permissions usage justified

**Response Strategy:**
- Read full rejection details carefully
- Address specific issues mentioned
- Resubmit within 30 days with changes
- Include detailed notes explaining fixes

## Monitoring & Maintenance

### Post-Launch Analytics
- **Installs & Uninstalls:** App Store Connect dashboard
- **Crash Reports:** Xcode Organizer → Crashes
- **Performance Metrics:** App Store Connect → Metrics
- **User Reviews:** App Store Connect → Reviews

### Version Support Policy
- Support current version + 1 previous version
- Security fixes: Backport to supported versions
- Feature updates: Current version only
- Deprecation notice: 30 days notice before dropping support

### Update Cycle
- **Security patches:** Within 24 hours of discovery
- **Bug fixes:** Weekly builds if issues reported
- **Feature updates:** Monthly or quarterly releases
- **Major versions:** Semi-annual or annually

## Checklist: Release to Production

- [ ] Version numbers updated (marketing + build)
- [ ] Release notes written
- [ ] Screenshots and preview video ready
- [ ] Privacy policy and terms of service finalized
- [ ] App Store Connect listing complete
- [ ] Build created and archived
- [ ] TestFlight beta tested (minimum 1 week)
- [ ] No critical crash reports
- [ ] All requested app info provided
- [ ] Age rating questionnaire completed
- [ ] Export compliance set correctly
- [ ] Pricing and availability configured
- [ ] Support contact information verified
- [ ] App submitted for review
- [ ] Release date scheduled or approved manually
- [ ] Monitoring dashboards set up

## Resources

- [App Store Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)
- [App Store Connect Help](https://help.apple.com/app-store-connect/)
- [Xcode Help: Distributing Your App](https://help.apple.com/xcode/mac/current/#/devbe4f38925)
- [Apple's Code Signing Guide](https://developer.apple.com/support/code-signing/)
- [TestFlight Beta Testing Guide](https://developer.apple.com/testflight/)

## Version History Template

```markdown
## Version 1.0.0 - Initial Release
- ✅ User authentication (phone + password)
- ✅ Browse and request rides
- ✅ Real-time ride matching
- ✅ In-app messaging
- ✅ User profiles & ratings

## Version 1.0.1 - Bug Fixes
- Fixed crash when loading large ride lists
- Improved message loading performance
- Corrected time display for international users

## Version 1.1.0 - Enhanced Features
- Added ride filters (date, price, route)
- New notification system
- Improved search & discovery
- Integration with Apple Pay
```
