#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use aes_gcm::{aead::{Aead, KeyInit}, Aes256Gcm, Nonce};
use pbkdf2::pbkdf2_hmac;
use rand::RngCore;
use reqwest::Url;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::Sha256;
use std::{fs, path::PathBuf, time::Duration};
use tauri::{AppHandle, Manager};

const VAULT_HEADER: &[u8; 8] = b"AURAMEM1";
const KDF_ITERATIONS: u32 = 310_000;

fn validate_local_url(value: &str) -> Result<Url, String> {
    let url = Url::parse(value).map_err(|_| "Enter a valid local inference URL".to_string())?;
    if url.scheme() != "http" && url.scheme() != "https" {
        return Err("Inference URL must use HTTP or HTTPS".into());
    }
    if !url.username().is_empty() || url.password().is_some() {
        return Err("Do not place credentials in the inference URL".into());
    }
    let host = url.host_str().unwrap_or_default();
    let local = host == "localhost" || host == "127.0.0.1" || host == "::1";
    if !local {
        return Err("For this desktop build, connect only to a local inference service".into());
    }
    Ok(url)
}

#[derive(Serialize)]
struct ChatRequest<'a> {
    model: &'a str,
    messages: &'a [ChatMessage],
    temperature: f32,
    max_tokens: u32,
    stream: bool,
}

#[derive(Serialize, Deserialize, Clone)]
struct ChatMessage {
    role: String,
    content: String,
}

#[derive(Deserialize)]
struct ChatResponse {
    choices: Vec<ChatChoice>,
}

#[derive(Deserialize)]
struct ChatChoice {
    message: ChatMessage,
}

#[derive(Serialize, Deserialize, Clone)]
struct MemoryItem {
    id: String,
    text: String,
    category: String,
    created_at: String,
}

fn vault_path(app: &AppHandle) -> Result<PathBuf, String> {
    let directory = app.path().app_data_dir().map_err(|e| e.to_string())?;
    fs::create_dir_all(&directory).map_err(|e| e.to_string())?;
    Ok(directory.join("memory.aesgcm"))
}

fn derive_vault_key(passphrase: &str, salt: &[u8]) -> Result<[u8; 32], String> {
    if passphrase.chars().count() < 12 || passphrase.len() > 1024 {
        return Err("Vault passphrase must contain 12–1024 characters".into());
    }
    let mut key = [0u8; 32];
    pbkdf2_hmac::<Sha256>(passphrase.as_bytes(), salt, KDF_ITERATIONS, &mut key);
    Ok(key)
}

fn validate_memory(items: &[MemoryItem]) -> Result<(), String> {
    if items.len() > 1000 {
        return Err("Memory vault cannot contain more than 1,000 items".into());
    }
    for item in items {
        if item.id.is_empty() || item.id.len() > 128 ||
           item.text.trim().is_empty() || item.text.chars().count() > 8000 ||
           item.category.len() > 80 || item.created_at.len() > 64 {
            return Err("Memory item failed validation".into());
        }
    }
    Ok(())
}

#[tauri::command]
fn memory_load(app: AppHandle, passphrase: String) -> Result<Vec<MemoryItem>, String> {
    let path = vault_path(&app)?;
    if !path.exists() { return Ok(Vec::new()); }
    let data = fs::read(path).map_err(|e| e.to_string())?;
    if data.len() < 8 + 16 + 12 + 16 || &data[..8] != VAULT_HEADER {
        return Err("Memory vault format is invalid".into());
    }
    let salt = &data[8..24];
    let nonce = Nonce::from_slice(&data[24..36]);
    let key = derive_vault_key(&passphrase, salt)?;
    let cipher = Aes256Gcm::new_from_slice(&key).map_err(|_| "Unable to initialize vault encryption".to_string())?;
    let plaintext = cipher.decrypt(nonce, &data[36..])
        .map_err(|_| "Unable to unlock vault. Check your passphrase or vault integrity.".to_string())?;
    let items: Vec<MemoryItem> = serde_json::from_slice(&plaintext)
        .map_err(|_| "Encrypted memory data is invalid".to_string())?;
    validate_memory(&items)?;
    Ok(items)
}

