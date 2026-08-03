package org.schabi.newpipe.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object LocalModelEngine {
    private const val TAG = "LocalModelEngine"

    private var nativeHandle: Long = 0L
    private var isInitialized = false
    private var loadedModelId: String? = null

    init {
        try {
            System.loadLibrary("blacktube_llama")
            Log.i(TAG, "Native blacktube_llama library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native blacktube_llama library failed to load: ${e.message}")
        }
    }

    fun isModelDownloaded(context: Context): Boolean {
        val activeModel = UniversalModelRegistry.getActiveModel(context)
        return UniversalModelRegistry.isModelDownloaded(context, activeModel)
    }

    fun getModelFile(context: Context): File {
        val activeModel = UniversalModelRegistry.getActiveModel(context)
        return UniversalModelRegistry.getModelFile(context, activeModel)
    }

    suspend fun initialize(context: Context, contextSize: Int = 16384): Boolean = withContext(Dispatchers.IO) {
        val activeModel = UniversalModelRegistry.getActiveModel(context)
        if (isInitialized && nativeHandle != 0L && loadedModelId == activeModel.id) {
            return@withContext true
        }

        release() // release any previously loaded model

        val modelFile = UniversalModelRegistry.getModelFile(context, activeModel)
        if (!modelFile.exists()) {
            Log.w(TAG, "Cannot initialize local model: file does not exist at ${modelFile.absolutePath}")
            return@withContext false
        }

        try {
            nativeHandle = nativeInitModel(modelFile.absolutePath, contextSize)
            isInitialized = nativeHandle != 0L
            if (isInitialized) {
                loadedModelId = activeModel.id
            }
            Log.i(TAG, "Local model [${activeModel.name}] initialized with handle=$nativeHandle")
            return@withContext isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize native local model: ${activeModel.name}", e)
            return@withContext false
        }
    }

    suspend fun generateSummary(prompt: String): String = withContext(Dispatchers.IO) {
        if (!isInitialized || nativeHandle == 0L) {
            return@withContext "Local AI engine is initializing or model is not loaded yet."
        }

        try {
            return@withContext nativeGenerateResponse(nativeHandle, prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating local AI response", e)
            return@withContext "Error running local LLM inference: ${e.localizedMessage}"
        }
    }

    fun release() {
        if (nativeHandle != 0L) {
            try {
                nativeFreeModel(nativeHandle)
                Log.i(TAG, "Native model resources released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing native model", e)
            } finally {
                nativeHandle = 0L
                isInitialized = false
                loadedModelId = null
            }
        }
    }

    // Native JNI signatures
    private external fun nativeInitModel(modelPath: String, contextSize: Int): Long
    private external fun nativeGenerateResponse(handle: Long, prompt: String): String
    private external fun nativeFreeModel(handle: Long)
}
