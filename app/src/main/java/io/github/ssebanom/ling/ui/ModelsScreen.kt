package io.github.ssebanom.ling.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ssebanom.ling.AppContainer
import io.github.ssebanom.ling.data.AppSettings
import io.github.ssebanom.ling.data.ModelCatalog
import io.github.ssebanom.ling.data.ModelStore
import io.github.ssebanom.ling.data.ModelVariant
import io.github.ssebanom.ling.domain.ModelFamily
import io.github.ssebanom.ling.runtime.DeviceProfiler
import io.github.ssebanom.ling.service.LingService
import io.github.ssebanom.ling.ui.components.Pill
import io.github.ssebanom.ling.ui.components.ScreenScaffold
import io.github.ssebanom.ling.ui.components.SectionCard
import io.github.ssebanom.ling.ui.theme.LingColors
import kotlinx.coroutines.launch
import java.io.File

private fun familyInfo(f: ModelFamily): Pair<String, String> = when (f) {
    ModelFamily.LING -> "7.9B 총 · 1.3B 활성 MoE" to "KDA+MLA 하이브리드 · 빠르고 한국어 양호 · 출처 ${ModelCatalog.REPO} (MIT)"
    ModelFamily.LFM2 -> "8.3B 총 · 1.5B 활성 MoE" to "conv+GQA · 툴 호출 특화, 항상 추론 · 출처 ${ModelCatalog.LFM_REPO} (LFM Open License v1.0)"
    ModelFamily.K2H -> "3.7B dense" to "툴 호출 정상, 한국어 약함(영어 권장) · KV 가 커서 컨텍스트 8K·q8 고정 · 출처 ${ModelCatalog.K2H_REPO} (Apache-2.0)"
    ModelFamily.QWEN36 -> "36B 총 · 3B 활성 MoE (실험)" to "1bit 양자화(+일부 expert 가지치기). 큰 파일은 저장장치에서 읽으며 실행, " +
        "GPU/NPU 선택 시 expert 는 CPU(mmap). 다른 앱이 종료될 수 있고 원본보다 품질 낮음 · Apache-2.0"
}

