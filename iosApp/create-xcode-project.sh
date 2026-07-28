#!/bin/bash
# Creates Xcode project structure for Split Cruiser iOS app
# This script delegates to the Python generator for a valid, maintainable .xcodeproj

set -e

echo "🔨 Creating Xcode project for iosApp..."

# Use Python generator for better reliability and maintainability
python3 generate-project.py

echo "✅ Xcode project structure created successfully!"