#[tauri::command]
fn memory_save(app: AppHandle, passphrase: String, items: Vec<MemoryItem>) -> Result<(), String> {
    validate_memory(&items)?;
    let path = vault_path(&app)?;
    let plaintext = serde_json::to_vec(&items).map_err(|e| e.to_string())?;
    let mut salt = [0u8; 16];
    let mut nonce_bytes = [0u8; 12];
    rand::rngs::OsRng.fill_bytes(&mut salt);
    rand::rngs::OsRng.fill_bytes(&mut nonce_bytes);
    let key = derive_vault_key(&passphrase, &salt)?;
    let cipher = Aes256Gcm::new_from_slice(&key).map_err(|_| "Unable to initialize vault encryption".to_string())?;
    let ciphertext = cipher.encrypt(Nonce::from_slice(&nonce_bytes), plaintext.as_ref())
        .map_err(|_| "Unable to encrypt memory vault".to_string())?;
    let mut output = Vec::with_capacity(36 + ciphertext.len());
    output.extend_from_slice(VAULT_HEADER);
    output.extend_from_slice(&salt);
    output.extend_from_slice(&nonce_bytes);
    output.extend_from_slice(&ciphertext);
    let temporary = path.with_extension(format!("aesgcm.tmp-{}", std::process::id()));
    fs::write(&temporary, output).map_err(|e| e.to_string())?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(&temporary, fs::Permissions::from_mode(0o600)).map_err(|e| e.to_string())?;
    }
    if path.exists() { fs::remove_file(&path).map_err(|e| e.to_string())?; }
    fs::rename(&temporary, &path).map_err(|e| e.to_string())?;
    Ok(())
}

#[tauri::command]
fn memory_clear(app: AppHandle) -> Result<(), String> {
    let path = vault_path(&app)?;
    if path.exists() { fs::remove_file(path).map_err(|e| e.to_string())?; }
    Ok(())
}


fn validate_recovery_url(value: &str) -> Result<Url, String> {
    let url = Url::parse(value).map_err(|_| "Enter a valid recovery service URL".to_string())?;
    let host = url.host_str().unwrap_or_default();
    let local_http = url.scheme() == "http" && (host == "localhost" || host == "127.0.0.1");
    if url.scheme() != "https" && !local_http {
        return Err("Recovery service must use HTTPS (HTTP is allowed only for localhost development)".into());
    }
    if !url.username().is_empty() || url.password().is_some() || url.query().is_some() || url.fragment().is_some() {
        return Err("Recovery URL must not contain credentials, query strings or fragments".into());
    }
    Ok(url)
}

fn validate_account_token(token: &str) -> Result<(), String> {
    if token.len() < 32 || token.len() > 4096 {
        return Err("Account token must contain at least 32 characters".into());
    }
    Ok(())
}

fn validate_device_id(device_id: &str) -> Result<(), String> {
    let valid = (8..=128).contains(&device_id.len()) &&
        device_id.bytes().all(|b| b.is_ascii_alphanumeric() || b"._:-".contains(&b));
    if !valid { return Err("Invalid device identifier".into()); }
    Ok(())
}

async fn recovery_response(request: reqwest::RequestBuilder) -> Result<Value, String> {
    let response = request.send().await.map_err(|e| format!("Recovery request failed: {e}"))?;
    let status = response.status();
    let data: Value = response.json().await.unwrap_or_else(|_| json!({}));
    if !status.is_success() {
        let detail = data.get("error").and_then(Value::as_str).unwrap_or("request_failed");
        return Err(format!("Recovery API returned HTTP {}: {}", status.as_u16(), detail));
    }
    Ok(data)
}

#[tauri::command]
async fn recovery_list_devices(base_url: String, account_token: String) -> Result<Value, String> {
    validate_account_token(&account_token)?;
    let mut url = validate_recovery_url(&base_url)?;
    url.set_path(&format!("{}/v1/devices", url.path().trim_end_matches('/')));
    let client = reqwest::Client::builder().timeout(Duration::from_secs(20)).build().map_err(|e| e.to_string())?;
    recovery_response(client.get(url).bearer_auth(account_token)).await
}

