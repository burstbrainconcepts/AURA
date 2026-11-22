import test from "node:test";
import assert from "node:assert/strict";
import { MemoryStore } from "../src/index.js";

test("memory is disabled by default and requires explicit opt-in", () => {
  const memory = new MemoryStore();
  assert.equal(memory.enabled, false);
  assert.throws(() => memory.add({text:"remember this"}), /disabled/);
  memory.setEnabled(true);
  assert.equal(memory.add({text:"remember this"}).text, "remember this");
});

test("list, category filter, delete, export and clear work", () => {
  const memory = new MemoryStore({enabled:true,clock:()=>0});
  const a = memory.add({text:"first",category:"preference"});
  memory.add({text:"second",category:"project"});
  assert.equal(memory.list().length, 2);
  assert.equal(memory.list({category:"preference"}).length, 1);
  assert.equal(memory.export().items.length, 2);
  assert.equal(memory.delete(a.id), true);
  assert.equal(memory.clear(), 1);
});

test("disabling memory clears stored content", () => {
  const memory = new MemoryStore({enabled:true});
  memory.add({text:"sensitive"});
  memory.setEnabled(false);
  assert.deepEqual(memory.list(), []);
});
