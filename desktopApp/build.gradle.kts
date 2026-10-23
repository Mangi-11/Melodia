import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.jna)
    implementation(libs.jaudiotagger)
    implementation(libs.graal.polyglot)
    runtimeOnly(libs.graal.js.community)
    runtimeOnly(libs.graal.wasm.community)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// 听歌识曲的 afp 指纹库与 Android assets 共用一份
tasks.processResources {
    from(rootProject.file("app/src/main/assets")) {
        include("afp/afp.js", "afp/afp.wasm.js")
    }
}

// 与 Android 端共用发版参数，保证两端版本号一致
val desktopVersion = (findProperty("releaseVersionName") as String?) ?: "1.0.0"

// jpackage 只接受纯数字版本：预发布版 1.2.3-beta.1 取 1.2.3（预发布不出 MSI，只出免安装版）
val desktopPackageVersion = desktopVersion.substringBefore('-').also {
    require(Regex("""\d+\.\d+\.\d+""").matches(it)) { "版本号需形如 1.2.3：$desktopVersion" }
}

// 调用本机 cmake 编译原生桥接 DLL；缺少 cmake 或 MSVC 时默认只告警跳过（运行时由代码回退），
// required 为真（CI 发版）时直接失败，避免打出缺少系统媒体控制的安装包
abstract class CmakeDllTask @Inject constructor(private val execOps: ExecOperations) : DefaultTask() {
    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty

    @get:Internal
    abstract val cmakeBuildDir: DirectoryProperty

    @get:Input
    abstract val dllName: Property<String>

    @get:Input
    abstract val required: Property<Boolean>

    @get:OutputFile
    abstract val outputDll: RegularFileProperty

    private fun skip(reason: String) {
        if (required.get()) throw GradleException(reason)
        logger.warn("$reason，已跳过")
    }

    @TaskAction
    fun build() {
        val cmake = System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .map { File(it, "cmake.exe") }
            .firstOrNull { it.isFile }
        if (cmake == null) {
            skip("未找到 cmake，无法编译 ${dllName.get()}")
            return
        }
        val buildDir = cmakeBuildDir.get().asFile
        val steps = listOf(
            listOf(cmake.absolutePath, "-S", sourceDir.get().asFile.absolutePath, "-B", buildDir.absolutePath),
            listOf(cmake.absolutePath, "--build", buildDir.absolutePath, "--config", "Release")
        )
        for (command in steps) {
            val log = ByteArrayOutputStream()
            val result = execOps.exec {
                commandLine(command)
                standardOutput = log
                errorOutput = log
                isIgnoreExitValue = true
            }
            if (result.exitValue != 0) {
                skip("${dllName.get()} 编译失败：\n$log")
                return
            }
        }
        File(buildDir, "Release/${dllName.get()}").copyTo(outputDll.get().asFile, overwrite = true)
    }
}

val buildSmtc by tasks.registering(CmakeDllTask::class) {
    onlyIf { System.getProperty("os.name").startsWith("Windows") }
    sourceDir.set(layout.projectDirectory.dir("native-src/smtc"))
    cmakeBuildDir.set(layout.buildDirectory.dir("smtc"))
    dllName.set("melodia_smtc.dll")
    required.set(providers.gradleProperty("requireSmtc").map { it.toBoolean() }.orElse(false))
    outputDll.set(layout.projectDirectory.file("native/melodia_smtc.dll"))
}

// 按 gradle.properties 固定的 Release 下载定制 libmpv 并校验 SHA256；本地已有且校验一致时不重复下载
abstract class DownloadVerifiedFileTask : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    private fun digestOf(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @TaskAction
    fun download() {
        val target = outputFile.get().asFile
        val expected = sha256.get().lowercase()
        if (target.isFile && digestOf(target) == expected) return
        target.parentFile.mkdirs()
        val temp = File(target.parentFile, "${target.name}.download")
        logger.lifecycle("下载 ${url.get()}")
        try {
            URI(url.get()).toURL().openStream().use { input ->
                temp.outputStream().use { input.copyTo(it) }
            }
        } catch (e: IOException) {
            temp.delete()
            throw GradleException("下载失败：${url.get()}", e)
        }
        val actual = digestOf(temp)
        if (actual != expected) {
            temp.delete()
            throw GradleException("SHA256 校验失败：期望 $expected，实际 $actual")
        }
        if (target.exists() && !target.delete()) throw GradleException("无法替换 ${target.absolutePath}，可能正被占用")
        if (!temp.renameTo(target)) throw GradleException("无法写入 ${target.absolutePath}")
    }
}