#[tauri::command]
async fn recovery_queue_command(base_url: String, account_token: String, device_id: String, command: String, user_approved: bool) -> Result<Value, String> {
    validate_account_token(&account_token)?;
    validate_device_id(&device_id)?;
    if !["device.lock", "location.refresh"].contains(&command.as_str()) {
        return Err("Unsupported recovery command".into());
    }
    if !user_approved { return Err("Explicit user approval is required".into()); }
    let mut url = validate_recovery_url(&base_url)?;
    url.set_path(&format!("{}/v1/commands", url.path().trim_end_matches('/')));
    let client = reqwest::Client::builder().timeout(Duration::from_secs(20)).build().map_err(|e| e.to_string())?;
    let body = json!({ "deviceId": device_id, "command": command, "userApproved": true, "ttlMs": 300000 });
    recovery_response(client.post(url).bearer_auth(account_token).json(&body)).await
}

#[tauri::command]
async fn recovery_revoke_device(base_url: String, account_token: String, device_id: String) -> Result<Value, String> {
    validate_account_token(&account_token)?;
    validate_device_id(&device_id)?;
    let mut url = validate_recovery_url(&base_url)?;
    url.set_path(&format!("{}/v1/devices/{}/revoke", url.path().trim_end_matches('/'), device_id));
    let client = reqwest::Client::builder().timeout(Duration::from_secs(20)).build().map_err(|e| e.to_string())?;
    recovery_response(client.post(url).bearer_auth(account_token)).await
}

#[tauri::command]
async fn recovery_clear_location(base_url: String, account_token: String, device_id: String) -> Result<Value, String> {
    validate_account_token(&account_token)?;
    validate_device_id(&device_id)?;
    let mut url = validate_recovery_url(&base_url)?;
    url.set_path(&format!("{}/v1/devices/{}/location/clear", url.path().trim_end_matches('/'), device_id));
    let client = reqwest::Client::builder().timeout(Duration::from_secs(20)).build().map_err(|e| e.to_string())?;
    recovery_response(client.post(url).bearer_auth(account_token)).await
}

#[tauri::command]
async fn local_health(base_url: String) -> Result<bool, String> {
    let mut url = validate_local_url(&base_url)?;
    url.set_path(&format!("{}/health", url.path().trim_end_matches('/')));
    let client = reqwest::Client::builder().timeout(Duration::from_secs(5)).build()
        .map_err(|e| e.to_string())?;
    let response = client.get(url).send().await.map_err(|e| format!("Local engine is not reachable: {e}"))?;
    Ok(response.status().is_success())
}

#[tauri::command]
async fn local_chat(base_url: String, messages: Vec<ChatMessage>) -> Result<String, String> {
    if messages.is_empty() || messages.len() > 80 {
        return Err("Conversation must contain between 1 and 80 messages".into());
    }
    if messages.iter().any(|m| !["system", "user", "assistant"].contains(&m.role.as_str()) || m.content.len() > 32_000) {
        return Err("Conversation contains an invalid message".into());
    }
    let mut url = validate_local_url(&base_url)?;
    url.set_path(&format!("{}/v1/chat/completions", url.path().trim_end_matches('/')));
    let client = reqwest::Client::builder().timeout(Duration::from_secs(120)).build()
        .map_err(|e| e.to_string())?;
    let body = ChatRequest { model: "local-model", messages: &messages, temperature: 0.7, max_tokens: 512, stream: false };
    let response = client.post(url).json(&body).send().await.map_err(|e| format!("Local inference request failed: {e}"))?;
    if !response.status().is_success() {
        return Err(format!("Local inference returned HTTP {}", response.status()));
    }
    let result: ChatResponse = response.json().await.map_err(|_| "Local inference returned an invalid response".to_string())?;
    result.choices.first().map(|choice| choice.message.content.clone())
        .ok_or_else(|| "Local inference returned no answer".into())
}

fn main() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![local_health, local_chat, memory_load, memory_save, memory_clear, recovery_list_devices, recovery_queue_command, recovery_revoke_device, recovery_clear_location])
        .run(tauri::generate_context!())
        .expect("error while running AURA desktop");
}
