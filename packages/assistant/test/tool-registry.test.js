import test from "node:test";
import assert from "node:assert/strict";
import {AssistantSession} from "../src/session.js";

test("assistant delegates tools to the configured confirmation-gated registry",async()=>{
 const calls=[];
 const registry={execute:async(name,input,context)=>{calls.push({name,input,context});return {ok:true,output:"done"};}};
 const session=new AssistantSession({inference:{chat:async()=>({content:"ok"})},toolRegistry:registry});
 const result=await session.executeTool("device.lock",{deviceId:"device-123456"},{source:"user-ui"});
 assert.equal(result.output,"done");
 assert.equal(calls[0].name,"device.lock");
 assert.equal(calls[0].context.source,"user-ui");
});
test("assistant cannot execute tools when no registry is configured",async()=>{
 const session=new AssistantSession({inference:{chat:async()=>({content:"ok"})}});
 await assert.rejects(()=>session.executeTool("device.lock",{}),/No tool registry/);
});
