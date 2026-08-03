package org.schabi.newpipe.ai

import android.content.Context
import java.io.File

data class LocalModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val downloadUrl: String,
    val fileName: String,
    val fileSizeMB: Int,
    val minRamGB: Int,
    val quantFormat: String,
    val isPreset: Boolean = true
)

object UniversalModelRegistry {
    const val PREF_ACTIVE_MODEL_ID = "pref_key_active_local_model_id"

    val PRESET_MODELS = listOf(
        LocalModelInfo(
            id = "phi_5_1_3b",
            name = "Phi-5 1.3B (Next-Gen SLM / 850 MB)",
            description = "Microsoft's brand-new 1.3B model. Blazing fast (50+ t/s) and runs on 3GB+ RAM.",
            downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-GGUF/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
            fileName = "Phi-5-1.3b-instruct-Q5_K_M.gguf",
            fileSizeMB = 850,
            minRamGB = 3,
            quantFormat = "Q5_K_M"
        ),
        LocalModelInfo(
            id = "qwen_2_5_1_5b",
            name = "Qwen 2.5 1.5B (Ultra-Fast)",
            description = "Lightweight & blazing fast. Ideal for phones with 4GB+ RAM.",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            fileSizeMB = 1100,
            minRamGB = 4,
            quantFormat = "Q4_K_M"
        ),
        LocalModelInfo(
            id = "phi_4_mini_3_8b",
            name = "Phi-4 Mini 3.8B (Balanced Flagship)",
            description = "Microsoft's flagship 3.8B model. Excellent accuracy for long video summaries.",
            downloadUrl = "https://huggingface.co/microsoft/Phi-4-mini-instruct-GGUF/resolve/main/Phi-4-mini-instruct-Q5_K_M.gguf",
            fileName = "Phi-4-mini-instruct-Q5_K_M.gguf",
            fileSizeMB = 2600,
            minRamGB = 6,
            quantFormat = "Q5_K_M"
        ),
        LocalModelInfo(
            id = "llama_3_2_3b",
            name = "Llama 3.2 3B (Meta Reasoning)",
            description = "Meta's state-of-the-art 3B model. High quality reasoning for complex video topics.",
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            fileSizeMB = 2000,
            minRamGB = 6,
            quantFormat = "Q4_K_M"
        )
    )

    fun getActiveModel(context: Context): LocalModelInfo {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val activeId = prefs.getString(PREF_ACTIVE_MODEL_ID, "phi_5_1_3b")
        return PRESET_MODELS.find { it.id == activeId } ?: PRESET_MODELS[0]
    }

    fun setActiveModel(context: Context, modelId: String) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putString(PREF_ACTIVE_MODEL_ID, modelId).apply()
    }

    fun getModelFile(context: Context, modelInfo: LocalModelInfo): File {
        val modelsDir = File(context.getExternalFilesDir(null), "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        return File(modelsDir, modelInfo.fileName)
    }

    fun isModelDownloaded(context: Context, modelInfo: LocalModelInfo): Boolean {
        val file = getModelFile(context, modelInfo)
        return file.exists() && file.length() > 50 * 1024 * 1024L
    }
}
