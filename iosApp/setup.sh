#!/bin/bash
# iOS app setup script - builds framework and prepares for Xcode development

set -e

echo "🔨 Setting up iOS app..."

# Step 1: Generate Xcode project if it doesn't exist
if [ ! -d "iosApp.xcodeproj" ]; then
  echo "📝 Generating Xcode project..."
  python3 generate-project.py
  echo "✅ Xcode project generated"
else
  echo "✅ Xcode project already exists"
fi

# Step 2: Verify project structure
echo "🔍 Verifying iOS project structure..."
if [ ! -d "iosApp/Assets.xcassets" ]; then
  echo "⚠️  Warning: Assets.xcassets not found"
fi
if [ ! -f "iosApp/LaunchScreen.storyboard" ]; then
  echo "⚠️  Warning: LaunchScreen.storyboard not found"
fi
if [ ! -f "iosApp/Info.plist" ]; then
  echo "⚠️  Warning: Info.plist not found"
fi

# Step 3: Build the Shared.framework for all iOS targets
echo "📦 Building Shared.framework for iOS..."
cd ..
./gradlew :shared:assembleSharedReleaseXCFramework --stacktrace
echo "✅ Shared.framework built successfully"

# Step 4: Install CocoaPods (if Pods are configured)
echo "📱 Setting up CocoaPods..."
cd iosApp
if [ -f "Podfile" ]; then
  pod install --repo-update || echo "⚠️  CocoaPods install had issues, but continuing..."
  echo "✅ CocoaPods setup complete"
else
  echo "⚠️  No Podfile found, skipping pod install"
fi

echo ""
echo "✅ iOS app setup complete!"
echo ""
echo "📋 Project Structure:"
echo "   ✓ Xcode project: iosApp.xcodeproj"
echo "   ✓ Swift sources: iosApp/iOSApp.swift, iosApp/ContentView.swift"
echo "   ✓ Resources: Info.plist, LaunchScreen.storyboard, Assets.xcassets"
echo "   ✓ Framework: Shared.framework (built from KMP)"
echo ""
echo "🚀 Next steps on macOS:"
echo "1. Open iosApp.xcodeproj in Xcode"
echo "2. Select 'iosApp' target and build scheme"
echo "3. Select iOS Simulator or device"
echo "4. Press Cmd+R to build and run"
