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

    private var isLibraryLoaded = false

    init {
        try {
            System.loadLibrary("blacktube_llama")
            isLibraryLoaded = true
            Log.i(TAG, "Native blacktube_llama library loaded successfully")
        } catch (e: Throwable) {
            isLibraryLoaded = false
            Log.w(TAG, "Native blacktube_llama library failed to load gracefully: ${e.message}")
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
        if (!isLibraryLoaded) {
            Log.w(TAG, "Cannot initialize local model: native blacktube_llama library is not loaded")
            return@withContext false
        }

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
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize native local model gracefully: ${activeModel.name}", e)
            nativeHandle = 0L
            isInitialized = false
            loadedModelId = null
            return@withContext false
        }
    }

    suspend fun generateSummary(prompt: String): String = withContext(Dispatchers.IO) {
        if (!isLibraryLoaded || !isInitialized || nativeHandle == 0L) {
            return@withContext "Local AI engine is preparing. Please tap Retry to initialize the model."
        }

        try {
            return@withContext nativeGenerateResponse(nativeHandle, prompt)
        } catch (e: Throwable) {
            Log.e(TAG, "Error generating local AI response gracefully", e)
            return@withContext "Local AI engine encountered a temporary hiccup. Tapping Retry will re-initialize the model engine."
        }
    }

    fun release() {
        if (nativeHandle != 0L) {
            try {
                nativeFreeModel(nativeHandle)
                Log.i(TAG, "Native model resources released")
            } catch (e: Throwable) {
                Log.e(TAG, "Error releasing native model gracefully", e)
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
