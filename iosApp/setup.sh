#!/bin/bash
# iOS app setup script - runs CocoaPods setup and builds Shared.framework

set -e

echo "🔨 Setting up iOS app..."

# Build the Shared.framework for all iOS targets
echo "📦 Building Shared.framework..."
cd ..
./gradlew :shared:assembleSharedXCFramework

echo "📱 Installing CocoaPods dependencies..."
cd iosApp
pod install --repo-update

echo "✅ iOS app setup complete!"
echo ""
echo "Next steps:"
echo "1. Open iosApp.xcworkspace in Xcode (NOT iosApp.xcodeproj)"
echo "2. Select 'iosApp' scheme and desired simulator"
echo "3. Press Cmd+R to build and run"
