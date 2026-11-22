#include <jni.h>
#include <android/log.h>
#include <llama.h>
#include <algorithm>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#define AURA_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AURA", __VA_ARGS__)

namespace {
std::mutex g_mutex;
llama_model * g_model = nullptr;
llama_context * g_context = nullptr;
bool g_backend_initialized = false;

void release_model() {
    if (g_context) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

void throw_java(JNIEnv * env, const char * message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls) env->ThrowNew(cls, message);
}

std::string jstring_to_string(JNIEnv * env, jstring value) {
    if (!value) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_burstbrainconcepts_aura_NativeLlama_nativeLoadModel(
        JNIEnv * env, jobject, jstring model_path) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const std::string path = jstring_to_string(env, model_path);
    if (path.empty()) {
        throw_java(env, "Choose a valid GGUF model file.");
        return JNI_FALSE;
    }

    release_model();
    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // CPU-first; no GPU assumed on Android.
    g_model = llama_model_load_from_file(path.c_str(), model_params);
    if (!g_model) {
        throw_java(env, "AURA could not load this GGUF model. Check that the download is complete and valid.");
        return JNI_FALSE;
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = 2048;
    context_params.n_batch = 512;
    context_params.n_ubatch = 256;
    const unsigned int cores = std::max(2u, std::thread::hardware_concurrency());
    const int threads = static_cast<int>(std::min(4u, cores > 1 ? cores - 1 : 2u));
    context_params.n_threads = threads;
    context_params.n_threads_batch = threads;
    g_context = llama_init_from_model(g_model, context_params);
    if (!g_context) {
        release_model();
        throw_java(env, "AURA could not allocate the model context. Try a smaller model or close other apps.");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_burstbrainconcepts_aura_NativeLlama_nativeGenerate(
        JNIEnv * env, jobject, jstring prompt_value, jint max_tokens) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_context) {
        throw_java(env, "Load an offline model before sending messages.");
        return nullptr;
    }
    const std::string user_prompt = jstring_to_string(env, prompt_value);
    if (user_prompt.empty() || user_prompt.size() > 12000) {
        throw_java(env, "Prompt must contain between 1 and 12,000 characters.");
        return nullptr;
    }

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    const char * chat_template = llama_model_chat_template(g_model, nullptr);
    llama_chat_message messages[] = {
        {"system", "You are AURA, a helpful and honest private assistant. Be clear, useful and safe. Do not claim actions you have not performed."},
        {"user", user_prompt.c_str()}
    };

    std::string formatted;
    if (chat_template) {
        const int32_t required = llama_chat_apply_template(chat_template, messages, 2, true, nullptr, 0);
        if (required <= 0 || required > 24000) {
            throw_java(env, "The selected model has an unsupported chat template.");
            return nullptr;
        }
        std::vector<char> buffer(static_cast<size_t>(required) + 1);
        const int32_t written = llama_chat_apply_template(chat_template, messages, 2, true, buffer.data(), static_cast<int32_t>(buffer.size()));
        if (written < 0 || written > required) {
            throw_java(env, "Could not format the model chat prompt.");
            return nullptr;
        }
        formatted.assign(buffer.data(), static_cast<size_t>(written));
    } else {
        formatted = "### System\nYou are AURA, a helpful and honest private assistant.\n### User\n" + user_prompt + "\n### Assistant\n";
    }

    const int32_t token_count = -llama_tokenize(vocab, formatted.c_str(), static_cast<int32_t>(formatted.size()), nullptr, 0, true, true);
    if (token_count <= 0 || token_count > 1800) {
        throw_java(env, "Conversation is too long for the current 2K context. Start a new conversation.");
        return nullptr;
    }
    std::vector<llama_token> tokens(static_cast<size_t>(token_count));
    const int32_t tokenized = llama_tokenize(vocab, formatted.c_str(), static_cast<int32_t>(formatted.size()), tokens.data(), token_count, true, true);
    if (tokenized < 0) {
        throw_java(env, "Could not tokenize the prompt.");
        return nullptr;
    }

    llama_memory_clear(llama_get_memory(g_context), true);
    llama_batch_ext * batch = llama_batch_ext_init(g_context);
    if (!batch) {
        throw_java(env, "Could not allocate the inference batch.");
        return nullptr;
    }

    int32_t pos = 0;
    for (int32_t offset = 0; offset < tokenized; ) {
        llama_batch_ext_clear(batch);
        const int32_t count = std::min(512, tokenized - offset);
        for (int32_t i = 0; i < count; ++i) {
            const int32_t index = llama_batch_ext_add_token(batch, 0, tokens[static_cast<size_t>(offset + i)]);
            if (index < 0) {
                llama_batch_ext_free(batch);
                throw_java(env, "Could not prepare model input tokens.");
                return nullptr;
            }
            const llama_pos position = pos++;
            llama_batch_ext_set_pos(batch, index, &position);
            if (offset + i == tokenized - 1) llama_batch_ext_set_output_logits(batch, index, true);
        }
        if (llama_process(g_context, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) {
            llama_batch_ext_free(batch);
            throw_java(env, "The model failed while processing the prompt.");
            return nullptr;
        }
        offset += count;
    }

    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.90f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.70f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string output;
    const int32_t limit = std::max(1, std::min(512, static_cast<int>(max_tokens)));
    for (int32_t generated = 0; generated < limit; ++generated) {
        const llama_token next = llama_sampler_sample(sampler, g_context, -1);
        if (llama_vocab_is_eog(vocab, next)) break;
        llama_sampler_accept(sampler, next);

        char piece[512];
        const int32_t piece_size = llama_token_to_piece(vocab, next, piece, sizeof(piece), 0, true);
        if (piece_size > 0) output.append(piece, static_cast<size_t>(piece_size));

        llama_batch_ext_clear(batch);
        const int32_t index = llama_batch_ext_add_token(batch, 0, next);
        if (index < 0) break;
        const llama_pos position = pos++;
        llama_batch_ext_set_pos(batch, index, &position);
        llama_batch_ext_set_output_logits(batch, index, true);
        if (llama_process(g_context, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) break;
    }

    llama_sampler_free(sampler);
    llama_batch_ext_free(batch);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_burstbrainconcepts_aura_NativeLlama_nativeUnloadModel(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_model();
}
