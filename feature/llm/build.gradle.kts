// :feature:llm
plugins {
    id("nous.android.feature")
}

android {
    namespace = "com.roshan.persona.llm"
}

dependencies {
    // LLM module implements Brain's LocalLlmProvider, CloudLlmLayer, and
    // LlmAgenticPlanner interfaces. Must depend on :feature:brain for these.
    implementation(project(":feature:brain"))
    // OkHttp for cloud LLM API calls (Strategy §4.6 — 5 cloud providers via OkHttp).
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    // OkHttp SSE support for parsing Server-Sent Events streams from cloud LLMs
    // (OpenAI/Anthropic/Gemini streaming responses). Used by StreamParser.
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    // WorkManager for PredictiveModelPreloader (background model preload when
    // device is charging + idle). Strategy - Predictive Preloading.
    implementation(libs.androidx.work.runtime)
}
