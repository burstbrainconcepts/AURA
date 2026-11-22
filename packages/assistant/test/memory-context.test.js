import test from "node:test";
import assert from "node:assert/strict";
import {AssistantSession} from "../src/session.js";
import {MemoryStore} from "../../memory/src/index.js";

test("assistant uses saved memory only when memory is explicitly enabled",async()=>{
 const memory=new MemoryStore();
 memory.add;
 const requests=[];
 const session=new AssistantSession({memory,inference:{chat:async input=>{requests.push(input);return {content:"ok"};}}});
 await session.send("hello");
 assert.equal(requests[0].messages[0].content.includes("saved context"),false);
 memory.setEnabled(true);
 memory.add({text:"Prefers concise explanations",category:"preference"});
 await session.send("hello again");
 assert.equal(requests[1].messages[0].content.includes("Prefers concise explanations"),true);
});
