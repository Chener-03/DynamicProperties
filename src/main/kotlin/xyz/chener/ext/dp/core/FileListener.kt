package xyz.chener.ext.dp.core

import org.apache.commons.io.monitor.FileAlterationListenerAdaptor
import org.apache.commons.io.monitor.FileAlterationObserver
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory


abstract class FileListener(protected val filePath: Path,protected val changeType:Int,protected val scanTime:Int = 1000) {
    companion object {
        const val CREATE = 2
        const val DELETE = 4
        const val MODIFY = 8
    }

    init {
        if (scanTime < 1000) throw IllegalArgumentException("Scan time must be greater than 1000ms")
        if (filePath.isDirectory() || !filePath.toFile().exists()) {
            throw IllegalArgumentException("File path does not exist or is a directory")
        }
    }

    abstract fun registerListener(callback: (Int,String) -> Unit)
}


enum class ListenerType {
    WatchService,ApacheCommonIo,FileHash
}


class FileListenerFactory {
    companion object {
        fun createListener(filePath: Path,changeType:Int, listenerType: ListenerType,scanTime:Int = 1000): FileListener {
            return when (listenerType) {
                ListenerType.WatchService -> WatchServiceListener(filePath,changeType,scanTime)
                ListenerType.ApacheCommonIo -> ApacheCommonIoListener(filePath,changeType,scanTime)
                ListenerType.FileHash -> FileHashListener(filePath,changeType,scanTime)
            }
        }
    }
}


class ApacheCommonIoListener(filePath: Path,changeType:Int,scanTime:Int) : FileListener(filePath,changeType,scanTime) {

    override fun registerListener(callback: (Int,String) -> Unit) {
        val listener = object: FileAlterationListenerAdaptor(){
            override fun onFileChange(file: File?) {
                if ((changeType and MODIFY > 0) && file?.absolutePath == filePath.toAbsolutePath().toString()){
                    runCatching { callback(MODIFY,file.absolutePath) }
                }
            }

            override fun onFileCreate(file: File?) {
                if ((changeType and CREATE > 0) && file?.absolutePath == filePath.toAbsolutePath().toString()){
                    runCatching { callback(CREATE,file.absolutePath) }
                }
            }

            override fun onFileDelete(file: File?) {
                if ((changeType and DELETE > 0) && file?.absolutePath == filePath.toAbsolutePath().toString()){
                    runCatching { callback(DELETE,file.absolutePath) }
                }
            }
        }
        val monitor = org.apache.commons.io.monitor.FileAlterationMonitor(scanTime.toLong())
        val ob = FileAlterationObserver(filePath.toAbsolutePath().parent.toFile())
        ob.addListener(listener)
        monitor.addObserver(ob)
        monitor.setThreadFactory(object :ThreadFactory{
            override fun newThread(r: Runnable): Thread {
                val t = object : Thread() {

                    private var thread: Thread? = null

                    override fun start() {
                        thread = ofVirtual().start(r)
                    }

                    override fun interrupt() {
                        if (thread == null) throw IllegalStateException("Thread not started")
                        thread!!.interrupt()
                    }

                    override fun isInterrupted(): Boolean {
                        if (thread == null) throw IllegalStateException("Thread not started")
                        return thread!!.isInterrupted ?: true
                    }


                    override fun getContextClassLoader(): ClassLoader {
                        if (thread == null) throw IllegalStateException("Thread not started")
                        return thread!!.contextClassLoader
                    }

                    override fun setContextClassLoader(cl: ClassLoader?) {
                        if (thread == null) throw IllegalStateException("Thread not started")
                        thread!!.contextClassLoader = cl
                    }

                    override fun getStackTrace(): Array<StackTraceElement> {
                        return thread?.stackTrace ?: emptyArray()
                    }

                    override fun getId(): Long {
                        throw UnsupportedOperationException()
                    }

                    override fun getState(): State {
                        if (thread == null) throw IllegalStateException("Thread not started")
                        return thread!!.state
                    }

                    override fun getUncaughtExceptionHandler(): UncaughtExceptionHandler {
                        if (thread == null) throw IllegalStateException("Thread not started")
                        return thread!!.uncaughtExceptionHandler
                    }

                    override fun setUncaughtExceptionHandler(ueh: UncaughtExceptionHandler?) {
                        if (thread == null) throw IllegalStateException("Thread not started")
                        thread!!.uncaughtExceptionHandler = ueh
                    }
                }
                return t
            }
        })
        monitor.start()
        Runtime.getRuntime().addShutdownHook(Thread{
            monitor.stop()
        })
    }
}

class WatchServiceListener(filePath: Path,changeType:Int,scanTime:Int) : FileListener(filePath,changeType,scanTime) {

    private val log: Logger = LoggerFactory.getLogger(WatchServiceListener::class.java)

    init {
        val k8sEnv = System.getenv("KUBERNETES_SERVICE_HOST")
        if (k8sEnv != null) {
            log.warn("Kubernetes environment detected, it is recommended to use ApacheCommonIoListener in the Kubernetes environment.")
        }
    }

    override fun registerListener(callback: (Int,String) -> Unit) {
        val watchService = FileSystems.getDefault().newWatchService()
        filePath.toAbsolutePath().parent.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY,StandardWatchEventKinds.ENTRY_CREATE,StandardWatchEventKinds.ENTRY_DELETE)
        Thread.ofVirtual().name("DP|${filePath.fileName}").start {
            val hasUpdateThread = AtomicBoolean(false)
            while (true) {
                val take = watchService.take()
                run jmp@{
                    take.pollEvents().forEach {
                        val context = it.context()
                        if (context is Path && context.absolutePathString() == filePath.absolutePathString()) {
                            if (hasUpdateThread.get()) return@jmp
                            hasUpdateThread.set(true)
                            Thread.ofVirtual().start {
                                Thread.sleep(scanTime.toLong())
                                runCatching {
                                    if (it.kind() == StandardWatchEventKinds.ENTRY_MODIFY && changeType and MODIFY > 0)
                                        callback(MODIFY, filePath.toAbsolutePath().toString())
                                    if (it.kind() == StandardWatchEventKinds.ENTRY_CREATE && changeType and CREATE > 0)
                                        callback(CREATE, filePath.toAbsolutePath().toString())
                                    if (it.kind() == StandardWatchEventKinds.ENTRY_DELETE && changeType and DELETE > 0)
                                        callback(DELETE, filePath.toAbsolutePath().toString())
                                }
                                hasUpdateThread.set(false)
                            }
                        }
                    }
                    take.reset()
                }
            }
        }

    }
}

class FileHashListener(filePath: Path,changeType:Int,scanTime:Int) : FileListener(filePath,changeType,scanTime) {

    private val log: Logger = LoggerFactory.getLogger(FileHashListener::class.java)

    init {
        if (changeType != MODIFY) throw IllegalArgumentException("FileHashListener only support FileListener.MODIFY")
        if (Files.size(filePath) > 1024 * 1024) {
            log.warn("Files is more then 1MB, frequent hash calculations may affect performance.")
        }
    }

    override fun registerListener(callback: (Int,String) -> Unit) {
        var lastHash = Files.readAllBytes(filePath).contentHashCode()
        Thread.ofVirtual().name("DP|${filePath.fileName}").start {
            while (true){
                runCatching {
                    val hash = Files.readAllBytes(filePath).contentHashCode()
                    if (hash != lastHash){
                        lastHash = hash
                        callback(MODIFY,filePath.toAbsolutePath().toString())
                    }
                }
                Thread.sleep(scanTime.toLong())
            }
        }
    }
}
