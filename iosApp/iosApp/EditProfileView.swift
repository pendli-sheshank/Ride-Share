import SwiftUI
import PhotosUI
import Shared

/// Mirrors Android's `EditProfileDialog` (`SplitCruiserApp.kt:6409-6575`): the same six preset
/// avatars, a custom image URL field, name/last-initial fields, plus a direct photo upload
/// (Android has none in its dialog — only in onboarding — but there is no reason iOS editing
/// should be worse than iOS onboarding at the same task).
struct EditProfileView: View {
    @ObservedObject var viewModel: AppViewModel
    @Environment(\.presentationMode) private var presentationMode

    @State private var name: String
    @State private var lastInitial: String
    @State private var avatarUrl: String
    @State private var customUrlInput: String
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var isUploadingPhoto = false
    @State private var validationError: String?

    init(viewModel: AppViewModel) {
        self.viewModel = viewModel
        let user = viewModel.currentUser
        _name = State(initialValue: user?.name ?? "")
        _lastInitial = State(initialValue: user?.lastInitial ?? "")
        let currentAvatar = user?.avatarUrl ?? ""
        _avatarUrl = State(initialValue: currentAvatar)
        _customUrlInput = State(initialValue: currentAvatar.hasPrefix("http") ? currentAvatar : "")
    }

    private static let presets: [(key: String, emoji: String)] = [
        ("preset_grad", "🎓"), ("preset_driver", "🚗"), ("preset_tech", "💻"),
        ("preset_explorer", "🎒"), ("preset_star", "⭐"), ("preset_globe", "🌐"),
    ]

    private var previewAvatarUrl: String {
        customUrlInput.isEmpty ? avatarUrl : customUrlInput
    }

    var body: some View {
        NavigationView {
            Form {
                Section("Profile picture") {
                    HStack {
                        Spacer()
                        BrandAvatar(avatarUrl: previewAvatarUrl, name: name, size: 72)
                        Spacer()
                    }

                    PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                        Label(isUploadingPhoto ? "Uploading…" : "Upload a photo", systemImage: "camera.fill")
                    }
                    .disabled(isUploadingPhoto)
                    .onChange(of: selectedPhotoItem) { newItem in
                        Task { await uploadSelectedPhoto(newItem) }
                    }

                    Text("Or select a preset avatar:")
                        .font(.caption)
                        .foregroundColor(Brand.textSecondary)

                    HStack {
                        ForEach(Self.presets, id: \.key) { preset in
                            presetButton(preset)
                        }
                    }

                    TextField("Or paste a custom image URL", text: $customUrlInput)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .onChange(of: customUrlInput) { newValue in
                            if !newValue.isEmpty { avatarUrl = newValue }
                        }
                }

                Section("Personal details") {
                    TextField("First name", text: $name)
                    TextField("Initial", text: $lastInitial)
                }

                if let error = validationError ?? viewModel.errorMessage {
                    Text(error).font(.caption).foregroundColor(Brand.danger)
                }

                Button("Save changes") { submit() }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || viewModel.isLoading)
            }
            .navigationTitle("Edit your profile")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { presentationMode.wrappedValue.dismiss() }
                }
            }
        }
    }

    private func presetButton(_ preset: (key: String, emoji: String)) -> some View {
        let isSelected = avatarUrl == preset.key && customUrlInput.isEmpty
        return Button {
            avatarUrl = preset.key
            customUrlInput = ""
        } label: {
            Text(preset.emoji)
                .font(.title3)
                .frame(width: 40, height: 40)
                .background(isSelected ? Brand.primary.opacity(0.25) : Brand.surface)
                .clipShape(Circle())
                .overlay(
                    Circle().stroke(isSelected ? Brand.primary : .clear, lineWidth: 2)
                )
        }
        .buttonStyle(.plain)
    }

    /// See `ProfileImageResizer` (`Theme.swift`) — the same 512px/JPEG-0.85 contract
    /// `ProfileSetupView`'s picker uses, matching Android's `ProfileImages.kt`.
    private func uploadSelectedPhoto(_ item: PhotosPickerItem?) async {
        guard let item, let userId = viewModel.currentUser?.id else { return }
        isUploadingPhoto = true
        defer { isUploadingPhoto = false }
        guard let data = try? await item.loadTransferable(type: Data.self),
              let jpegData = ProfileImageResizer.resizeToUploadContract(data) else {
            validationError = "Couldn't read that photo. Try another one."
            return
        }
        if await viewModel.uploadProfilePicture(userId: userId, imageData: jpegData) {
            avatarUrl = viewModel.currentUser?.avatarUrl ?? avatarUrl
            customUrlInput = ""
        }
    }

    private func submit() {
        validationError = nil
        let trimmedName = name.trimmingCharacters(in: .whitespaces)
        guard !trimmedName.isEmpty else {
            validationError = "Enter a first name."
            return
        }
        Task {
            let finalAvatar = customUrlInput.isEmpty ? avatarUrl : customUrlInput
            if await viewModel.updateProfile(name: trimmedName, lastInitial: lastInitial, avatarUrl: finalAvatar) {
                presentationMode.wrappedValue.dismiss()
            }
        }
    }
}
