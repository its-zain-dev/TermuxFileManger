package dev.itszain.termuxfilemanager;

import java.util.ArrayList;
import java.util.List;

/** Known API providers and defaults. Model identifiers remain editable because catalogs change frequently. */
public final class AiProvider {
    public final String id, name, endpoint, defaultModel, docs, type;
    private AiProvider(String id,String name,String endpoint,String model,String docs,String type){this.id=id;this.name=name;this.endpoint=endpoint;this.defaultModel=model;this.docs=docs;this.type=type;}
    private static final List<AiProvider> ALL = new ArrayList<>();
    static {
        ALL.add(new AiProvider("gemini","Google Gemini","https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent","gemini-3.8-flash","https://ai.google.dev/gemini-api/docs","gemini"));
        ALL.add(new AiProvider("anthropic","Anthropic Claude","https://api.anthropic.com/v1/messages","claude-sonnet-5-5","https://docs.anthropic.com/en/api/messages","anthropic"));
        ALL.add(new AiProvider("openai","OpenAI","https://api.openai.com/v1/chat/completions","gpt-4.1-mini","https://platform.openai.com/docs/api-reference/chat/create","openai"));
        ALL.add(new AiProvider("openrouter","OpenRouter","https://openrouter.ai/api/v1/chat/completions","openrouter/auto","https://openrouter.ai/docs/api-reference/chat-completion","openai"));
        ALL.add(new AiProvider("groq","Groq","https://api.groq.com/openai/v1/chat/completions","openai/gpt-oss-120b","https://console.groq.com/docs/api-reference","openai"));
        ALL.add(new AiProvider("mistral","Mistral AI","https://api.mistral.ai/v1/chat/completions","mistral-small-latest","https://docs.mistral.ai/api/endpoint/chat","openai"));
        ALL.add(new AiProvider("cohere","Cohere","https://api.cohere.com/v2/chat","command-a-plus-05-2026","https://docs.cohere.com/reference/chat","cohere"));
        ALL.add(new AiProvider("deepseek","DeepSeek","https://api.deepseek.com/chat/completions","deepseek-v4-pro","https://api-docs.deepseek.com/api/create-chat-completion","openai"));
        ALL.add(new AiProvider("xai","xAI","https://api.x.ai/v1/chat/completions","grok-4.1-fast","https://docs.x.ai/developers/rest-api-reference/inference/chat-completions","openai"));
        ALL.add(new AiProvider("together","Together AI","https://api.together.xyz/v1/chat/completions","meta-llama/Llama-3.3-70B-Instruct-Turbo","https://docs.together.ai/docs/openai-api-compatibility","openai"));
    }
    public static List<AiProvider> all(){ return new ArrayList<>(ALL); }
    public static AiProvider byId(String id){ for(AiProvider p:ALL) if(p.id.equals(id)) return p; return ALL.get(0); }
    public static int indexOf(String id){ for(int i=0;i<ALL.size();i++) if(ALL.get(i).id.equals(id)) return i; return 0; }
    @Override public String toString(){return name;}
}
