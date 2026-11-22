/**
 * Hardware-conscious starter profiles. These describe compatible model
 * families, not bundled weights; users must obtain model files separately.
 */
export const MODEL_PROFILES = Object.freeze([
  Object.freeze({id:"small-instruct-q4",label:"Small instruct (Q4)",format:"gguf",quantization:"Q4_K_M",memoryClass:"8GB+",contextTokens:4096,role:"assistant",recommended:true}),
  Object.freeze({id:"balanced-instruct-q4",label:"Balanced instruct (Q4)",format:"gguf",quantization:"Q4_K_M",memoryClass:"16GB+",contextTokens:8192,role:"assistant",recommended:false}),
  Object.freeze({id:"tiny-transcriber",label:"Tiny speech recognition",format:"whisper.cpp",memoryClass:"4GB+",role:"speech-to-text",recommended:true})
]);

export function getModelProfile(id) {
  return MODEL_PROFILES.find(profile => profile.id === id) ?? null;
}
