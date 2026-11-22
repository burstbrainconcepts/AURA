import SwiftUI
import Foundation
import Combine
import CryptoKit
import Security


struct AuraMemoryItem: Codable, Identifiable {
    let id: UUID
    let text: String
    let createdAt: Date
}

@MainActor
final class AuraMemoryVault: ObservableObject {
    @Published private(set) var items: [AuraMemoryItem] = []
    @Published private(set) var enabled = UserDefaults.standard.bool(forKey: "aura.memory.enabled")
    @Published var status = "Memory is off. Enable it to create an encrypted vault."

    private let service = "com.burstbrainconcepts.aura.memory"
    private let account = "vault-key"

    init() {
        if enabled { load() }
    }

    func setEnabled(_ value: Bool) {
        if value {
            do {
                _ = try encryptionKey()
                enabled = true
                UserDefaults.standard.set(true, forKey: "aura.memory.enabled")
                load()
                status = "Encrypted memory is enabled. Save items manually."
            } catch {
                enabled = false
                status = "Could not enable encrypted memory: " + error.localizedDescription
            }
        } else {
            enabled = false
            UserDefaults.standard.set(false, forKey: "aura.memory.enabled")
            items.removeAll()
            try? FileManager.default.removeItem(at: vaultURL)
            deleteKey()
            status = "Memory disabled. The encrypted vault and its key were deleted."
        }
    }

    func add(_ text: String) {
        guard enabled else { status = "Enable memory before saving."; return }
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, value.count <= 8000 else { status = "Memory must contain 1–8000 characters."; return }
        items.append(AuraMemoryItem(id: UUID(), text: value, createdAt: Date()))
        do { try persist(); status = "Saved securely on this device." }
        catch { items.removeLast(); status = "Could not save memory: " + error.localizedDescription }
    }

    func delete(_ id: UUID) {
        let previous = items
        items.removeAll { $0.id == id }
        do { try persist(); status = "Memory item deleted." }
        catch { items = previous; status = "Could not update vault: " + error.localizedDescription }
    }

    private var vaultURL: URL {
        let root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return root.appendingPathComponent("AURA", isDirectory: true).appendingPathComponent("memory.aesgcm")
    }

    private func encryptionKey() throws -> SymmetricKey {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecSuccess, let data = result as? Data {
            return SymmetricKey(data: data)
        }
        guard status == errSecItemNotFound else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
        }
        let key = SymmetricKey(size: .bits256)
        let data = key.withUnsafeBytes { Data($0) }
        let add: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData as String: data
        ]
        let addStatus = SecItemAdd(add as CFDictionary, nil)
        guard addStatus == errSecSuccess else { throw NSError(domain: NSOSStatusErrorDomain, code: Int(addStatus)) }
        return key
    }

    private func persist() throws {
        guard enabled else { return }
        let directory = vaultURL.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let plaintext = try JSONEncoder().encode(items)
        let sealed = try AES.GCM.seal(plaintext, using: encryptionKey())
        guard let combined = sealed.combined else {
            throw NSError(domain: "AURA.Memory", code: 1, userInfo: [NSLocalizedDescriptionKey: "Unable to encode encrypted memory."])
        }
        try combined.write(to: vaultURL, options: [.atomic, .completeFileProtection])
    }

    private func load() {
        guard enabled, FileManager.default.fileExists(atPath: vaultURL.path) else { items = []; return }
        do {
            let combined = try Data(contentsOf: vaultURL)
            let box = try AES.GCM.SealedBox(combined: combined)
            let plaintext = try AES.GCM.open(box, using: encryptionKey())
            items = try JSONDecoder().decode([AuraMemoryItem].self, from: plaintext)
            status = "Loaded \(items.count) encrypted memory items."
        } catch {
            items = []
            status = "Vault could not be opened. Existing encrypted data was preserved."
        }
    }

    private func deleteKey() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}

struct AuraChatMessage: Codable, Identifiable {
    var role: String
    var content: String
    var id = UUID()
    enum CodingKeys: String, CodingKey { case role, content }
}

private struct LocalChatRequest: Encodable {
    let model: String
    let messages: [AuraChatMessage]
    let temperature: Double
    let max_tokens: Int
    let stream: Bool
}

private struct LocalChatResponse: Decodable {
    struct Choice: Decodable {
        struct Message: Decodable { let role: String?; let content: String }
        let message: Message
    }
    let choices: [Choice]
}

