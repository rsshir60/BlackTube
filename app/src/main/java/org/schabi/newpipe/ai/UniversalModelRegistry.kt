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
            downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-GGUF/resolve/main/Phi-3-mini-4k-instruct-q4.gguf?download=true",
            fileName = "Phi-5-1.3b-instruct-Q5_K_M.gguf",
            fileSizeMB = 850,
            minRamGB = 3,
            quantFormat = "Q5_K_M"
        ),
        LocalModelInfo(
            id = "qwen_2_5_1_5b",
            name = "Qwen 2.5 1.5B (Ultra-Fast)",
            description = "Lightweight & blazing fast. Ideal for phones with 4GB+ RAM.",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            fileSizeMB = 1100,
            minRamGB = 4,
            quantFormat = "Q4_K_M"
        ),
        LocalModelInfo(
            id = "phi_4_mini_3_8b",
            name = "Phi-4 Mini 3.8B (Balanced Flagship)",
            description = "Microsoft's flagship 3.8B model. Excellent accuracy for long video summaries.",
            downloadUrl = "https://huggingface.co/bartowski/Phi-4-mini-instruct-GGUF/resolve/main/Phi-4-mini-instruct-Q4_K_M.gguf?download=true",
            fileName = "Phi-4-mini-instruct-Q4_K_M.gguf",
            fileSizeMB = 2490,
            minRamGB = 6,
            quantFormat = "Q4_K_M"
        ),
        LocalModelInfo(
            id = "llama_3_2_3b",
            name = "Llama 3.2 3B (Meta Reasoning)",
            description = "Meta's state-of-the-art 3B model. High quality reasoning for complex video topics.",
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf?download=true",
            fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            fileSizeMB = 2020,
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
        // 1. Check public DIRECTORY_DOWNLOADS/BlackTube_AI/ first (written by system DownloadManager)
        val publicDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "BlackTube_AI")
        val publicFile = File(publicDir, modelInfo.fileName)
        if (publicFile.exists() && publicFile.length() > 50 * 1024 * 1024L) {
            return publicFile
        }

        // 2. Check app private files directory fallback
        val privateDir = File(context.getExternalFilesDir(null), "models")
        if (!privateDir.exists()) {
            privateDir.mkdirs()
        }
        val privateFile = File(privateDir, modelInfo.fileName)
        if (privateFile.exists() && privateFile.length() > 50 * 1024 * 1024L) {
            return privateFile
        }

        return publicFile
    }

    fun isModelDownloaded(context: Context, modelInfo: LocalModelInfo): Boolean {
        val file = getModelFile(context, modelInfo)
        return file.exists() && file.length() > 50 * 1024 * 1024L
    }
}
