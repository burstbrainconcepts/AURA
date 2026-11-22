import test from "node:test";
import assert from "node:assert/strict";
import {encryptMemorySnapshot,decryptMemorySnapshot} from "../src/vault.js";

test("memory snapshots round-trip through authenticated encryption",()=>{
 const snapshot={version:1,exportedAt:"2026-11-22T00:00:00.000Z",items:[{id:"a",text:"private"}]};
 const encrypted=encryptMemorySnapshot(snapshot,"a-long-private-passphrase");
 assert.equal(encrypted.format,"aura-memory-vault");
 assert.deepEqual(decryptMemorySnapshot(encrypted,"a-long-private-passphrase"),snapshot);
});
test("wrong passphrase and tampering fail closed",()=>{
 const encrypted=encryptMemorySnapshot({version:1,items:[]},"a-long-private-passphrase");
 assert.throws(()=>decryptMemorySnapshot(encrypted,"another-long-passphrase"),/Unable to decrypt/);
 const tampered={...encrypted,data:Buffer.from("tampered").toString("base64")};
 assert.throws(()=>decryptMemorySnapshot(tampered,"a-long-private-passphrase"),/Unable to decrypt/);
});
test("weak passphrases are rejected",()=>{
 assert.throws(()=>encryptMemorySnapshot({version:1,items:[]},"short"),/12 characters/);
});
