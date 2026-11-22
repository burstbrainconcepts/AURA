import test from "node:test";
import assert from "node:assert/strict";
import {ToolRegistry} from "../src/index.js";

test("unknown tools and invalid inputs are rejected",async()=>{
 const tools=new ToolRegistry();
 await assert.rejects(()=>tools.execute("device.lock",{}),/Unknown tool/);
 tools.register({name:"device.lock",schema:{validate:x=>x.deviceId?"": "deviceId required"},handler:async()=>true});
 await assert.rejects(()=>tools.execute("device.lock",{}),/deviceId required/);
});
test("mutating tools require explicit confirmation",async()=>{
 const denied=new ToolRegistry();
 denied.register({name:"device.lock",schema:{validate:()=>true},handler:async()=> "locked"});
 assert.deepEqual(await denied.execute("device.lock",{}),{ok:false,denied:true,reason:"User confirmation was not granted."});
 const approved=new ToolRegistry({confirm:async()=>true});
 approved.register({name:"device.lock",schema:{validate:()=>true},handler:async()=> "locked"});
 assert.deepEqual(await approved.execute("device.lock",{}),{ok:true,output:"locked"});
});
