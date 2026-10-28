package com.lewis.phoneagent.data

import android.content.Context
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 本地 OCR（拍图识字）：Google ML Kit 中文识别，离线模型，不调任何云端服务。
 *
 * 诚实边界：
 *  - 首次识别需要联网下载中文离线模型（约 20MB，国内网络可能失败）；
 *  - 失败时明确提示，不会静默假装识别成功。
 */
class OcrEngine(private val context: Context) {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /** 识别图片中的文字；失败抛异常（含模型下载失败），由调用方转提示 */
    suspend fun recognize(imagePath: String): String = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, android.net.Uri.fromFile(java.io.File(imagePath)))
        val result = recognizer.process(image).await()
        result.textBlocks.joinToString("\n") { it.text }.ifBlank { "（没有识别到文字）" }
    }

    /** Task → 挂起（不引 kotlinx-coroutines-play-services，手写转换） */
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }
}
