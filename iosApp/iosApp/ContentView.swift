import SwiftUI
import Shared

struct ContentView: View {
    @State private var message = "Loading..."

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "car.fill")
                .font(.system(size: 48))
                .foregroundColor(.blue)

            Text("SawaariShare")
                .font(.title)
                .fontWeight(.bold)

            Text("US Desi Student Carpools")
                .font(.subheadline)
                .foregroundColor(.gray)

            Spacer()

            Text(message)
                .font(.body)
                .multilineTextAlignment(.center)
                .padding()

            Spacer()

            NavigationLink(destination: Text("Login Screen (Coming Soon)")) {
                Text("Get Started")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(8)
            }
            .padding()
        }
        .padding()
        .onAppear {
            message = "iOS app ready. Shared framework linked successfully."
        }
    }
}

#Preview {
    ContentView()
}
