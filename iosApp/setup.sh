#!/bin/bash
# iOS app setup script - creates Xcode project, builds framework, and installs pods

set -e

echo "🔨 Setting up iOS app..."

# Step 1: Create Xcode project if it doesn't exist
if [ ! -d "iosApp.xcodeproj" ]; then
  echo "📝 Creating Xcode project structure..."
  ./create-xcode-project.sh
else
  echo "✅ Xcode project already exists"
fi

# Step 2: Build the Shared.framework for all iOS targets
echo "📦 Building Shared.framework..."
cd ..
./gradlew :shared:assembleSharedXCFramework -x test

# Step 3: Install CocoaPods dependencies
echo "📱 Installing CocoaPods dependencies..."
cd iosApp
pod install --repo-update

echo "✅ iOS app setup complete!"
echo ""
echo "Next steps:"
echo "1. Open iosApp.xcworkspace in Xcode (NOT iosApp.xcodeproj)"
echo "2. Select 'iosApp' scheme and desired simulator"
echo "3. Press Cmd+R to build and run"
