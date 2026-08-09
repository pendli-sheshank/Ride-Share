import SwiftUI
import Shared

// MARK: - Login

/// Combined log in / sign up, matching Android's `EmailPasswordLoginScreen`.
///
/// Wrapped in a `ScrollView`: the previous layout used two `Spacer`s and no scroll, so on a small
/// device with the keyboard up the form was clipped and unreachable.
///
/// Google sign-in stays Android-only — the token exchange is shared, but only Android acquires a
/// Google ID token, and iOS would need an `ASWebAuthenticationSession` plus a URL scheme in the
/// generated Xcode project.
struct LoginScreen: View {
    @EnvironmentObject private var viewModel: AppViewModel

    @State private var email = ""
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var isSigningUp = false
    @State private var formError: String?
    @State private var isSubmitting = false

    var body: some View {
        ScrollView {
            VStack(spacing: BrandScale.spaceLg) {
                heroCard
                wordmark

                Text("US Desi Rideshare. Cost-split, trust-matched.")
                    .font(BrandFont.body())
                    .foregroundColor(Brand.textSecondary)
                    .multilineTextAlignment(.center)

                FirebaseStatusPill(isEnabled: viewModel.isBackendConfigured)

                form
            }
            .padding(.horizontal, BrandScale.spaceXl)
            .padding(.vertical, BrandScale.spaceXl)
        }
        .background(Brand.surface)
        .scrollContentBackground(.hidden)
    }

    private var heroCard: some View {
        Image("AuthIllustration")
            .resizable()
            .scaledToFill()
            .frame(height: 180)
            .clipped()
            .cornerRadius(20)
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(Brand.outline, lineWidth: 1))
    }

    private var wordmark: some View {
        HStack(spacing: BrandScale.spaceMd) {
            Image("SplitCruiserLogo")
                .resizable()
                .scaledToFill()
                .frame(width: 48, height: 48)
                .clipShape(Circle())
                .overlay(Circle().stroke(Brand.primaryContainer, lineWidth: 1.5))
            Text("Split Cruiser")
                .font(.system(size: 28, weight: .black))
                .foregroundColor(Brand.textPrimary)
        }
    }

    private var form: some View {
        VStack(spacing: BrandScale.spaceMd) {
            BrandTextField(
                title: "Email Address",
                placeholder: "your.email@example.com",
                text: $email,
                icon: "envelope.fill",
                keyboard: .emailAddress,
                accessibilityID: "email_input"
            )
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()

            SecureBrandField(
                title: "Password",
                placeholder: "At least 6 characters",
                text: $password,
                accessibilityID: "password_input"
            )

            if isSigningUp {
                SecureBrandField(
                    title: "Confirm Password",
                    placeholder: "Re-enter your password",
                    text: $confirmPassword,
                    accessibilityID: "confirm_password_input"
                )
            }

            if !viewModel.isBackendConfigured {
                Text("This build has no Firebase configuration, so sign-in is unavailable.")
                    .font(BrandFont.eyebrow(.regular))
                    // Not `Brand.warning` on this near-white background — #EAB308 on #F8F9FF is
                    // about 1.9:1 and unreadable. The amber Android uses for foreground text is
                    // the darker `pendingAmber`.
                    .foregroundColor(BrandLiteral.pendingAmber)
                    .multilineTextAlignment(.center)
            }

            if let formError {
                Text(formError)
                    .font(BrandFont.caption())
                    .foregroundColor(Brand.danger)
                    .multilineTextAlignment(.center)
            }

            Button(isSigningUp ? "Sign Up" : "Log In") { submit() }
                .buttonStyle(BrandButtonStyle(isEnabled: !isSubmitting))
                .disabled(isSubmitting)
                .accessibilityIdentifier("auth_submit_button")

            Button(isSigningUp
                   ? "Already have an account? Log In"
                   : "Don't have an account? Sign Up") {
                withAnimation { isSigningUp.toggle() }
                formError = nil
            }
            .font(BrandFont.body(.semibold))
            .foregroundColor(Brand.primary)

            Text("Split Cruiser connects verified riders safely. Cost-split, trust-matched.")
                .font(BrandFont.eyebrow(.regular))
                .foregroundColor(Brand.textSecondary.opacity(0.5))
                .multilineTextAlignment(.center)
                .padding(.top, BrandScale.spaceSm)
        }
    }

    private func submit() {
        formError = nil
        guard !email.isEmpty, !password.isEmpty else {
            formError = "Email and password cannot be empty"
            return
        }
        guard email.contains("@"), email.contains(".") else {
            formError = "Please enter a valid email address."
            return
        }
        if isSigningUp {
            guard password.count >= 6 else {
                formError = "Password must be at least 6 characters"
                return
            }
            guard password == confirmPassword else {
                formError = "Passwords do not match"
                return
            }
        }
        isSubmitting = true
        Task {
            if isSigningUp {
                await viewModel.signUp(email: email, password: password)
            } else {
                await viewModel.logIn(email: email, password: password)
            }
            isSubmitting = false
        }
    }
}

