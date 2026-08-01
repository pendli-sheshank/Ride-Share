#!/usr/bin/env python3
"""
Generate a valid iOS Xcode project structure following Apple standards.
This creates a proper pbxproj file with correct build phases and configurations.
"""

import os
import json
import uuid
from pathlib import Path


# Deterministic on purpose. uuid4() meant every run rewrote every ID, so the checked-in
# project.pbxproj could never be diffed against a fresh generation to confirm it was still
# correct — which is how 12 dangling references went unnoticed until a release build.
_ID_NAMESPACE = uuid.UUID("6f1a9c3e-58d2-4a7b-9e14-2c8d5b0f7a63")


def generate_id(name, length=24):
    """Stable identifier for a pbxproj object, derived from its role."""
    return uuid.uuid5(_ID_NAMESPACE, name).hex[:length].upper()


# Every Swift file the app target compiles, in compile order. Adding a file here is the whole
# change: the PBXBuildFile, PBXFileReference, group child and Sources build-phase entries are all
# derived from this list. They used to be written out by hand in four places, which is how
# ViewModel.swift ended up on disk, defining AppViewModel, and never compiled.
SWIFT_SOURCES = [
    "iOSApp.swift",
    "Theme.swift",
    "ContentView.swift",
    "RideDetailView.swift",
    "ChatView.swift",
    "ViewModel.swift",
    "EditProfileView.swift",
    "BlockedListView.swift",
    "HostDashboardView.swift",
]


def _swift_key(filename):
    """The `ids` key for a Swift source, e.g. "ContentView.swift" -> "contentview_swift"."""
    return filename.replace(".swift", "").lower() + "_swift"


