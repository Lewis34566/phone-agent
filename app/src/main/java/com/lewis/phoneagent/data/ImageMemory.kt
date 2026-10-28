package com.lewis.phoneagent.data

import java.util.concurrent.atomic.AtomicReference

/** 内存态：最近一张图片路径（拍照/生图后写入，供"看图"工具读取） */
class ImageMemory {
    private val path = AtomicReference<String?>()

    fun set(p: String) = path.set(p)

    fun latest(): String? = path.get()
}