val downloadLibmpv by tasks.registering(DownloadVerifiedFileTask::class) {
    val tag = providers.gradleProperty("libmpvReleaseTag")
    url.set(tag.map { "https://github.com/rinchao0721/mpv-winbuild-cmake/releases/download/$it/libmpv-2.dll" })
    sha256.set(providers.gradleProperty("libmpvSha256"))
    outputFile.set(layout.projectDirectory.file("native/libmpv-2.dll"))
}

// native 目录里的 libmpv 与 SMTC 桥接 DLL，打包与运行前同步进 Compose 约定的平台资源目录
val prepareNativeResources by tasks.registering(Sync::class) {
    dependsOn(buildSmtc, downloadLibmpv)
    from(layout.projectDirectory.dir("native")) {
        include("*.dll")
    }
    into(layout.buildDirectory.dir("appResources/windows"))
}

val generateAppInfo by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/appinfo")
    val version = desktopVersion
    // 仅 CI 传入发布版本号的构建参与检查更新，本地构建的默认版本号不可信
    val release = findProperty("releaseVersionName") != null
    inputs.property("version", version)
    inputs.property("release", release)
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile.apply { mkdirs() }
        val sep = System.lineSeparator()
        dir.resolve("app-info.properties").writeText("version=$version${sep}release=$release$sep")
    }
}

sourceSets.main {
    resources.srcDir(generateAppInfo)
}

// jpackage 需要 JDK 17+ 完整版，由工具链自动下载，不影响日常编译所用 JDK
val packagingJavaHome = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
    vendor.set(JvmVendorSpec.ADOPTIUM)
}.map { it.metadata.installationPath.asFile.absolutePath }

compose.desktop {
    application {
        mainClass = "com.lin0721.linmusic.desktop.MainKt"
        javaHome = packagingJavaHome.get()
        // 中文系统默认 GBK，统一日志输出编码
        jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "--enable-native-access=ALL-UNNAMED")
        // 实测堆不足 200MB，上限只作兜底；空闲时 G1 定期回收并把空闲堆还给系统
        jvmArgs("-Xmx1g", "-XX:G1PeriodicGCInterval=60000")
        // 发布版打包时用 ProGuard 裁掉未使用的类（图标库等体积大头）
        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
            obfuscate.set(false)
            optimize.set(false)
        }
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Melodia"
            packageVersion = desktopPackageVersion
            vendor = "Melodia"
            description = "Melodia 音乐播放器"
            appResourcesRootDir.set(layout.buildDirectory.dir("appResources"))
            // jdeps 建议列表 + TLS 椭圆曲线套件所需的 jdk.crypto.ec（jdeps 无法静态分析出）
            modules("java.instrument", "java.naming", "java.prefs", "java.sql", "jdk.unsupported", "jdk.crypto.ec")
            windows {
                iconFile.set(layout.projectDirectory.file("icons/melodia.ico"))
                // 固定值，覆盖安装时据此识别为同一应用并升级
                upgradeUuid = "6f0b8a52-3c1e-4d7a-9b25-8e4c2f1d7a93"
                perUserInstall = true
                menu = true
                menuGroup = "Melodia"
                shortcut = true
                dirChooser = false
            }
        }
    }
}

// 运行与所有打包任务都先同步 DLL，避免产物沿用旧版本
afterEvaluate {
    tasks.matching { task ->
        task.name == "run" || task.name == "prepareAppResources" ||
            task.name.startsWith("createDistributable") || task.name.startsWith("createReleaseDistributable") ||
            (task.name.startsWith("package") && task.name != "packageReleaseZip")
    }.configureEach { dependsOn(prepareNativeResources) }
    // gradle run 视为调试环境
    tasks.named<JavaExec>("run") {
        jvmArgs("-Dmelodia.debug=true")
        // -PperfProbe 时由 Skiko 每 2 秒向 stdout 输出实际渲染帧率与长帧
        if (providers.gradleProperty("perfProbe").isPresent) {
            jvmArgs("-Dskiko.fps.enabled=true", "-Dskiko.fps.periodSeconds=2", "-Dskiko.fps.longFrames.show=true")
        }
    }
}

// 免安装版：把发布版程序目录打成 zip
val packageReleaseZip by tasks.registering(Zip::class) {
    dependsOn("createReleaseDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main-release/app"))
    // 保留真实修改时间
    isPreserveFileTimestamps = true
    archiveFileName.set("Melodia-v$desktopVersion-windows-x64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main-release/zip"))
}