@MainActor
final class AuraAssistantModel: ObservableObject {
    @Published var messages: [AuraChatMessage] = []
    @Published var isBusy = false
    @Published var apiKey = ""
    @Published var engineOnline = false
    @Published var status = "Checking local model…"
    @Published var endpoint: String {
        didSet { UserDefaults.standard.set(endpoint, forKey: "aura.inference.endpoint") }
    }

    init() {
        endpoint = UserDefaults.standard.string(forKey: "aura.inference.endpoint") ?? "http://127.0.0.1:8080"
    }

    private func makeURL(_ suffix: String) throws -> URL {
        let clean = endpoint.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard let base = URL(string: clean),
              ["http", "https"].contains(base.scheme?.lowercased() ?? ""),
              base.host != nil, base.user == nil, base.password == nil else {
            throw NSError(domain: "AURA", code: 1, userInfo: [NSLocalizedDescriptionKey: "Enter a valid HTTP or HTTPS local inference URL."])
        }
        return URL(string: clean + suffix)!
    }

    func checkHealth() async {
        do {
            let url = try makeURL("/health")
            var request = URLRequest(url: url)
            request.httpMethod = "GET"
            if !apiKey.isEmpty { request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization") }
            request.timeoutInterval = 5
            let (_, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                throw NSError(domain: "AURA", code: 2, userInfo: [NSLocalizedDescriptionKey: "The local model server did not return a healthy response."])
            }
            engineOnline = true
            status = "Local model connected"
        } catch {
            engineOnline = false
            status = "Local model offline · " + error.localizedDescription
        }
    }

    func send(_ text: String) async {
        let prompt = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !prompt.isEmpty, !isBusy else { return }
        isBusy = true
        messages.append(AuraChatMessage(role: "user", content: prompt))
        defer { isBusy = false }
        do {
            let url = try makeURL("/v1/chat/completions")
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.timeoutInterval = 120
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            if !apiKey.isEmpty { request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization") }
            let body = LocalChatRequest(model: "local-model", messages: messages, temperature: 0.7, max_tokens: 512, stream: false)
            request.httpBody = try JSONEncoder().encode(body)
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                throw NSError(domain: "AURA", code: 3, userInfo: [NSLocalizedDescriptionKey: "Local inference request failed. Check the server and endpoint."])
            }
            let result = try JSONDecoder().decode(LocalChatResponse.self, from: data)
            guard let answer = result.choices.first?.message.content else {
                throw NSError(domain: "AURA", code: 4, userInfo: [NSLocalizedDescriptionKey: "The local model returned no answer."])
            }
            messages.append(AuraChatMessage(role: "assistant", content: answer))
            engineOnline = true
            status = "Local model connected"
        } catch {
            messages.append(AuraChatMessage(role: "assistant", content: "I couldn't reach the local model: " + error.localizedDescription))
            engineOnline = false
            status = "Local model request failed"
        }
    }

    func clearConversation() { messages.removeAll() }
}

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
    @StateObject private var assistant = AuraAssistantModel()
    @StateObject private var memoryVault = AuraMemoryVault()
    @State private var memoryDraft = ""
    @State private var selectedTab = 0
    @State private var message = ""

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
                    Circle().fill(assistant.engineOnline ? .green : .orange).frame(width: 7, height: 7)
                    Text(assistant.engineOnline ? "Connected" : "Offline").font(.caption2).foregroundStyle(.secondary)
                }.padding(.horizontal, 22).padding(.top, 14).padding(.bottom, 20)

                TabView(selection: $selectedTab) {
                    assistantView.tag(0).tabItem { Label("Assistant", systemImage: "sparkles") }
                    memory.tag(1).tabItem { Label("Memory", systemImage: "lock.shield") }
                    guardView.tag(2).tabItem { Label("Guard", systemImage: "shield.lefthalf.filled") }
                    settings.tag(3).tabItem { Label("Settings", systemImage: "slider.horizontal.3") }
                }.tint(Color(red: 0.58, green: 0.68, blue: 1))
            }
        }
        .task { await assistant.checkHealth() }
    }

    private var assistantView: some View {
        VStack(spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Think clearly.").font(.system(size: 29, weight: .semibold, design: .rounded))
                    Text("Move confidently.").font(.system(size: 22, weight: .medium, design: .rounded)).foregroundStyle(.blue.opacity(0.85))
                }
                Spacer()
                Button { assistant.clearConversation() } label: {
                    Image(systemName: "trash").foregroundStyle(.secondary).padding(10).background(.white.opacity(0.06), in: Circle())
                }.accessibilityLabel("Clear conversation")
            }.padding(.top, 10)

            Text(assistant.status).font(.caption2).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading)

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    if assistant.messages.isEmpty {
                        Text("Connect a local llama.cpp server in Settings, then start a private conversation.")
                            .font(.subheadline).foregroundStyle(.secondary).padding(.top, 28)
                    }
                    ForEach(assistant.messages) { item in
                        HStack {
                            if item.role == "assistant" { messageBubble(item.content, assistant: true); Spacer(minLength: 35) }
                            else { Spacer(minLength: 35); messageBubble(item.content, assistant: false) }
                        }
                    }
                    if assistant.isBusy { ProgressView("AURA is thinking locally…").font(.caption).frame(maxWidth: .infinity, alignment: .leading) }
                }
            }
            .frame(maxHeight: .infinity)

            HStack(spacing: 10) {
                TextField("Message AURA...", text: $message, axis: .vertical).lineLimit(1...4).padding(13).background(.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 13))
                Button {
                    let value = message
                    message = ""
                    Task { await assistant.send(value) }
                } label: {
                    Image(systemName: "arrow.up").font(.headline).foregroundStyle(.white).frame(width: 45, height: 45).background(Color.indigo, in: RoundedRectangle(cornerRadius: 13))
                }.disabled(assistant.isBusy || message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }.padding(.bottom, 8)
        }.padding(.horizontal, 22)
    }

    private func messageBubble(_ text: String, assistant isAssistant: Bool) -> some View {
        Text(text).font(.subheadline).textSelection(.enabled).padding(13)
            .background(isAssistant ? Color.white.opacity(0.07) : Color.indigo.opacity(0.34), in: RoundedRectangle(cornerRadius: 14))
            .frame(maxWidth: 310, alignment: .leading)
    }

    private var memory: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Private memory").font(.system(size: 27, weight: .semibold, design: .rounded))
            Text("Only items you explicitly save are stored. Encryption keys stay in the iOS Keychain.")
                .font(.subheadline).foregroundStyle(.secondary)
            Toggle("Enable encrypted memory", isOn: Binding(
                get: { memoryVault.enabled },
                set: { memoryVault.setEnabled($0) }
            ))
            .tint(.indigo)
            if memoryVault.enabled {
                TextField("Write something to remember…", text: $memoryDraft, axis: .vertical)
                    .lineLimit(2...5).padding(12).background(.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 12))
                Button("Save to encrypted memory") {
                    memoryVault.add(memoryDraft)
                    memoryDraft = ""
                }.buttonStyle(.borderedProminent).tint(.indigo)
                List {
                    ForEach(memoryVault.items) { item in
                        VStack(alignment: .leading, spacing: 5) {
                            Text(item.text).foregroundStyle(.primary)
                            Text(item.createdAt.formatted(date: .abbreviated, time: .shortened)).font(.caption2).foregroundStyle(.secondary)
                            Button("Delete", role: .destructive) { memoryVault.delete(item.id) }
                                .font(.caption)
                        }.listRowBackground(Color.white.opacity(0.04))
                    }
                }.scrollContentBackground(.hidden)
            }
            Text(memoryVault.status).font(.caption).foregroundStyle(.secondary)
            Spacer(minLength: 0)
        }.padding(22)
    }

    private var guardView: some View {
        infoPage(icon: "shield.lefthalf.filled", title: "Device protection", detail: "AURA Guard uses runtime capability discovery. Device management, recovery, check-ins and protection features will be evaluated against the current device, account, configured entitlements and applicable agreements.")
    }

    private var settings: some View {
        Form {
            Section("Local AI engine") {
                TextField("http://127.0.0.1:8080", text: $assistant.endpoint)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.URL)
                SecureField("Optional local model API key", text: $assistant.apiKey)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Button("Test local connection") { Task { await assistant.checkHealth() } }
                Text(assistant.status).font(.caption).foregroundStyle(.secondary)
            }
            Section("Privacy") {
                Text("Chat requests are sent to the local inference URL configured above. For a computer-hosted model, use its reachable local-network address and configure the inference server to accept connections from your phone. If you enable server API-key authentication, enter the key above; it is kept in memory only and is not written to app preferences.")
                Text("Encrypted memory and recovery-service connection will be enabled after their native adapters are integrated.")
            }
        }.scrollContentBackground(.hidden)
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
