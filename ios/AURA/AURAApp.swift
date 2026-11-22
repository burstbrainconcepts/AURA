import SwiftUI

@main
struct AURAApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(.dark)
        }
    }
}

struct ContentView: View {
    @State private var selectedTab = 0
    @State private var message = ""
    @State private var messages: [String] = []

    var body: some View {
        ZStack {
            LinearGradient(colors: [Color(red: 0.07, green: 0.11, blue: 0.22), Color(red: 0.025, green: 0.04, blue: 0.09)], startPoint: .topLeading, endPoint: .bottomTrailing)
                .ignoresSafeArea()
            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 13).fill(LinearGradient(colors: [.indigo, Color(red: 0.12, green: 0.2, blue: 0.42)], startPoint: .topLeading, endPoint: .bottomTrailing))
                        Text("A").font(.system(size: 22, weight: .semibold, design: .rounded)).foregroundStyle(.white)
                    }.frame(width: 42, height: 42)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("AURA").font(.system(size: 16, weight: .bold, design: .rounded)).tracking(3)
                        Text("PRIVATE INTELLIGENCE").font(.system(size: 8, weight: .semibold)).tracking(1.7).foregroundStyle(.blue.opacity(0.8))
                    }
                    Spacer()
                    Circle().fill(.green).frame(width: 7, height: 7)
                    Text("On device").font(.caption2).foregroundStyle(.secondary)
                }.padding(.horizontal, 22).padding(.top, 14).padding(.bottom, 20)

                TabView(selection: $selectedTab) {
                    assistant.tag(0).tabItem { Label("Assistant", systemImage: "sparkles") }
                    memory.tag(1).tabItem { Label("Memory", systemImage: "lock.shield") }
                    guardView.tag(2).tabItem { Label("Guard", systemImage: "shield.lefthalf.filled") }
                    settings.tag(3).tabItem { Label("Settings", systemImage: "slider.horizontal.3") }
                }.tint(Color(red: 0.58, green: 0.68, blue: 1))
            }
        }
    }

    private var assistant: some View {
        VStack(spacing: 18) {
            Spacer()
            ZStack {
                Circle().fill(Color.indigo.opacity(0.15)).frame(width: 106, height: 106)
                Image(systemName: "sparkle").font(.system(size: 42, weight: .light)).foregroundStyle(Color(red: 0.68, green: 0.76, blue: 1))
            }
            Text("Think clearly.").font(.system(size: 32, weight: .semibold, design: .rounded))
            Text("Move confidently.").font(.system(size: 25, weight: .medium, design: .rounded)).foregroundStyle(.blue.opacity(0.85))
            Text("Your private, local-first assistant.").font(.subheadline).foregroundStyle(.secondary)
            Spacer()
            ScrollView {
                VStack(alignment: .trailing, spacing: 10) {
                    ForEach(Array(messages.enumerated()), id: \.offset) { _, item in
                        Text(item).padding(13).background(Color.indigo.opacity(0.3), in: RoundedRectangle(cornerRadius: 14))
                    }
                }.frame(maxWidth: .infinity, alignment: .trailing)
            }.frame(maxHeight: 170)
            HStack(spacing: 10) {
                TextField("Message AURA...", text: $message, axis: .vertical).lineLimit(1...4).padding(13).background(.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 13))
                Button {
                    let value = message.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !value.isEmpty else { return }
                    messages.append(value)
                    message = ""
                } label: {
                    Image(systemName: "arrow.up").font(.headline).foregroundStyle(.white).frame(width: 45, height: 45).background(Color.indigo, in: RoundedRectangle(cornerRadius: 13))
                }
            }.padding(.bottom, 12)
            Text("Preview interface · local inference adapter not connected").font(.system(size: 10)).foregroundStyle(.secondary).padding(.bottom, 5)
        }.padding(.horizontal, 22)
    }

    private var memory: some View {
        infoPage(icon: "lock.fill", title: "Your memory. Your choice.", detail: "Encrypted, opt-in memory will be connected to the AURA shared memory contract. Nothing is saved by this preview.")
    }

    private var guardView: some View {
        infoPage(icon: "shield.lefthalf.filled", title: "Device protection", detail: "iOS recovery is permission-limited. AURA can provide account security, device check-ins and recovery guidance, but cannot remotely lock or control an iPhone like Android Device Admin.")
    }

    private var settings: some View {
        infoPage(icon: "slider.horizontal.3", title: "AURA settings", detail: "Local model, privacy, voice and recovery-service configuration will be added through native iOS adapters.")
    }

    private func infoPage(icon: String, title: String, detail: String) -> some View {
        VStack(spacing: 18) {
            Spacer()
            Image(systemName: icon).font(.system(size: 42, weight: .light)).foregroundStyle(.blue.opacity(0.9))
            Text(title).font(.system(size: 25, weight: .semibold, design: .rounded)).multilineTextAlignment(.center)
            Text(detail).font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center).lineSpacing(5)
            Spacer()
        }.padding(28)
    }
}