/// A password field with the same treatment as `BrandTextField`.
struct SecureBrandField: View {
    let title: String
    var placeholder: String = ""
    @Binding var text: String
    var accessibilityID: String?

    @State private var isRevealed = false

    var body: some View {
        VStack(alignment: .leading, spacing: BrandScale.spaceXs) {
            Text(title)
                .font(BrandFont.eyebrow(.semibold))
                .foregroundColor(Brand.textSecondary)
            HStack(spacing: BrandScale.spaceSm) {
                Image(systemName: "lock.fill").foregroundColor(Brand.primary)
                Group {
                    if isRevealed {
                        TextField(placeholder, text: $text)
                    } else {
                        SecureField(placeholder, text: $text)
                    }
                }
                .textFieldStyle(.plain)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .foregroundColor(Brand.textPrimary)
                .accessibilityIdentifier(accessibilityID ?? "")

                Button {
                    isRevealed.toggle()
                } label: {
                    Image(systemName: isRevealed ? "eye.slash.fill" : "eye.fill")
                        .foregroundColor(Brand.textSecondary)
                }
                .buttonStyle(.plain)
            }
            .padding(BrandScale.spaceMd)
            .background(Brand.surfaceCard)
            .cornerRadius(BrandScale.radiusMd)
            .overlay(
                RoundedRectangle(cornerRadius: BrandScale.radiusMd)
                    .stroke(Brand.outline, lineWidth: 1)
            )
        }
    }
}

// MARK: - Onboarding

/// Collects what Android collects: name, last initial, home area, contact number, home address,
/// and optionally a vehicle.
struct ProfileSetupScreen: View {
    @EnvironmentObject private var viewModel: AppViewModel

    @State private var name = ""
    @State private var lastInitial = ""
    @State private var homeArea = ""
    @State private var phoneNumber = ""
    @State private var homeAddress = PlaceSelection()
    @State private var isDriver = false
    @State private var make = ""
    @State private var model = ""
    @State private var year = ""
    @State private var colour = ""
    @State private var plate = ""
    @State private var formError: String?
    @State private var isSubmitting = false

