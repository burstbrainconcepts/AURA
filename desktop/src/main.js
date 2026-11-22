import { invoke } from "@tauri-apps/api/core";
import "./style.css";

const app = document.querySelector("#app");
const DEFAULT_ENDPOINT = "http://127.0.0.1:8080";
let endpoint = localStorage.getItem("aura.inference.endpoint") || DEFAULT_ENDPOINT;
let history = [];
let busy = false;

app.innerHTML = `
  <main class="shell">
    <aside class="rail">
      <div class="brand"><span class="orb">A</span><span>AURA</span></div>
      <nav aria-label="Main navigation">
        <button class="nav active" data-view="assistant">◈ <span>Assistant</span></button>
        <button class="nav" data-view="memory">▤ <span>Memory</span></button>
        <button class="nav" data-view="guard">⬡ <span>Device Guard</span></button>
        <button class="nav" data-view="settings">⚙ <span>Settings</span></button>
      </nav>
      <div class="rail-foot"><span class="pulse"></span> Local-first workspace</div>
    </aside>
    <section class="content">
      <header><div><p class="eyebrow">PERSONAL INTELLIGENCE</p><h1 id="heading">Your assistant</h1></div><span class="status" id="engine-status"><i></i> Checking local engine</span></header>
      <section class="welcome" id="welcome">
        <div class="hero-orb">A</div>
        <p class="eyebrow">YOUR SPACE. YOUR CONTROL.</p>
        <h2>Think clearly.<br><em>Move confidently.</em></h2>
        <p class="muted">Private, local-first intelligence. Your prompts go to the local inference endpoint you configure.</p>
      </section>
      <section class="panel" id="panel">
        <div class="panel-head"><div><h3>Start a conversation</h3><p class="muted">Connect a local llama.cpp server to begin.</p></div><span class="tag" id="runtime-tag">Not connected</span></div>
        <div class="messages" id="messages"><p class="empty">Your conversation will appear here.</p></div>
        <form id="composer"><input id="prompt" autocomplete="off" placeholder="Ask AURA anything..." aria-label="Message AURA" /><button type="submit" id="send">Send <span>↗</span></button></form>
        <p class="hint" id="hint">Default endpoint: http://127.0.0.1:8080</p>
      </section>
      <footer>AURA · Private by design · Local runtime requests stay on this device</footer>
    </section>
  </main>`;

const heading = document.querySelector("#heading");
const panel = document.querySelector("#panel");
const welcome = document.querySelector("#welcome");
const messagesEl = document.querySelector("#messages");
const statusEl = document.querySelector("#engine-status");
const tagEl = document.querySelector("#runtime-tag");
const labels = { assistant: "Your assistant", memory: "Private memory", guard: "Device Guard", settings: "Settings" };

