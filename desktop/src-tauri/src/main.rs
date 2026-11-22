#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use reqwest::Url;
use serde::{Deserialize, Serialize};
use std::time::Duration;

fn validate_local_url(value: &str) -> Result<Url, String> {
    let url = Url::parse(value).map_err(|_| "Enter a valid local inference URL".to_string())?;
    if url.scheme() != "http" && url.scheme() != "https" {
        return Err("Inference URL must use HTTP or HTTPS".into());
    }
    if !url.username().is_empty() || url.password().is_some() {
        return Err("Do not place credentials in the inference URL".into());
    }
    let host = url.host_str().unwrap_or_default();
    let local = host == "localhost" || host == "127.0.0.1" || host == "[::1]" || host == "::1";
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

#[derive(Serialize, Deserialize)]
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
        .invoke_handler(tauri::generate_handler![local_health, local_chat])
        .run(tauri::generate_context!())
        .expect("error while running AURA desktop");
}
