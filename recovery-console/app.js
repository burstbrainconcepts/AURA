const tokenInput = document.querySelector("#account-token");
const connectButton = document.querySelector("#connect");
const disconnectButton = document.querySelector("#disconnect");
const refreshButton = document.querySelector("#refresh");
const statusNode = document.querySelector("#status");
const devicesNode = document.querySelector("#devices");
let accountToken = "";

function setStatus(message, error = false) {
  statusNode.textContent = message;
  statusNode.classList.toggle("error", error);
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: {
      "Accept": "application/json",
      "Authorization": `Bearer ${accountToken}`,
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...(options.headers || {})
    },
    cache: "no-store",
    credentials: "same-origin"
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(payload.error || `Request failed (${response.status})`);
  return payload;
}

function addText(parent, tag, text, className) {
  const node = document.createElement(tag);
  node.textContent = text;
  if (className) node.className = className;
  parent.append(node);
  return node;
}

function addAction(parent, label, className, action) {
  const button = document.createElement("button");
  button.type = "button";
  button.textContent = label;
  button.className = className;
  button.addEventListener("click", action);
  parent.append(button);
  return button;
}

function formatDate(value) {
  if (!value) return "Never";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Unknown" : date.toLocaleString();
}

function renderDevices(devices) {
  devicesNode.replaceChildren();
  if (!devices.length) {
    addText(devicesNode, "div", "No devices are enrolled yet. Link AURA from an Android device to get started.", "empty");
    return;
  }

  for (const device of devices) {
    const card = document.createElement("article");
    card.className = "device-card";
    addText(card, "h3", device.label || device.id);
    addText(card, "div", `${device.platform || "device"} · ${device.id}`, "device-meta");
    addText(card, "div", `Last check-in: ${formatDate(device.last_seen_at)}`, "device-meta");
    addText(card, "div", device.revoked_at ? `Revoked: ${formatDate(device.revoked_at)}` : "Status: enrolled", "device-meta");

    const caps = document.createElement("div");
    caps.className = "capabilities";
    for (const capability of (Array.isArray(device.capabilities) ? device.capabilities : [])) {
      addText(caps, "span", capability, "capability");
    }
    card.append(caps);

    const location = document.createElement("div");
    location.className = "location";
    if (Number.isFinite(Number(device.latitude)) && Number.isFinite(Number(device.longitude)) && device.location_updated_at) {
      const lat = Number(device.latitude);
      const lon = Number(device.longitude);
      addText(location, "div", `Last reported location · ±${Math.round(Number(device.location_accuracy_m || 0))} m`);
      addText(location, "div", formatDate(device.location_updated_at));
      const mapLink = document.createElement("a");
      mapLink.href = `https://www.openstreetmap.org/?mlat=${encodeURIComponent(lat)}&mlon=${encodeURIComponent(lon)}#map=16/${encodeURIComponent(lat)}/${encodeURIComponent(lon)}`;
      mapLink.target = "_blank";
      mapLink.rel = "noopener noreferrer";
      mapLink.textContent = "Open location map";
      location.append(mapLink);
    } else {
      addText(location, "div", "No location has been reported. Location sharing is optional and off by default.");
    }
    card.append(location);

    const actions = document.createElement("div");
    actions.className = "actions";
    const capabilities = Array.isArray(device.capabilities) ? device.capabilities : [];
    if (!device.revoked_at && capabilities.includes("device.lock.supported")) {
      addAction(actions, "Lock device", "danger", () => lockDevice(device));
    }
    if (!device.revoked_at) {
      addAction(actions, "Revoke device", "secondary", () => revokeDevice(device));
    }
    if (device.location_updated_at) {
      addAction(actions, "Clear location", "secondary", () => clearLocation(device));
    }
    card.append(actions);
    devicesNode.append(card);
  }
}

async function loadDevices() {
  setStatus("Loading your device registry...");
  try {
    const payload = await api("/v1/devices");
    renderDevices(Array.isArray(payload.devices) ? payload.devices : []);
    setStatus(`${payload.devices?.length || 0} enrolled device(s) loaded.`);
  } catch (error) {
    setStatus(error.message, true);
  }
}

async function lockDevice(device) {
  const confirmed = window.confirm(`Send a signed screen-lock command to ${device.label || device.id}? The device must be online and have AURA's Android lock permission enabled.`);
  if (!confirmed) return;
  setStatus("Issuing signed lock command...");
  try {
    const result = await api("/v1/commands", {
      method: "POST",
      body: JSON.stringify({ deviceId: device.id, command: "device.lock", userApproved: true, ttlMs: 300000 })
    });
    setStatus(`Lock command queued. It expires at ${formatDate(result.expiresAt)}.`);
  } catch (error) {
    setStatus(error.message, true);
  }
}

async function revokeDevice(device) {
  if (!window.confirm(`Revoke ${device.label || device.id}? This invalidates its recovery token.`)) return;
  try {
    await api(`/v1/devices/${encodeURIComponent(device.id)}/revoke`, { method: "POST", body: "{}" });
    setStatus("Device access revoked.");
    await loadDevices();
  } catch (error) {
    setStatus(error.message, true);
  }
}

async function clearLocation(device) {
  if (!window.confirm(`Clear the last saved location for ${device.label || device.id}?`)) return;
  try {
    await api(`/v1/devices/${encodeURIComponent(device.id)}/location/clear`, { method: "POST", body: "{}" });
    setStatus("Saved location cleared.");
    await loadDevices();
  } catch (error) {
    setStatus(error.message, true);
  }
}

connectButton.addEventListener("click", async () => {
  const candidate = tokenInput.value.trim();
  if (candidate.length < 32) {
    setStatus("Enter a valid account API token.", true);
    return;
  }
  accountToken = candidate;
  tokenInput.value = "";
  connectButton.disabled = true;
  disconnectButton.disabled = false;
  refreshButton.disabled = false;
  await loadDevices();
  connectButton.disabled = false;
});

disconnectButton.addEventListener("click", () => {
  accountToken = "";
  devicesNode.replaceChildren();
  disconnectButton.disabled = true;
  refreshButton.disabled = true;
  setStatus("Disconnected. The account token has been cleared from this page.");
});

refreshButton.addEventListener("click", loadDevices);
