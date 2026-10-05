package io.github.ssebanom.ling.runtime

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File

/**
 * 기기 프로파일: CPU 클러스터(최대 주파수 기준), 메모리, SoC.
 * S25 Ultra(SM8750): Oryon v2 prime 2개(4.47GHz) + performance 6개(3.53GHz) — 번호 배치는 런타임에 확인한다.
 */
data class CpuCluster(val maxFreqKHz: Long, val cpus: List<Int>)

data class DeviceProfile(
    val socModel: String,
    val socManufacturer: String,
    val totalRamBytes: Long,
    val availRamBytes: Long,
    val lowMemory: Boolean,
    val clusters: List<CpuCluster>,
    val cpuFeatures: Set<String>,
) {
    val nCpus: Int get() = clusters.sumOf { it.cpus.size }
    /** 빠른 클러스터부터 정렬된 CPU 번호 */
    val cpusFastFirst: List<Int> get() = clusters.sortedByDescending { it.maxFreqKHz }.flatMap { it.cpus }
    val isS25UltraClass: Boolean get() = socModel.contains("SM8750", ignoreCase = true)
    val hasI8mm: Boolean get() = "i8mm" in cpuFeatures
    val hasDotprod: Boolean get() = "asimddp" in cpuFeatures

    fun summary(): String = buildString {
        append("SoC $socManufacturer $socModel, RAM ${totalRamBytes / (1 shl 20)}MiB (가용 ${availRamBytes / (1 shl 20)}MiB)\n")
        clusters.sortedByDescending { it.maxFreqKHz }.forEach {
            append("  cluster ${it.maxFreqKHz / 1000}MHz: cpu${it.cpus.joinToString(",")}\n")
        }
        append("  features: ${listOf("asimddp", "i8mm", "sve", "sve2", "sme", "bf16").filter { it in cpuFeatures }.joinToString(" ")}")
    }
}

object DeviceProfiler {
    fun profile(context: Context): DeviceProfile {
        val am = context.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return DeviceProfile(
            socModel = Build.SOC_MODEL,
            socManufacturer = Build.SOC_MANUFACTURER,
            totalRamBytes = mi.totalMem,
            availRamBytes = mi.availMem,
            lowMemory = mi.lowMemory,
            clusters = readClusters(),
            cpuFeatures = readFeatures(),
        )
    }

    fun readClusters(root: File = File("/sys/devices/system/cpu")): List<CpuCluster> {
        val byFreq = sortedMapOf<Long, MutableList<Int>>()
        val n = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        for (cpu in 0 until maxOf(n, 8)) {
            val f = File(root, "cpu$cpu/cpufreq/cpuinfo_max_freq")
            val freq = runCatching { f.readText().trim().toLong() }.getOrNull() ?: continue
            byFreq.getOrPut(freq) { mutableListOf() }.add(cpu)
        }
        if (byFreq.isEmpty()) return listOf(CpuCluster(0, (0 until n).toList()))
        return byFreq.map { (f, c) -> CpuCluster(f, c) }
    }

    fun readFeatures(): Set<String> = runCatching {
        File("/proc/cpuinfo").readLines().firstOrNull { it.startsWith("Features") }
            ?.substringAfter(':')?.trim()?.split(Regex("\\s+"))?.toSet()
    }.getOrNull().orEmpty()

    /**
     * 스레드 수 후보별 cpumask: 빠른 코어부터 채운다(n=2 → prime 2개, n=6 → prime 2 + perf 4 ...).
     * 디코드는 메모리 바운드라 일부 코어만 쓰는 편이 빠를 수 있어 튜너가 실측으로 고른다.
     */
    fun maskFor(profile: DeviceProfile, nThreads: Int): String =
        profile.cpusFastFirst.take(nThreads).sorted().joinToString(",")

    /**
     * 추론 관련 스레드 TID (ADPF 힌트 세션 등록용).
     * ggml 워커는 "ling-infer" 스레드에서 생성되어 같은 comm 이름을 물려받는다.
     */
    fun inferenceThreadIds(): Set<Int> =
        File("/proc/self/task").listFiles()?.mapNotNull { dir ->
            val comm = runCatching { File(dir, "comm").readText().trim() }.getOrNull() ?: return@mapNotNull null
            if (comm.startsWith("ling-infer")) dir.name.toIntOrNull() else null
        }?.toSet().orEmpty()
}
