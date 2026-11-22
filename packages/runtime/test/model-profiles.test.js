import test from "node:test";
import assert from "node:assert/strict";
import {MODEL_PROFILES,getModelProfile} from "../src/model-profiles.js";

test("CPU-friendly profiles are available and immutable",()=>{
 assert.ok(MODEL_PROFILES.some(profile=>profile.recommended && profile.role==="assistant"));
 assert.equal(getModelProfile("small-instruct-q4").quantization,"Q4_K_M");
 assert.equal(getModelProfile("missing"),null);
 assert.throws(()=>{MODEL_PROFILES.push({id:"x"});});
});
