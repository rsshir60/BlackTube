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

    @JvmField
    val DEFAULT_MODEL = LocalModelInfo(
        id = "phi_4_mini_3_8b",
        name = "Phi-4 Mini 3.8B (Flagship Offline SLM)",
        description = "Microsoft's flagship 3.8B SLM. Provides high-accuracy offline video summaries and Q&A.",
        downloadUrl = "https://huggingface.co/unsloth/Phi-4-mini-instruct-GGUF/resolve/main/Phi-4-mini-instruct-Q4_K_M.gguf?download=true",
        fileName = "Phi-4-mini-instruct-Q4_K_M.gguf",
        fileSizeMB = 2490,
        minRamGB = 4,
        quantFormat = "Q4_K_M"
    )

    val PRESET_MODELS = listOf(DEFAULT_MODEL)

    fun getActiveModel(context: Context): LocalModelInfo {
        return DEFAULT_MODEL
    }

    fun setActiveModel(context: Context, modelId: String) {
        // Single default model architecture
    }

    fun getModelFile(context: Context, modelInfo: LocalModelInfo = DEFAULT_MODEL): File {
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

    fun isModelDownloaded(context: Context, modelInfo: LocalModelInfo = DEFAULT_MODEL): Boolean {
        val file = getModelFile(context, modelInfo)
        return file.exists() && file.length() > 50 * 1024 * 1024L
    }
}