function setStatus(online, detail = "") {
  statusEl.innerHTML = online ? "<i></i> Local engine connected" : "<i></i> Local engine offline";
  statusEl.title = detail;
  tagEl.textContent = online ? "Connected" : "Not connected";
}
function appendMessage(role, content) {
  const item = document.createElement("p");
  item.className = role === "user" ? "user-message" : "assistant-message";
  item.textContent = content;
  messagesEl.append(item);
  messagesEl.scrollTop = messagesEl.scrollHeight;
}
async function checkEngine() {
  try {
    const online = await invoke("local_health", { baseUrl: endpoint });
    setStatus(Boolean(online));
    return Boolean(online);
  } catch (error) {
    setStatus(false, String(error));
    return false;
  }
}
async function sendMessage(value) {
  if (busy) return;
  busy = true;
  document.querySelector("#send").disabled = true;
  appendMessage("user", value);
  history.push({ role: "user", content: value });
  const pending = document.createElement("p");
  pending.className = "empty";
  pending.textContent = "AURA is thinking locally…";
  messagesEl.append(pending);
  try {
    const answer = await invoke("local_chat", { baseUrl: endpoint, messages: history.slice(-40) });
    pending.remove();
    history.push({ role: "assistant", content: answer });
    appendMessage("assistant", answer);
    setStatus(true);
  } catch (error) {
    pending.remove();
    history.pop();
    appendMessage("assistant", `I couldn't reach the local model: ${String(error)}. Check the endpoint in Settings and make sure llama.cpp server is running.`);
    setStatus(false, String(error));
  } finally {
    busy = false;
    document.querySelector("#send").disabled = false;
  }
}
function showAssistant() {
  panel.innerHTML = `
    <div class="panel-head"><div><h3>Start a conversation</h3><p class="muted">Your prompts are sent to your configured local llama.cpp server.</p></div><span class="tag" id="runtime-tag">${tagEl.textContent}</span></div>
    <div class="messages" id="messages"><p class="empty">Your conversation will appear here.</p></div>
    <form id="composer"><input id="prompt" autocomplete="off" placeholder="Ask AURA anything..." aria-label="Message AURA" /><button type="submit" id="send">Send <span>↗</span></button></form>
    <p class="hint">Local endpoint: ${endpoint}</p>`;
  const input = document.querySelector("#prompt");
  document.querySelector("#composer").addEventListener("submit", (event) => {
    event.preventDefault();
    const value = input.value.trim();
    if (!value) return;
    input.value = "";
    sendMessage(value);
  });
  messagesEl.replaceChildren();
  if (!history.length) {
    const empty = document.createElement("p");
    empty.className = "empty";
    empty.textContent = "Your conversation will appear here.";
    messagesEl.append(empty);
  } else {
    history.forEach((message) => appendMessage(message.role, message.content));
  }
}
function showPanel(view) {
  if (view === "assistant") {
    showAssistant();
    return;
  }
  if (view === "settings") {
    panel.innerHTML = `
      <div class="panel-head"><div><h3>Local AI runtime</h3><p class="muted">Configure the llama.cpp OpenAI-compatible server running on this computer.</p></div></div>
      <form id="settings-form" class="settings-form">
        <label for="endpoint">Inference server URL</label>
        <input id="endpoint" value="${endpoint}" spellcheck="false" autocomplete="url" />
        <div class="settings-actions"><button type="submit">Save endpoint</button><button type="button" id="test-endpoint" class="secondary">Test connection</button></div>
        <p class="hint" id="settings-result">For security, the desktop native bridge currently accepts localhost endpoints only.</p>
      </form>`;
    document.querySelector("#settings-form").addEventListener("submit", async (event) => {
      event.preventDefault();
      const next = document.querySelector("#endpoint").value.trim();
      try {
        const parsed = new URL(next);
        if (!["http:", "https:"].includes(parsed.protocol) || !["localhost", "127.0.0.1", "[::1]"].includes(parsed.hostname)) throw new Error("Use a localhost URL such as http://127.0.0.1:8080");
        endpoint = next.replace(/\/$/, "");
        localStorage.setItem("aura.inference.endpoint", endpoint);
        document.querySelector("#settings-result").textContent = "Endpoint saved.";
        await checkEngine();
      } catch (error) {
        document.querySelector("#settings-result").textContent = String(error);
      }
    });
    document.querySelector("#test-endpoint").addEventListener("click", async () => {
      const result = document.querySelector("#settings-result");
      result.textContent = await checkEngine() ? "Local inference service is reachable." : "Could not reach the local service. Check that it is running.";
    });
    return;
  }
  if (view === "memory") {
    panel.innerHTML = '<div class="panel-head"><div><h3>Your memory stays yours</h3><p class="muted">Opt-in encrypted memory is the next native storage integration.</p></div><span class="tag">Not enabled</span></div><div class="notice">This desktop preview does not persist conversation memory. Memory will only be enabled after the encrypted native vault is connected and you explicitly opt in.</div>';
    return;
  }
  panel.innerHTML = '<div class="panel-head"><div><h3>Device protection</h3><p class="muted">Connect AURA Guard to your recovery service.</p></div><span class="tag">Service not connected</span></div><div class="notice">The shared recovery API supports device enrollment, signed command queues, acknowledgements and audit events. Connect the deployed recovery service to manage enrolled devices. Capability availability and approval requirements are discovered per platform and deployment.</div>';
}

document.querySelectorAll(".nav").forEach((button) => button.addEventListener("click", () => {
  document.querySelectorAll(".nav").forEach((item) => item.classList.toggle("active", item === button));
  const view = button.dataset.view;
  heading.textContent = labels[view];
  welcome.hidden = view !== "assistant";
  showPanel(view);
}));
showAssistant();
checkEngine();
