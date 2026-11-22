import test from "node:test";
import assert from "node:assert/strict";
import {generateKeyPairSync} from "node:crypto";
import {DeviceCommandAuthority} from "../src/index.js";

const keys=generateKeyPairSync("ed25519");
const publicKeyPem=keys.publicKey.export({type:"spki",format:"pem"});
const privateKeyPem=keys.privateKey.export({type:"pkcs8",format:"pem"});
const setup=()=>{let now=Date.parse("2026-11-22T12:00:00Z");const authority=new DeviceCommandAuthority({clock:()=>now});authority.registerDevice({deviceId:"device-123456",publicKeyPem,capabilities:["device.lock.supported"]});return {authority,now:()=>now,setNow:v=>now=v};};

test("signed commands verify once and reject replay",()=>{
 const {authority}=setup();
 const signed=authority.issueCommand({deviceId:"device-123456",command:"recovery.checkin",requestedBy:"account-123456",privateKeyPem});
 assert.equal(authority.verifyAndConsume(signed).valid,true);
 assert.deepEqual(authority.verifyAndConsume(signed),{valid:false,reason:"replay_detected"});
});
test("high impact commands require explicit user approval before signing",()=>{
 const {authority}=setup();
 assert.throws(()=>authority.issueCommand({deviceId:"device-123456",command:"device.lock",requestedBy:"account-123456",privateKeyPem}),/approval/);
 const signed=authority.issueCommand({deviceId:"device-123456",command:"device.lock",requestedBy:"account-123456",privateKeyPem,userApproved:true});
 assert.equal(authority.verifyAndConsume(signed).valid,true);
});
test("tampered and expired commands are rejected",()=>{
 const {authority,setNow}=setup();
 const signed=authority.issueCommand({deviceId:"device-123456",command:"recovery.checkin",requestedBy:"account-123456",privateKeyPem});
 const tampered={...signed,envelope:{...signed.envelope,command:"device.lock"}};
 assert.equal(authority.verifyAndConsume(tampered).reason,"signature_invalid");
 setNow(Date.parse(signed.envelope.expiresAt)+1);
 assert.equal(authority.verifyAndConsume(signed).reason,"invalid_envelope");
});
test("unregistered devices cannot verify commands",()=>{
 const {authority}=setup();
 assert.equal(authority.verifyAndConsume({envelope:{deviceId:"unknown-123"},algorithm:"Ed25519",signature:"x"}).reason,"device_not_registered");
});
