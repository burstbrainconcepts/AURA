import "./style.css";

const app = document.querySelector("#app");
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
      <header><div><p class="eyebrow">PERSONAL INTELLIGENCE</p><h1 id="heading">Your assistant</h1></div><span class="status"><i></i> Desktop preview</span></header>
      <section class="welcome" id="welcome">
        <div class="hero-orb">A</div>
        <p class="eyebrow">YOUR SPACE. YOUR CONTROL.</p>
        <h2>Think clearly.<br><em>Move confidently.</em></h2>
        <p class="muted">AURA is being built around private, local-first intelligence and user-controlled device protection.</p>
      </section>
      <section class="panel" id="panel">
        <div class="panel-head"><div><h3>Start a conversation</h3><p class="muted">The desktop interface is ready for the local inference runtime.</p></div><span class="tag">Runtime not connected</span></div>
        <div class="messages" id="messages"><p class="empty">Your conversations will appear here.</p></div>
        <form id="composer"><input id="prompt" autocomplete="off" placeholder="Ask AURA anything..." aria-label="Message AURA" /><button type="submit">Send <span>↗</span></button></form>
        <p class="hint">Messages are not sent to a cloud service by this preview interface.</p>
      </section>
      <footer> AURA · Private by design · Platform capabilities depend on your device permissions </footer>
    </section>
  </main>`;

const heading = document.querySelector("#heading");
const panel = document.querySelector("#panel");
const welcome = document.querySelector("#welcome");
const messages = document.querySelector("#messages");
const labels = { assistant: "Your assistant", memory: "Private memory", guard: "Device Guard", settings: "Settings" };
document.querySelectorAll(".nav").forEach((button) => button.addEventListener("click", () => {
  document.querySelectorAll(".nav").forEach((item) => item.classList.toggle("active", item === button));
  const view = button.dataset.view;
  heading.textContent = labels[view];
  welcome.hidden = view !== "assistant";
  panel.hidden = view !== "assistant";
  if (view !== "assistant") {
    panel.hidden = false;
    panel.innerHTML = view === "guard"
      ? '<div class="panel-head"><div><h3>Device protection</h3><p class="muted">Manage enrolled devices and recovery requests.</p></div><span class="tag">Recovery service required</span></div><div class="notice">Remote actions are only available to enrolled devices, require explicit approval, and may be limited by the operating system.</div>'
      : view === "memory"
      ? '<div class="panel-head"><div><h3>Your memory stays yours</h3><p class="muted">Opt-in memory controls will be connected to the encrypted platform store.</p></div><span class="tag">Not enabled</span></div><div class="notice">No conversation memory is stored by this desktop preview.</div>'
      : '<div class="panel-head"><div><h3>Application settings</h3><p class="muted">Runtime, privacy and platform settings.</p></div></div><div class="notice">Local model and voice configuration will appear here when their adapters are integrated.</div>';
  } else {
    panel.innerHTML = '<div class="panel-head"><div><h3>Start a conversation</h3><p class="muted">The desktop interface is ready for the local inference runtime.</p></div><span class="tag">Runtime not connected</span></div><div class="messages" id="messages"><p class="empty">Your conversations will appear here.</p></div><form id="composer"><input id="prompt" autocomplete="off" placeholder="Ask AURA anything..." aria-label="Message AURA" /><button type="submit">Send <span>↗</span></button></form><p class="hint">Messages are not sent to a cloud service by this preview interface.</p>';
    bindComposer();
  }
}));
function bindComposer() {
  document.querySelector("#composer")?.addEventListener("submit", (event) => {
    event.preventDefault();
    const input = document.querySelector("#prompt");
    const value = input.value.trim();
    if (!value) return;
    const bubble = document.createElement("p");
    bubble.className = "user-message";
    bubble.textContent = value;
    document.querySelector("#messages").append(bubble);
    input.value = "";
    const note = document.createElement("p");
    note.className = "empty";
    note.textContent = "Connect the AURA local runtime to generate a response. Your message has not left this interface.";
    document.querySelector("#messages").append(note);
  });
}
bindComposer();
