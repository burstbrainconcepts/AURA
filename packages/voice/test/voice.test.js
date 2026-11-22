import test from "node:test";
import assert from "node:assert/strict";
import {SpeechToTextAdapter,TextToSpeechAdapter,WakeWordAdapter} from "../src/index.js";

test("STT validates audio and normalizes transcript", async()=>{
 const adapter=new SpeechToTextAdapter({transcribe:async()=>({text:" hello "})});
 assert.equal((await adapter.transcribe({audio:new Uint8Array([1])})).text,"hello");
 await assert.rejects(()=>adapter.transcribe({audio:new Uint8Array()}),/audio/);
});
test("TTS validates text and requires audio bytes",async()=>{
 const adapter=new TextToSpeechAdapter({synthesize:async()=>new Uint8Array([1,2])});
 assert.equal((await adapter.synthesize({text:"hello"})).byteLength,2);
 await assert.rejects(()=>adapter.synthesize({text:" "}),/text/);
});
test("wake word delegates explicit start and stop",()=>{
 const calls=[];const adapter=new WakeWordAdapter({start:fn=>{calls.push("start");return fn},stop:()=>calls.push("stop")});
 adapter.start(()=>{});adapter.stop();assert.deepEqual(calls,["start","stop"]);
});
