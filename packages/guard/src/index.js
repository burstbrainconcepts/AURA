import { createPrivateKey, createPublicKey, sign, verify, randomUUID, randomBytes } from "node:crypto";
import { createCommandEnvelope, CAPABILITIES } from "@aura/core";

const APPROVAL_REQUIRED = new Set(["device.lock", "location.refresh"]);
const REQUIRED_CAPABILITY = Object.freeze({
  "session.revoke": "device.session.revoke",
  "device.lock": "device.lock.supported",
  "location.refresh": "device.location.user_consented",
  "recovery.checkin": "device.recovery.queue"
});
const canonical = value => JSON.stringify({
  version:value.version, commandId:value.commandId, deviceId:value.deviceId,
  command:value.command, issuedAt:value.issuedAt, expiresAt:value.expiresAt,
  nonce:value.nonce, requestedBy:value.requestedBy
});

export class DeviceCommandAuthority {
  #devices = new Map();
  #usedNonces = new Map();
  #clock;
  constructor({clock=()=>Date.now()}={}) { this.#clock=clock; }

  registerDevice({deviceId,publicKeyPem,capabilities=[]}) {
    if(typeof deviceId!=="string" || deviceId.length<8) throw new TypeError("Invalid deviceId.");
    if (!Array.isArray(capabilities) || capabilities.some(value => !CAPABILITIES.includes(value))) {
      throw new TypeError("Device capabilities must come from the AURA capability allowlist.");
    }
    const publicKey=createPublicKey(publicKeyPem);
    if(publicKey.asymmetricKeyType!=="ed25519") throw new TypeError("Device key must be Ed25519.");
    this.#devices.set(deviceId,{publicKey,capabilities:new Set(capabilities)});
    return {deviceId,registered:true};
  }

  revokeDevice(deviceId) { return this.#devices.delete(deviceId); }

  issueCommand({deviceId,command,requestedBy,privateKeyPem,ttlMs=5*60*1000,userApproved=false}) {
    const device = this.#devices.get(deviceId);
    if(!device) throw new Error("Device is not registered.");
    const requiredCapability = REQUIRED_CAPABILITY[command];
    if (!requiredCapability || !device.capabilities.has(requiredCapability)) {
      throw new Error("Device does not have the capability required for this command.");
    }
    if(APPROVAL_REQUIRED.has(command) && userApproved!==true) throw new Error("Explicit user approval is required before issuing this command.");
    const now=this.#clock();
    const envelope=createCommandEnvelope({
      commandId:randomUUID(),deviceId,command,
      issuedAt:new Date(now).toISOString(),expiresAt:new Date(now+ttlMs).toISOString(),
      nonce:randomBytes(24).toString("base64url"),requestedBy
    },now);
    const privateKey=createPrivateKey(privateKeyPem);
    if(privateKey.asymmetricKeyType!=="ed25519") throw new TypeError("Signing key must be Ed25519.");
    const signature=sign(null,Buffer.from(canonical(envelope)),privateKey).toString("base64");
    return Object.freeze({envelope,signature,algorithm:"Ed25519"});
  }

  verifyAndConsume(signedCommand) {
    const envelope=signedCommand?.envelope;
    const device=this.#devices.get(envelope?.deviceId);
    if(!device) return {valid:false,reason:"device_not_registered"};
    if(signedCommand.algorithm!=="Ed25519" || typeof signedCommand.signature!=="string") return {valid:false,reason:"invalid_signature_format"};
    let normalized;
    try { normalized=createCommandEnvelope(envelope,this.#clock()); }
    catch(error) { return {valid:false,reason:"invalid_envelope",detail:error.message}; }
    const ok=verify(null,Buffer.from(canonical(normalized)),device.publicKey,Buffer.from(signedCommand.signature,"base64"));
    if(!ok) return {valid:false,reason:"signature_invalid"};
    const requiredCapability = REQUIRED_CAPABILITY[normalized.command];
    if (!requiredCapability || !device.capabilities.has(requiredCapability)) {
      return {valid:false,reason:"capability_not_granted"};
    }
    const now=this.#clock();
    for(const [nonce,expiry] of this.#usedNonces) if(expiry<=now) this.#usedNonces.delete(nonce);
    const replayKey=normalized.deviceId+":"+normalized.nonce;
    if(this.#usedNonces.has(replayKey)) return {valid:false,reason:"replay_detected"};
    this.#usedNonces.set(replayKey,Date.parse(normalized.expiresAt));
    return {valid:true,envelope:normalized,deviceCapabilities:[...device.capabilities]};
  }
}
