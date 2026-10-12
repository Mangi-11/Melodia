package com.lin0721.linmusic.desktop.app

import java.io.File
import kotlin.system.exitProcess

// 由 SingleInstanceTest 以独立进程启动，打印自己是首个还是后来者
object SingleInstanceProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val instance = SingleInstance(File(args[0]), retries = 5, retryDelayMs = 100)
        val primary = instance.acquire { }
        println(if (primary) "PRIMARY" else "SECONDARY")
        System.out.flush()
        if (primary) Thread.sleep(args.getOrNull(1)?.toLong() ?: 0L)
        instance.close()
        exitProcess(0)
    }
}
