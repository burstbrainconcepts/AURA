const NAME = /^[a-z][a-z0-9_.-]{1,63}$/;

export class ToolRegistry {
  #tools = new Map();
  #confirm;
  constructor({confirm = async () => false} = {}) {
    if (typeof confirm !== "function") throw new TypeError("confirm must be a function.");
    this.#confirm = confirm;
  }
  register({name,description="",schema,handler,requiresConfirmation=true}) {
    if (typeof name !== "string" || !NAME.test(name) || this.#tools.has(name)) throw new TypeError("Invalid or duplicate tool name.");
    if (!schema || typeof schema.validate !== "function" || typeof handler !== "function") throw new TypeError("Tool schema.validate and handler are required.");
    this.#tools.set(name,{name,description,schema,handler,requiresConfirmation:Boolean(requiresConfirmation)});
    return this;
  }
  list() { return [...this.#tools.values()].map(({name,description,requiresConfirmation})=>({name,description,requiresConfirmation})); }
  async execute(name,input,context={}) {
    const tool=this.#tools.get(name);
    if (!tool) throw new Error("Unknown tool: "+name);
    const validation=tool.schema.validate(input);
    if (validation !== true) throw new TypeError(typeof validation==="string"?validation:"Tool input failed validation.");
    if (tool.requiresConfirmation && !(await this.#confirm({name,description:tool.description,input,context}))) {
      return {ok:false,denied:true,reason:"User confirmation was not granted."};
    }
    const output=await tool.handler(structuredClone(input),context);
    return {ok:true,output};
  }
}