def create_xcode_project():
    """Create a complete iOS Xcode project structure."""

    base_path = Path(".")
    proj_name = "iosApp"
    bundle_id = "com.splitcruiser.app"

    # Create .xcodeproj structure
    xcodeproj_path = base_path / f"{proj_name}.xcodeproj"
    xcodeproj_path.mkdir(exist_ok=True)

    (xcodeproj_path / "xcuserdata").mkdir(exist_ok=True)
    (xcodeproj_path / "project.xcworkspace").mkdir(exist_ok=True)
    (xcodeproj_path / "project.xcworkspace" / "xcuserdata").mkdir(exist_ok=True)

    # Generate unique identifiers for all objects
    ids = {
        "project": generate_id("project"),
        "products_group": generate_id("products_group"),
        "main_group": generate_id("main_group"),
        "iosapp_group": generate_id("iosapp_group"),
        "resources_group": generate_id("resources_group"),
        "frameworks_group": generate_id("frameworks_group"),
        "native_target": generate_id("native_target"),
        "frameworks_build_phase": generate_id("frameworks_build_phase"),
        "sources_build_phase": generate_id("sources_build_phase"),
        "resources_build_phase": generate_id("resources_build_phase"),
        "info_plist": generate_id("info_plist"),
        "assets": generate_id("assets"),
        "launchscreen": generate_id("launchscreen"),
        "shared_framework_ref": generate_id("shared_framework_ref"),
        "shared_framework_build": generate_id("shared_framework_build"),
        "app_product": generate_id("app_product"),
        "debug_config": generate_id("debug_config"),
        "release_config": generate_id("release_config"),
        "project_debug": generate_id("project_debug"),
        "project_release": generate_id("project_release"),
        # PBXFileReference ids, distinct from the PBXBuildFile ids above.
        "info_plist_ref": generate_id("info_plist_ref"),
        "assets_ref": generate_id("assets_ref"),
        "launchscreen_ref": generate_id("launchscreen_ref"),
        # Referenced by PBXNativeTarget/PBXProject and defined in XCConfigurationList. These
        # were emitted inline at four call sites, so reference and definition never matched;
        # that is exactly what "The project contains no build configurations" means.
        "project_config_list": generate_id("project_config_list"),
        "target_config_list": generate_id("target_config_list"),
    }

    # Two ids per Swift source: the PBXBuildFile and the PBXFileReference it points at.
    for source in SWIFT_SOURCES:
        key = _swift_key(source)
        ids[key] = generate_id(key)
        ids[f"{key}_ref"] = generate_id(f"{key}_ref")

    def swift_lines(template, indent="\t\t"):
        return "\n".join(
            indent + template.format(
                build=ids[_swift_key(name)],
                ref=ids[f"{_swift_key(name)}_ref"],
                name=name,
            )
            for name in SWIFT_SOURCES
        )

    swift_build_files = swift_lines(
        "{build} /* {name} in Sources */ = {{isa = PBXBuildFile; fileRef = {ref} /* {name} */; }};"
    )
    swift_file_refs = swift_lines(
        "{ref} /* {name} */ = {{isa = PBXFileReference; lastKnownFileType = sourcecode.swift; "
        "path = {name}; sourceTree = \"<group>\"; }};"
    )
    swift_group_children = swift_lines("{ref} /* {name} */,", indent="\t\t\t\t")
    swift_sources_phase = swift_lines("{build} /* {name} in Sources */,", indent="\t\t\t\t")

    pbxproj = f"""// !$*UTF8*$!
{{
	archiveVersion = 1;
	classes = {{
	}};
	objectVersion = 56;
	objects = {{
/* Begin PBXBuildFile section */
{swift_build_files}
		{ids['assets']} /* Assets.xcassets in Resources */ = {{isa = PBXBuildFile; fileRef = {ids['assets_ref']} /* Assets.xcassets */; }};
		{ids['launchscreen']} /* LaunchScreen.storyboard in Resources */ = {{isa = PBXBuildFile; fileRef = {ids['launchscreen_ref']} /* LaunchScreen.storyboard */; }};
		{ids['shared_framework_build']} /* Shared.xcframework in Frameworks */ = {{isa = PBXBuildFile; fileRef = {ids['shared_framework_ref']} /* Shared.xcframework */; }};
/* End PBXBuildFile section */

/* Begin PBXFileReference section */
		{ids['app_product']} /* iosApp.app */ = {{isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = iosApp.app; sourceTree = BUILT_PRODUCTS_DIR; }};
{swift_file_refs}
		{ids['info_plist_ref']} /* Info.plist */ = {{isa = PBXFileReference; lastKnownFileType = text.plist.xml; path = Info.plist; sourceTree = "<group>"; }};
		{ids['assets_ref']} /* Assets.xcassets */ = {{isa = PBXFileReference; lastKnownFileType = folder.assetcatalog; path = Assets.xcassets; sourceTree = "<group>"; }};
		{ids['launchscreen_ref']} /* LaunchScreen.storyboard */ = {{isa = PBXFileReference; lastKnownFileType = file.storyboard; path = LaunchScreen.storyboard; sourceTree = "<group>"; }};
		{ids['shared_framework_ref']} /* Shared.xcframework */ = {{isa = PBXFileReference; lastKnownFileType = wrapper.xcframework; path = ../shared/build/XCFrameworks/release/Shared.xcframework; sourceTree = SOURCE_ROOT; }};
/* End PBXFileReference section */

/* Begin PBXFrameworksBuildPhase section */
		{ids['frameworks_build_phase']} /* Frameworks */ = {{
			isa = PBXFrameworksBuildPhase;
			buildActionMask = 2147483647;
			files = (
				{ids['shared_framework_build']} /* Shared.xcframework in Frameworks */,
			);
			runOnlyForDeploymentPostprocessing = 0;
		}};
/* End PBXFrameworksBuildPhase section */

/* Begin PBXGroup section */
		{ids['main_group']} = {{
			isa = PBXGroup;
			children = (
				{ids['iosapp_group']} /* iosApp */,
				{ids['products_group']} /* Products */,
				{ids['frameworks_group']} /* Frameworks */,
			);
			sourceTree = "<group>";
		}};
		{ids['products_group']} /* Products */ = {{
			isa = PBXGroup;
			children = (
				{ids['app_product']} /* iosApp.app */,
			);
			name = Products;
			sourceTree = "<group>";
		}};
		{ids['iosapp_group']} /* iosApp */ = {{
			isa = PBXGroup;
			children = (
{swift_group_children}
				{ids['resources_group']} /* Resources */,
			);
			path = iosApp;
			sourceTree = "<group>";
		}};
		{ids['resources_group']} /* Resources */ = {{
			isa = PBXGroup;
			children = (
				{ids['assets_ref']} /* Assets.xcassets */,
				{ids['launchscreen_ref']} /* LaunchScreen.storyboard */,
				{ids['info_plist_ref']} /* Info.plist */,
			);
			name = Resources;
			sourceTree = "<group>";
		}};
		{ids['frameworks_group']} /* Frameworks */ = {{
			isa = PBXGroup;
			children = (
				{ids['shared_framework_ref']} /* Shared.xcframework */,
			);
			name = Frameworks;
			sourceTree = "<group>";
		}};
/* End PBXGroup section */

/* Begin PBXNativeTarget section */
		{ids['native_target']} /* iosApp */ = {{
			isa = PBXNativeTarget;
			buildConfigurationList = {ids['target_config_list']} /* Build Settings */;
			buildPhases = (
				{ids['sources_build_phase']} /* Sources */,
				{ids['frameworks_build_phase']} /* Frameworks */,
				{ids['resources_build_phase']} /* Resources */,
			);
			buildRules = (
			);
			dependencies = (
			);
			name = iosApp;
			productName = iosApp;
			productReference = {ids['app_product']} /* iosApp.app */;
			productType = "com.apple.product-type.application";
		}};
/* End PBXNativeTarget section */

/* Begin PBXProject section */
		{ids['project']} /* Project object */ = {{
			isa = PBXProject;
			attributes = {{
				BuildIndependentTargetsInParallel = 1;
				LastSwiftUpdateCheck = 1500;
				LastUpgradeCheck = 1500;
				TargetAttributes = {{
					{ids['native_target']} = {{
						CreatedOnToolsVersion = 15.0;
						DevelopmentTeam = "";
					}};
				}};
			}};
			buildConfigurationList = {ids['project_config_list']} /* Build Settings */;
			compatibilityVersion = "Xcode 14.0";
			developmentRegion = en;
			hasScannedForEncodings = 0;
			knownRegions = (
				en,
				Base,
			);
			mainGroup = {ids['main_group']};
			productRefGroup = {ids['products_group']} /* Products */;
			projectDirPath = "";
			projectRoot = "";
			targets = (
				{ids['native_target']} /* iosApp */,
			);
		}};
/* End PBXProject section */

/* Begin PBXResourcesBuildPhase section */
		{ids['resources_build_phase']} /* Resources */ = {{
			isa = PBXResourcesBuildPhase;
			buildActionMask = 2147483647;
			files = (
				{ids['assets']} /* Assets.xcassets in Resources */,
				{ids['launchscreen']} /* LaunchScreen.storyboard in Resources */,
			);
			runOnlyForDeploymentPostprocessing = 0;
		}};
/* End PBXResourcesBuildPhase section */

/* Begin PBXSourcesBuildPhase section */
		{ids['sources_build_phase']} /* Sources */ = {{
			isa = PBXSourcesBuildPhase;
			buildActionMask = 2147483647;
			files = (
{swift_sources_phase}
			);
			runOnlyForDeploymentPostprocessing = 0;
		}};
/* End PBXSourcesBuildPhase section */

/* Begin XCBuildConfiguration section */
		{ids['debug_config']} /* Debug */ = {{
			isa = XCBuildConfiguration;
			buildSettings = {{
				ALWAYS_SEARCH_USER_PATHS = NO;
				ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
				ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME = AccentColor;
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
				CODE_SIGN_STYLE = Automatic;
				COPY_PHASE_STRIP = NO;
				DEBUG_INFORMATION_FORMAT = dwarf;
				ENABLE_STRICT_OBJC_MSGSEND = YES;
				ENABLE_TESTABILITY = YES;
				FRAMEWORK_SEARCH_PATHS = (
					"$(inherited)",
					"$(SRCROOT)/../shared/build/XCFrameworks/debug",
				);
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
				IPHONEOS_DEPLOYMENT_TARGET = 16.0;
				MTL_ENABLE_DEBUG_INFO = INCLUDE_SOURCE;
				MTL_FAST_MATH = YES;
				ONLY_ACTIVE_ARCH = YES;
				INFOPLIST_FILE = iosApp/Info.plist;
				OTHER_LDFLAGS = (
					"$(inherited)",
					"-framework",
					"CFNetwork",
					"-framework",
					"Security",
				);
				PRODUCT_BUNDLE_IDENTIFIER = {bundle_id};
				PRODUCT_NAME = iosApp;
				SDKROOT = iphoneos;
				SWIFT_EMIT_LOC_STRINGS = YES;
				SWIFT_OPTIMIZATION_LEVEL = "-Onone";
				SWIFT_VERSION = 5.0;
				TARGETED_DEVICE_FAMILY = "1,2";
			}};
			name = Debug;
		}};
		{ids['release_config']} /* Release */ = {{
			isa = XCBuildConfiguration;
			buildSettings = {{
				ALWAYS_SEARCH_USER_PATHS = NO;
				ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
				ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME = AccentColor;
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
				CODE_SIGN_STYLE = Automatic;
				COPY_PHASE_STRIP = YES;
				DEBUG_INFORMATION_FORMAT = "dwarf-with-dsym";
				ENABLE_NS_ASSERTIONS = NO;
				ENABLE_STRICT_OBJC_MSGSEND = YES;
				FRAMEWORK_SEARCH_PATHS = (
					"$(inherited)",
					"$(SRCROOT)/../shared/build/XCFrameworks/release",
				);
				GCC_C_LANGUAGE_DIALECT = c99;
				GCC_NO_COMMON_BLOCKS = YES;
				GCC_OPTIMIZATION_LEVEL = s;
				GCC_WARN_64_TO_32_BIT_CONVERSION = YES;
				GCC_WARN_ABOUT_RETURN_TYPE = YES_ERROR;
				GCC_WARN_UNDECLARED_SELECTOR = YES;
				GCC_WARN_UNINITIALIZED_AUTOS = YES_AGGRESSIVE;
				GCC_WARN_UNUSED_FUNCTION = YES;
				GCC_WARN_UNUSED_VARIABLE = YES;
				IPHONEOS_DEPLOYMENT_TARGET = 16.0;
				MTL_ENABLE_DEBUG_INFO = NO;
				MTL_FAST_MATH = YES;
				INFOPLIST_FILE = iosApp/Info.plist;
				OTHER_LDFLAGS = (
					"$(inherited)",
					"-framework",
					"CFNetwork",
					"-framework",
					"Security",
				);
				PRODUCT_BUNDLE_IDENTIFIER = {bundle_id};
				PRODUCT_NAME = iosApp;
				SDKROOT = iphoneos;
				SWIFT_COMPILATION_MODE = wholemodule;
				SWIFT_EMIT_LOC_STRINGS = YES;
				SWIFT_OPTIMIZATION_LEVEL = "-O";
				SWIFT_VERSION = 5.0;
				TARGETED_DEVICE_FAMILY = "1,2";
				VALIDATE_PRODUCT = YES;
			}};
			name = Release;
		}};
		{ids['project_debug']} /* Debug */ = {{
			isa = XCBuildConfiguration;
			buildSettings = {{
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
				IPHONEOS_DEPLOYMENT_TARGET = 16.0;
				MTL_ENABLE_DEBUG_INFO = INCLUDE_SOURCE;
				MTL_FAST_MATH = YES;
				ONLY_ACTIVE_ARCH = YES;
				SDKROOT = iphoneos;
				SWIFT_ACTIVE_COMPILATION_CONDITIONS = DEBUG;
				SWIFT_OPTIMIZATION_LEVEL = "-Onone";
				SWIFT_VERSION = 5.0;
			}};
			name = Debug;
		}};
		{ids['project_release']} /* Release */ = {{
			isa = XCBuildConfiguration;
			buildSettings = {{
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
				COPY_PHASE_STRIP = YES;
				DEBUG_INFORMATION_FORMAT = "dwarf-with-dsym";
				ENABLE_NS_ASSERTIONS = NO;
				ENABLE_STRICT_OBJC_MSGSEND = YES;
				GCC_C_LANGUAGE_DIALECT = c99;
				GCC_NO_COMMON_BLOCKS = YES;
				GCC_OPTIMIZATION_LEVEL = s;
				GCC_WARN_64_TO_32_BIT_CONVERSION = YES;
				GCC_WARN_ABOUT_RETURN_TYPE = YES_ERROR;
				GCC_WARN_UNDECLARED_SELECTOR = YES;
				GCC_WARN_UNINITIALIZED_AUTOS = YES_AGGRESSIVE;
				GCC_WARN_UNUSED_FUNCTION = YES;
				GCC_WARN_UNUSED_VARIABLE = YES;
				IPHONEOS_DEPLOYMENT_TARGET = 16.0;
				MTL_ENABLE_DEBUG_INFO = NO;
				MTL_FAST_MATH = YES;
				SDKROOT = iphoneos;
				SWIFT_COMPILATION_MODE = wholemodule;
				SWIFT_OPTIMIZATION_LEVEL = "-O";
				SWIFT_VERSION = 5.0;
				VALIDATE_PRODUCT = YES;
			}};
			name = Release;
		}};
/* End XCBuildConfiguration section */

/* Begin XCConfigurationList section */
		{ids['project_config_list']} /* Build configuration list for PBXProject "iosApp" */ = {{
			isa = XCConfigurationList;
			buildConfigurations = (
				{ids['project_debug']} /* Debug */,
				{ids['project_release']} /* Release */,
			);
			defaultConfigurationIsVisible = 0;
			defaultConfigurationName = Release;
		}};
		{ids['target_config_list']} /* Build configuration list for PBXNativeTarget "iosApp" */ = {{
			isa = XCConfigurationList;
			buildConfigurations = (
				{ids['debug_config']} /* Debug */,
				{ids['release_config']} /* Release */,
			);
			defaultConfigurationIsVisible = 0;
			defaultConfigurationName = Release;
		}};
/* End XCConfigurationList section */
	}};
	rootObject = {ids['project']} /* Project object */;
}}
"""

    # Write pbxproj file
    pbxproj_path = xcodeproj_path / "project.pbxproj"
    pbxproj_path.write_text(pbxproj)
    print(f"✅ Generated {pbxproj_path}")

    # No project.pbxproj.orig: it was written with the same content as project.pbxproj in the
    # same breath, so it backed up nothing and only doubled the diff on every regeneration.

    # Create xcworkspace Contents.json
    workspace_path = xcodeproj_path / "project.xcworkspace" / "contents.xcworkspacedata"
    workspace_contents = """<?xml version="1.0" encoding="UTF-8"?>
<Workspace version = "1.0">
   <FileRef
      location = "group:iosApp.xcodeproj">
   </FileRef>
</Workspace>
"""
    workspace_path.write_text(workspace_contents)
    print(f"✅ Generated {workspace_path}")

    # A *shared* scheme. `xcodebuild archive` requires -scheme (there is no -target form for
    # archiving), and whether xcodebuild autocreates one for a project with no xcshareddata is
    # version-dependent — exactly the kind of thing that works locally and fails on a runner.
    # Committing the scheme removes the question.
    schemes_dir = xcodeproj_path / "xcshareddata" / "xcschemes"
    schemes_dir.mkdir(parents=True, exist_ok=True)
    scheme_path = schemes_dir / "iosApp.xcscheme"
    buildable = f"""<BuildableReference
               BuildableIdentifier = "primary"
               BlueprintIdentifier = "{ids['native_target']}"
               BuildableName = "iosApp.app"
               BlueprintName = "iosApp"
               ReferencedContainer = "container:iosApp.xcodeproj">
            </BuildableReference>"""
    scheme_contents = f"""<?xml version="1.0" encoding="UTF-8"?>
<Scheme
   LastUpgradeVersion = "1500"
   version = "1.7">
   <BuildAction
      parallelizeBuildables = "YES"
      buildImplicitDependencies = "YES">
      <BuildActionEntries>
         <BuildActionEntry
            buildForTesting = "YES"
            buildForRunning = "YES"
            buildForProfiling = "YES"
            buildForArchiving = "YES"
            buildForAnalyzing = "YES">
            {buildable}
         </BuildActionEntry>
      </BuildActionEntries>
   </BuildAction>
   <TestAction
      buildConfiguration = "Debug"
      selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB"
      selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB"
      shouldUseLaunchSchemeArgsEnv = "YES">
      <Testables>
      </Testables>
   </TestAction>
   <LaunchAction
      buildConfiguration = "Debug"
      selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB"
      selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB"
      launchStyle = "0"
      useCustomWorkingDirectory = "NO"
      ignoresPersistentStateOnLaunch = "NO"
      debugDocumentVersioning = "YES"
      debugServiceExtension = "internal"
      allowLocationSimulation = "YES">
      <BuildableProductRunnable
         runnableDebuggingMode = "0">
         {buildable}
      </BuildableProductRunnable>
   </LaunchAction>
   <ProfileAction
      buildConfiguration = "Release"
      shouldUseLaunchSchemeArgsEnv = "YES"
      savedToolIdentifier = ""
      useCustomWorkingDirectory = "NO"
      debugDocumentVersioning = "YES">
      <BuildableProductRunnable
         runnableDebuggingMode = "0">
         {buildable}
      </BuildableProductRunnable>
   </ProfileAction>
   <AnalyzeAction
      buildConfiguration = "Debug">
   </AnalyzeAction>
   <ArchiveAction
      buildConfiguration = "Release"
      revealArchiveInOrganizer = "YES">
   </ArchiveAction>
</Scheme>
"""
    scheme_path.write_text(scheme_contents)
    print(f"✅ Generated {scheme_path}")

    print(f"✅ Xcode project structure created successfully")


if __name__ == "__main__":
    create_xcode_project()