@Composable
fun ModelsScreen(c: AppContainer, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dl by c.models.download.collectAsState()
    val settings by c.settings.flow.collectAsState(initial = AppSettings())
    var refresh by remember { mutableIntStateOf(0) }
    var files by remember { mutableStateOf(listOf<File>()) }
    var importMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(refresh, dl) { files = c.models.listModels() }
    val profile = remember { DeviceProfiler.profile(ctx) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { cur ->
            val i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cur.moveToFirst() && i >= 0) cur.getString(i) else null
        } ?: "imported.gguf"
        c.appScope.launch {
            importMsg = "가져오는 중: $name"
            val f = c.models.importFrom(uri, name) { n -> importMsg = "가져오는 중: $name (${n shr 20} MiB)" }
            importMsg = if (ModelStore.isGguf(f)) "가져옴: ${f.name}" else { f.delete(); "GGUF 파일이 아닙니다" }
            refresh++
        }
    }

    ScreenScaffold("모델", onBack) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                val total = profile.totalRamBytes.toFloat()
                val used = (profile.totalRamBytes - profile.availRamBytes).toFloat()
                SectionCard("기기 여유", Icons.Outlined.SdStorage) {
                    Text("RAM ${profile.totalRamBytes shr 30}GB 중 가용 ${"%.1f".format(profile.availRamBytes / 1e9)}GB · 저장공간 여유 ${c.models.dir.usableSpace shr 30}GB",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { used / total }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
                }
            }
            for ((family, vs) in ModelCatalog.variants.groupBy { it.family }) {
                item(key = "h-${family.name}") { FamilyHeader(family, active = c.inference.familyFor(File(settings.modelFile)) == family) }
                items(vs, key = { it.id }) { v -> VariantCard(v, c, settings, dl, onChanged = { refresh++ }) }
            }
            item {
                SectionCard("파일에서 가져오기", Icons.Outlined.FileOpen, subtitle = "PC 등에서 받은 .gguf 파일을 앱 폴더로 복사합니다") {
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("GGUF 선택") }
                    importMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            val others = files.filter { f -> ModelCatalog.byFile(f.name) == null }
            if (others.isNotEmpty()) item { Text("기타 파일", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(others, key = { it.name }) { f ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${f.length() shr 20} MiB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        TextButton(onClick = { scope.launch { c.settings.update { it.copy(modelFile = f.name) } } },
                            enabled = settings.modelFile != f.name) { Text(if (settings.modelFile == f.name) "사용 중" else "사용") }
                        IconButton(onClick = { c.models.delete(f); refresh++ }) { Icon(Icons.Outlined.Delete, "삭제") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FamilyHeader(f: ModelFamily, active: Boolean) {
    val (spec, desc) = familyInfo(f)
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(10.dp).clip(CircleShape).background(
                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)),
                ),
            )
            Spacer(Modifier.width(10.dp))
            Text(f.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (active) Pill("사용 중", LingColors.success)
        }
        Text(spec, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp))
        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariantCard(v: ModelVariant, c: AppContainer, settings: AppSettings, dl: ModelStore.DownloadState, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val complete = c.models.isComplete(v)
    val partFile = File(c.models.fileFor(v).path + ".part")
    val active = settings.modelFile == v.fileName
    val mine = when (dl) {
        is ModelStore.DownloadState.Running -> dl.variant == v.id
        is ModelStore.DownloadState.Verifying -> dl.variant == v.id
        is ModelStore.DownloadState.WaitingNetwork -> dl.variant == v.id
        is ModelStore.DownloadState.Retrying -> dl.variant == v.id
        else -> false
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().animateContentSize().then(
            if (active) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), MaterialTheme.shapes.large) else Modifier,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v.id, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${"%.2f".format(v.sizeBytes / 1e9)} GB", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (v.recommended) Pill("권장", MaterialTheme.colorScheme.primary, icon = Icons.Outlined.Star)
                if (v.experimental) Pill("실험", LingColors.warning, icon = Icons.Outlined.Science)
                if (complete) Pill("받음", LingColors.success, icon = Icons.Outlined.CheckCircle)
                Pill("상한 ≈ ${"%.0f".format(60e9 / v.decodeBytesPerToken)} tok/s", MaterialTheme.colorScheme.secondary)
            }
            Text(v.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${v.repo} · 토큰당 가중치 읽기 ${"%.2f".format(v.decodeBytesPerToken / 1e9)} GB (60GB/s 기준 상한)",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            AnimatedContent(mine, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "dl") { downloading ->
                if (downloading) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (dl) {
                        is ModelStore.DownloadState.Running -> {
                            val p by animateFloatAsState(dl.done.toFloat() / dl.total, label = "p")
                            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${(p * 100).toInt()}% · ${dl.done shr 20}/${dl.total shr 20} MiB · ${"%.1f".format(dl.bytesPerSec / 1e6)} MB/s",
                                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = { c.models.cancelDownload() }) { Icon(Icons.Outlined.Pause, "일시정지") }
                            }
                        }
                        is ModelStore.DownloadState.Verifying -> {
                            LinearProgressIndicator(progress = { dl.done.toFloat() / dl.total }, modifier = Modifier.fillMaxWidth().clip(CircleShape),
                                color = MaterialTheme.colorScheme.secondary)
                            Text("SHA-256 검증 중 ${dl.done * 100 / dl.total}%", style = MaterialTheme.typography.bodySmall)
                        }
                        is ModelStore.DownloadState.WaitingNetwork -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(dl.reason, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { c.models.cancelDownload() }) { Text("일시정지") }
                        }
                        is ModelStore.DownloadState.Retrying -> Text("재시도 ${dl.attempt}: ${dl.reason}", style = MaterialTheme.typography.bodySmall)
                        else -> Unit
                    }
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (complete) {
                        Button(onClick = { scope.launch { c.settings.update { it.copy(modelFile = v.fileName) } } }, enabled = !active) {
                            Text(if (active) "사용 중" else "이 모델 사용")
                        }
                        OutlinedButton(onClick = { c.models.delete(c.models.fileFor(v)); onChanged() }) { Text("삭제") }
                    } else {
                        FilledTonalButton(onClick = {
                            LingService.start(ctx)
                            c.appScope.launch {
                                if (c.models.downloadModel(v) && c.settings.current().modelFile.isEmpty()) {
                                    c.settings.update { it.copy(modelFile = v.fileName) }
                                }
                                onChanged()
                            }
                        }, enabled = !c.models.isDownloading) {
                            Icon(Icons.Outlined.CloudDownload, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (partFile.exists()) "이어받기 (${partFile.length() shr 20} MiB)" else "다운로드")
                        }
                    }
                }
            }
            if (dl is ModelStore.DownloadState.Failed && dl.variant == v.id) {
                Text("실패: ${dl.error}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
