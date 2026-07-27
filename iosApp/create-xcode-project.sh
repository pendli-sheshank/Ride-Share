#!/bin/bash
# Creates Xcode project structure for SawaariShare iOS app
# This script generates a valid .xcodeproj that can be opened in Xcode

set -e

PROJ_NAME="iosApp"
PROJ_PATH="."
BUNDLE_ID="com.splitcruiser.app"
TEAM_ID="${TEAM_ID:-}"  # Optional: set via environment variable

echo "🔨 Creating Xcode project for $PROJ_NAME..."

# Create project directory structure
mkdir -p "$PROJ_PATH/$PROJ_NAME.xcodeproj"
mkdir -p "$PROJ_PATH/$PROJ_NAME.xcodeproj/xcuserdata"
mkdir -p "$PROJ_PATH/$PROJ_NAME.xcodeproj/project.xcworkspace/xcuserdata"

# Create minimal project.pbxproj (Xcode project file)
# This is a basic structure that Xcode can open and modify
cat > "$PROJ_PATH/$PROJ_NAME.xcodeproj/project.pbxproj" << 'PBXEOF'
// !$*UTF8*$!
{
	archiveVersion = 1;
	classes = {
	};
	objectVersion = 56;
	objects = {
/* Begin PBXBuildFile section */
		2F1234561A1A1A1A001A1A1A /* iOSApp.swift in Sources */ = {isa = PBXBuildFile; fileRef = 2F1234551A1A1A1A001A1A1A /* iOSApp.swift */; };
		2F1234581A1A1A1A001A1A1A /* ContentView.swift in Sources */ = {isa = PBXBuildFile; fileRef = 2F1234571A1A1A1A001A1A1A /* ContentView.swift */; };
		2F123459A1A1A1A1A001A1A1A /* Shared.framework in Frameworks */ = {isa = PBXBuildFile; fileRef = 2F12345BA1A1A1A1A001A1A1A /* Shared.framework */; };
/* End PBXBuildFile section */

/* Begin PBXFileReference section */
		2F1234501A1A1A1A001A1A1A /* iosApp.app */ = {isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = iosApp.app; sourceTree = BUILT_PRODUCTS_DIR; };
		2F1234551A1A1A1A001A1A1A /* iOSApp.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = iOSApp.swift; sourceTree = "<group>"; };
		2F1234571A1A1A1A001A1A1A /* ContentView.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = ContentView.swift; sourceTree = "<group>"; };
		2F1234591A1A1A1A001A1A1A /* Info.plist */ = {isa = PBXFileReference; lastKnownFileType = text.plist.xml; path = Info.plist; sourceTree = "<group>"; };
		2F12345BA1A1A1A1A001A1A1A /* Shared.framework */ = {isa = PBXFileReference; explicitFileType = wrapper.framework; path = ../shared/build/XCFrameworks/Shared.xcframework; sourceTree = "<group>"; };
/* End PBXFileReference section */

/* Begin PBXFrameworksBuildPhase section */
		2F123451A1A1A1A1A001A1A1A /* Frameworks */ = {
			isa = PBXFrameworksBuildPhase;
			buildActionMask = 2147483647;
			files = (
				2F123459A1A1A1A1A001A1A1A /* Shared.framework in Frameworks */,
			);
			runOnlyForDeploymentPostprocessing = 0;
		};
/* End PBXFrameworksBuildPhase section */

/* Begin PBXGroup section */
		2F1234471A1A1A1A001A1A1A = {
			isa = PBXGroup;
			children = (
				2F1234521A1A1A1A001A1A1A /* iosApp */,
				2F1234511A1A1A1A001A1A1A /* Products */,
				2F12345AA1A1A1A1A001A1A1A /* Frameworks */,
			);
			sourceTree = "<group>";
		};
		2F1234511A1A1A1A001A1A1A /* Products */ = {
			isa = PBXGroup;
			children = (
				2F1234501A1A1A1A001A1A1A /* iosApp.app */,
			);
			name = Products;
			sourceTree = "<group>";
		};
		2F1234521A1A1A1A001A1A1A /* iosApp */ = {
			isa = PBXGroup;
			children = (
				2F1234551A1A1A1A001A1A1A /* iOSApp.swift */,
				2F1234571A1A1A1A001A1A1A /* ContentView.swift */,
				2F1234591A1A1A1A001A1A1A /* Info.plist */,
			);
			path = iosApp;
			sourceTree = "<group>";
		};
		2F12345AA1A1A1A1A001A1A1A /* Frameworks */ = {
			isa = PBXGroup;
			children = (
				2F12345BA1A1A1A1A001A1A1A /* Shared.framework */,
			);
			name = Frameworks;
			sourceTree = "<group>";
		};
/* End PBXGroup section */

/* Begin PBXNativeTarget section */
		2F123450A1A1A1A1A001A1A1A /* iosApp */ = {
			isa = PBXNativeTarget;
			buildConfigurationList = 2F123460A1A1A1A1A001A1A1A /* Build configuration list for PBXNativeTarget "iosApp" */;
			buildPhases = (
				2F123451A1A1A1A1A001A1A1A /* Frameworks */,
				2F123453A1A1A1A1A001A1A1A /* Sources */,
			);
			buildRules = (
			);
			dependencies = (
			);
			name = iosApp;
			productName = iosApp;
			productReference = 2F1234501A1A1A1A001A1A1A /* iosApp.app */;
			productType = "com.apple.product-type.application";
		};
/* End PBXNativeTarget section */

/* Begin PBXProject section */
		2F1234471A1A1A1A001A1A1A /* Project object */ = {
			isa = PBXProject;
			attributes = {
				BuildIndependentTargetsInParallel = 1;
				LastSwiftUpdateCheck = 1500;
				LastUpgradeCheck = 1500;
				TargetAttributes = {
					2F123450A1A1A1A1A001A1A1A = {
						CreatedOnToolsVersion = 15.0;
					};
				};
			};
			buildConfigurationList = 2F123460A1A1A1A1A001A1A1A /* Build configuration list for PBXProject "iosApp" */;
			compatibilityVersion = "Xcode 14.0";
			developmentRegion = en;
			hasScannedForEncodings = 0;
			knownRegions = (
				en,
				Base,
			);
			mainGroup = 2F1234471A1A1A1A001A1A1A;
			productRefGroup = 2F1234511A1A1A1A001A1A1A /* Products */;
			projectDirPath = "";
			projectRoot = "";
			targets = (
				2F123450A1A1A1A1A001A1A1A /* iosApp */,
			);
		};
/* End PBXProject section */

/* Begin PBXSourcesBuildPhase section */
		2F123453A1A1A1A1A001A1A1A /* Sources */ = {
			isa = PBXSourcesBuildPhase;
			buildActionMask = 2147483647;
			files = (
				2F1234561A1A1A1A001A1A1A /* iOSApp.swift in Sources */,
				2F1234581A1A1A1A001A1A1A /* ContentView.swift in Sources */,
			);
			runOnlyForDeploymentPostprocessing = 0;
		};
/* End PBXSourcesBuildPhase section */

/* Begin XCBuildConfiguration section */
		2F123461A1A1A1A1A001A1A1A /* Debug */ = {
			isa = XCBuildConfiguration;
			buildSettings = {
				ALWAYS_SEARCH_USER_PATHS = NO;
				CLANG_ANALYZER_NONNULL = YES;
				CLANG_ANALYZER_NUMBER_OBJECT_CONVERSION = YES_AGGRESSIVE;
				CLANG_CXX_LANGUAGE_DIALECT = "c++17";
				CLANG_CXX_LIBRARY = "libc++";
				CLANG_ENABLE_MODULES = YES;
				CLANG_ENABLE_OBJC_ARC = YES;
				CLANG_WARN_BLOCK_CAPTURE_AUTORELEASING = YES;
				CLANG_WARN_BOOL_CONVERSION = YES;
				CLANG_WARN_COMMA = YES;
				CLANG_WARN_CONSTANT_CONVERSION = YES;
				CLANG_WARN_DEPRECATED_OBJC_IMPLEMENTATIONS = YES;
				CLANG_WARN_DIRECT_OBJC_ISA_USAGE = YES_ERROR;
				CLANG_WARN_DOCUMENTATION_COMMENTS = YES;
				CLANG_WARN_EMPTY_BODY = YES;
				CLANG_WARN_ENUM_CONVERSION = YES;
				CLANG_WARN_INFINITE_RECURSION = YES;
				CLANG_WARN_INT_CONVERSION = YES;
				CLANG_WARN_NON_LITERAL_NULL_CONVERSION = YES;
				CLANG_WARN_OBJC_IMPLICIT_RETAIN_SELF = YES;
				CLANG_WARN_OBJC_LITERAL_CONVERSION = YES;
				CLANG_WARN_OBJC_ROOT_CLASS = YES_ERROR;
				CLANG_WARN_QUOTED_INCLUDE_IN_FRAMEWORK_HEADER = YES;
				CLANG_WARN_RANGE_LOOP_ANALYSIS = YES;
				CLANG_WARN_STRICT_PROTOTYPES = YES;
				CLANG_WARN_SUSPICIOUS_MOVE = YES;
				CLANG_WARN_SUSPICIOUS_MOVES = YES;
				CLANG_WARN_UNREACHABLE_CODE = YES;
				CLANG_WARN__DUPLICATE_METHOD_MATCH = YES;
				CODE_SIGN_IDENTITY = "iPhone Developer";
				COPY_PHASE_STRIP = NO;
				DEBUG_INFORMATION_FORMAT = dwarf;
				ENABLE_STRICT_OBJC_MSGSEND = YES;
				ENABLE_TESTABILITY = YES;
				GCC_C_LANGUAGE_DIALECT = c99;
				GCC_DYNAMIC_NO_PIC = NO;
				GCC_NO_COMMON_BLOCKS = YES;
				GCC_OPTIMIZATION_LEVEL = 0;
				GCC_PREPROCESSOR_DEFINITIONS = (
					"DEBUG=1",
					"$(inherited)",
				);
				GCC_WARN_64_TO_32_BIT_CONVERSION = YES;
				GCC_WARN_ABOUT_RETURN_TYPE = YES_ERROR;
				GCC_WARN_UNDECLARED_SELECTOR = YES;
				GCC_WARN_UNINITIALIZED_AUTOS = YES_AGGRESSIVE;
				GCC_WARN_UNUSED_FUNCTION = YES;
				GCC_WARN_UNUSED_VARIABLE = YES;
				IPHONEOS_DEPLOYMENT_TARGET = 14.0;
				MTL_ENABLE_DEBUG_INFO = INCLUDE_SOURCE;
				MTL_FAST_MATH = YES;
				ONLY_ACTIVE_ARCH = YES;
				SDKROOT = iphoneos;
				SWIFT_ACTIVE_COMPILATION_CONDITIONS = DEBUG;
				SWIFT_OPTIMIZATION_LEVEL = "-Onone";
				SWIFT_VERSION = 5.0;
			};
			name = Debug;
		};
		2F123462A1A1A1A1A001A1A1A /* Release */ = {
			isa = XCBuildConfiguration;
			buildSettings = {
				ALWAYS_SEARCH_USER_PATHS = NO;
				CLANG_ANALYZER_NONNULL = YES;
				CLANG_ANALYZER_NUMBER_OBJECT_CONVERSION = YES_AGGRESSIVE;
				CLANG_CXX_LANGUAGE_DIALECT = "c++17";
				CLANG_CXX_LIBRARY = "libc++";
				CLANG_ENABLE_MODULES = YES;
				CLANG_ENABLE_OBJC_ARC = YES;
				CLANG_WARN_BLOCK_CAPTURE_AUTORELEASING = YES;
				CLANG_WARN_BOOL_CONVERSION = YES;
				CLANG_WARN_COMMA = YES;
				CLANG_WARN_CONSTANT_CONVERSION = YES;
				CLANG_WARN_DEPRECATED_OBJC_IMPLEMENTATIONS = YES;
				CLANG_WARN_DIRECT_OBJC_ISA_USAGE = YES_ERROR;
				CLANG_WARN_DOCUMENTATION_COMMENTS = YES;
				CLANG_WARN_EMPTY_BODY = YES;
				CLANG_WARN_ENUM_CONVERSION = YES;
				CLANG_WARN_INFINITE_RECURSION = YES;
				CLANG_WARN_INT_CONVERSION = YES;
				CLANG_WARN_NON_LITERAL_NULL_CONVERSION = YES;
				CLANG_WARN_OBJC_IMPLICIT_RETAIN_SELF = YES;
				CLANG_WARN_OBJC_LITERAL_CONVERSION = YES;
				CLANG_WARN_OBJC_ROOT_CLASS = YES_ERROR;
				CLANG_WARN_QUOTED_INCLUDE_IN_FRAMEWORK_HEADER = YES;
				CLANG_WARN_RANGE_LOOP_ANALYSIS = YES;
				CLANG_WARN_STRICT_PROTOTYPES = YES;
				CLANG_WARN_SUSPICIOUS_MOVE = YES;
				CLANG_WARN_SUSPICIOUS_MOVES = YES;
				CLANG_WARN_UNREACHABLE_CODE = YES;
				CLANG_WARN__DUPLICATE_METHOD_MATCH = YES;
				CODE_SIGN_IDENTITY = "iPhone Developer";
				COPY_PHASE_STRIP = NO;
				DEBUG_INFORMATION_FORMAT = "dwarf-with-dsym";
				ENABLE_NS_ASSERTIONS = NO;
				ENABLE_STRICT_OBJC_MSGSEND = YES;
				GCC_C_LANGUAGE_DIALECT = c99;
				GCC_NO_COMMON_BLOCKS = YES;
				GCC_OPTIMIZE_FOR_SIZE = YES;
				GCC_WARN_64_TO_32_BIT_CONVERSION = YES;
				GCC_WARN_ABOUT_RETURN_TYPE = YES_ERROR;
				GCC_WARN_UNDECLARED_SELECTOR = YES;
				GCC_WARN_UNINITIALIZED_AUTOS = YES_AGGRESSIVE;
				GCC_WARN_UNUSED_FUNCTION = YES;
				GCC_WARN_UNUSED_VARIABLE = YES;
				IPHONEOS_DEPLOYMENT_TARGET = 14.0;
				MTL_ENABLE_DEBUG_INFO = NO;
				MTL_FAST_MATH = YES;
				SDKROOT = iphoneos;
				SWIFT_COMPILATION_MODE = wholemodule;
				SWIFT_OPTIMIZATION_LEVEL = "-O";
				SWIFT_VERSION = 5.0;
				VALIDATE_PRODUCT = YES;
			};
			name = Release;
		};
		2F123463A1A1A1A1A001A1A1A /* Debug */ = {
			isa = XCBuildConfiguration;
			buildSettings = {
				ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
				ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME = AccentColor;
				CODE_SIGN_STYLE = Automatic;
				CURRENT_PROJECT_VERSION = 1;
				DEVELOPMENT_TEAM = "";
				EXECUTABLE_NAME = iosApp;
				GENERATE_INFOPLIST_FILE = YES;
				INFOPLIST_FILE = iosApp/Info.plist;
				INFOPLIST_KEY_UIApplicationSceneManifest_Generation = YES;
				INFOPLIST_KEY_UIApplicationSupportsIndirectInputEvents = YES;
				IPHONEOS_DEPLOYMENT_TARGET = 14.0;
				LD_RUNPATH_SEARCH_PATHS = (
					"$(inherited)",
					"@executable_path/Frameworks",
				);
				MARKETING_VERSION = 1.0;
				PRODUCT_BUNDLE_IDENTIFIER = com.splitcruiser.app;
				PRODUCT_NAME = "$(TARGET_NAME)";
				SWIFT_EMIT_LOC_STRINGS = YES;
				SWIFT_VERSION = 5.0;
				TARGETED_DEVICE_FAMILY = "1,2";
			};
			name = Debug;
		};
		2F123464A1A1A1A1A001A1A1A /* Release */ = {
			isa = XCBuildConfiguration;
			buildSettings = {
				ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
				ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME = AccentColor;
				CODE_SIGN_STYLE = Automatic;
				CURRENT_PROJECT_VERSION = 1;
				DEVELOPMENT_TEAM = "";
				EXECUTABLE_NAME = iosApp;
				GENERATE_INFOPLIST_FILE = YES;
				INFOPLIST_FILE = iosApp/Info.plist;
				INFOPLIST_KEY_UIApplicationSceneManifest_Generation = YES;
				INFOPLIST_KEY_UIApplicationSupportsIndirectInputEvents = YES;
				IPHONEOS_DEPLOYMENT_TARGET = 14.0;
				LD_RUNPATH_SEARCH_PATHS = (
					"$(inherited)",
					"@executable_path/Frameworks",
				);
				MARKETING_VERSION = 1.0;
				PRODUCT_BUNDLE_IDENTIFIER = com.splitcruiser.app;
				PRODUCT_NAME = "$(TARGET_NAME)";
				SWIFT_EMIT_LOC_STRINGS = YES;
				SWIFT_VERSION = 5.0;
				TARGETED_DEVICE_FAMILY = "1,2";
			};
			name = Release;
		};
/* End XCBuildConfiguration section */

/* Begin XCConfigurationList section */
		2F1234601A1A1A1A1A001A1A1A /* Build configuration list for PBXProject "iosApp" */ = {
			isa = XCConfigurationList;
			buildConfigurations = (
				2F123461A1A1A1A1A001A1A1A /* Debug */,
				2F123462A1A1A1A1A001A1A1A /* Release */,
			);
			defaultConfigurationIsVisible = 0;
			defaultConfigurationName = Release;
		};
		2F1234631A1A1A1A1A001A1A1A /* Build configuration list for PBXNativeTarget "iosApp" */ = {
			isa = XCConfigurationList;
			buildConfigurations = (
				2F123463A1A1A1A1A001A1A1A /* Debug */,
				2F123464A1A1A1A1A001A1A1A /* Release */,
			);
			defaultConfigurationIsVisible = 0;
			defaultConfigurationName = Release;
		};
/* End XCConfigurationList section */
	};
	rootObject = 2F1234471A1A1A1A001A1A1A /* Project object */;
}
PBXEOF

# Create workspace structure
cat > "$PROJ_PATH/$PROJ_NAME.xcodeproj/project.xcworkspace/contents.xcworkspacedata" << 'WSEOF'
<?xml version="1.0" encoding="UTF-8"?>
<Workspace version = "1.0">
   <FileRef location = "group:">
   </FileRef>
</Workspace>
WSEOF

echo "✅ Xcode project structure created successfully!"
echo ""
echo "📁 Project files:"
ls -la "$PROJ_PATH/$PROJ_NAME.xcodeproj/"
echo ""
echo "Next steps:"
echo "1. Run: pod install"
echo "2. Open: iosApp.xcworkspace in Xcode"
echo "3. Build & Run!"