    /// Every field `submit()` rejects is also required here. These used to disagree: the phone
    /// number was mandatory on submit but absent from `canSubmit`, so the button looked enabled
    /// and tapping it just produced an error.
    private var canSubmit: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
            && !lastInitial.trimmingCharacters(in: .whitespaces).isEmpty
            && !homeArea.trimmingCharacters(in: .whitespaces).isEmpty
            && !phoneNumber.trimmingCharacters(in: .whitespaces).isEmpty
            && !isSubmitting
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: BrandScale.spaceXl) {
                header

                FormSection(title: "About you") {
                    BrandTextField(title: "First Name", placeholder: "Amit", text: $name,
                                   icon: "person.fill", accessibilityID: "name_input")
                    BrandTextField(title: "Last Initial (1 letter)", placeholder: "S",
                                   text: $lastInitial, icon: "textformat",
                                   accessibilityID: "initial_input")
                        .onChange(of: lastInitial) { value in
                            lastInitial = String(value.prefix(1)).uppercased()
                        }
                    BrandTextField(title: "Home Area", placeholder: "e.g. Mission Hill",
                                   text: $homeArea, icon: "house.fill",
                                   accessibilityID: "home_area_input")
                }

                FormSection(title: "How riders reach you") {
                    BrandTextField(title: "Contact Number", placeholder: "+1 617 555 0100",
                                   text: $phoneNumber, icon: "phone.fill", keyboard: .phonePad,
                                   accessibilityID: "phone_input")
                    Text("Shared with people you're matched with, so they can find you at pickup.")
                        .font(BrandFont.eyebrow(.regular))
                        .foregroundColor(Brand.textSecondary)
                }

                FormSection(title: "Where you usually start") {
                    LocationAutocompleteField(
                        title: "Home Address",
                        placeholder: "Where should pickups start from?",
                        selection: $homeAddress,
                        viewModel: viewModel,
                        accent: Brand.success,
                        leadingSystemImage: "house.fill",
                        accessibilityID: "home_address_input"
                    )
                    Text("Private to you. Ride requests start from here so you don't retype it.")
                        .font(BrandFont.eyebrow(.regular))
                        .foregroundColor(Brand.textSecondary)
                }

                FormSection(title: "Your vehicle (optional)") {
                    Toggle(isOn: $isDriver) {
                        HStack(spacing: BrandScale.spaceSm) {
                            Image(systemName: "car.fill").foregroundColor(Brand.primary)
                            VStack(alignment: .leading, spacing: 1) {
                                Text("Are you offering rides?")
                                    .font(BrandFont.body(.bold))
                                    .foregroundColor(Brand.textPrimary)
                                Text("Add your vehicle details now (optional)")
                                    .font(BrandFont.eyebrow(.regular))
                                    .foregroundColor(Brand.textSecondary)
                            }
                        }
                    }
                    .tint(Brand.primary)

                    if isDriver {
                        BrandTextField(title: "Car Make", placeholder: "Toyota", text: $make, icon: "car.fill")
                        BrandTextField(title: "Car Model", placeholder: "Camry", text: $model, icon: "car.fill")
                        BrandTextField(title: "Car Year", placeholder: "2021", text: $year,
                                       icon: "calendar", keyboard: .numberPad)
                        BrandTextField(title: "Colour", placeholder: "Silver", text: $colour, icon: "paintpalette.fill")
                        BrandTextField(title: "License Plate", placeholder: "7XYZ99", text: $plate, icon: "rectangle.fill")
                            .onChange(of: plate) { plate = $0.uppercased() }
                    }
                }

                if let formError {
                    Text(formError)
                        .font(BrandFont.caption())
                        .foregroundColor(Brand.danger)
                }

                Button("Finish setup") { submit() }
                    .buttonStyle(BrandButtonStyle(isEnabled: canSubmit))
                    .disabled(!canSubmit)
                    .accessibilityIdentifier("submit_profile_button")

                Spacer().frame(height: BrandScale.spaceXl)
            }
            .padding(BrandScale.spaceXl)
        }
        .background(Brand.surface)
        .scrollContentBackground(.hidden)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: BrandScale.spaceSm) {
            Image("SplitCruiserLogo")
                .resizable()
                .scaledToFill()
                .frame(width: 72, height: 72)
                .clipShape(Circle())
                .overlay(Circle().stroke(Brand.primaryContainer, lineWidth: 2))
            Text("Set up your profile")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(Brand.textPrimary)
            Text("Add your details so matches can coordinate.")
                .font(BrandFont.caption())
                .foregroundColor(Brand.textSecondary)
        }
    }

    private func submit() {
        formError = nil
        guard !phoneNumber.trimmingCharacters(in: .whitespaces).isEmpty else {
            formError = "Please enter a contact number so riders can reach you"
            return
        }
        let vehicle: Vehicle? = isDriver
            ? Vehicle(
                ownerId: viewModel.currentUser?.id ?? "",
                make: make, model: model, year: year, color: colour, licensePlate: plate
              )
            : nil

        isSubmitting = true
        Task {
            await viewModel.completeProfile(
                name: name,
                lastInitial: lastInitial,
                homeArea: homeArea,
                phoneNumber: phoneNumber,
                homeAddress: homeAddress.name,
                homeLat: homeAddress.lat,
                homeLng: homeAddress.lon,
                vehicle: vehicle
            )
            isSubmitting = false
        }
    }
}
