package io.github.ssebanom.ling.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.ssebanom.ling.AppContainer
import io.github.ssebanom.ling.data.AppSettings
import io.github.ssebanom.ling.data.ModelCatalog
import io.github.ssebanom.ling.data.ModelStore
import io.github.ssebanom.ling.data.ModelVariant
import io.github.ssebanom.ling.runtime.DeviceProfiler
import io.github.ssebanom.ling.service.LingService
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ModelsScreen(c: AppContainer, modifier: Modifier = Modifier) {
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

    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Ling-3.0-tiny GGUF", style = MaterialTheme.typography.titleLarge)
            Text(
                "RAM ${profile.totalRamBytes shr 30}GB · 가용 ${profile.availRamBytes shr 20}MiB · 저장공간 여유 ${c.models.dir.usableSpace shr 30}GB",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("출처: ${ModelCatalog.REPO} (MIT)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        items(ModelCatalog.variants, key = { it.id }) { v ->
            VariantCard(v, c, settings, dl, onChanged = { refresh++ })
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("파일에서 가져오기", style = MaterialTheme.typography.titleMedium)
                    Text("PC 등에서 받은 .gguf 파일을 앱 폴더로 복사합니다.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("GGUF 선택") }
                    importMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        val others = files.filter { f -> ModelCatalog.byFile(f.name) == null }
        if (others.isNotEmpty()) item { Text("기타 파일", style = MaterialTheme.typography.titleMedium) }
        items(others, key = { it.name }) { f ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name)
                        Text("${f.length() shr 20} MiB", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { scope.launch { c.settings.update { it.copy(modelFile = f.name) } } },
                        enabled = settings.modelFile != f.name) { Text(if (settings.modelFile == f.name) "사용 중" else "사용") }
                    TextButton(onClick = { c.models.delete(f); refresh++ }) { Text("삭제") }
                }
            }
        }
    }
}

@Composable
private fun VariantCard(
    v: ModelVariant, c: AppContainer, settings: AppSettings, dl: ModelStore.DownloadState, onChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val complete = c.models.isComplete(v)
    val partFile = File(c.models.fileFor(v).path + ".part")
    val active = settings.modelFile == v.fileName
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(v.id + if (v.recommended) "  ★ 권장" else "", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${"%.2f".format(v.sizeBytes / 1e9)} GB", style = MaterialTheme.typography.labelLarge)
            }
            Text(v.note, style = MaterialTheme.typography.bodySmall)
            Text(
                "토큰당 가중치 읽기 ≈ ${"%.2f".format(v.decodeBytesPerToken / 1e9)} GB → 60GB/s 기준 상한 ${"%.0f".format(60e9 / v.decodeBytesPerToken)} tok/s",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
            )
            when {
                dl is ModelStore.DownloadState.Running && dl.variant == v.id -> {
                    LinearProgressIndicator(progress = { dl.done.toFloat() / dl.total }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    Row {
                        Text("${dl.done shr 20}/${dl.total shr 20} MiB · ${"%.1f".format(dl.bytesPerSec / 1e6)} MB/s",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { c.models.cancelDownload() }) { Text("일시정지") }
                    }
                }
                dl is ModelStore.DownloadState.Verifying && dl.variant == v.id -> {
                    Text("SHA-256 검증 중 ${dl.done * 100 / dl.total}%", style = MaterialTheme.typography.bodySmall)
                }
                dl is ModelStore.DownloadState.WaitingNetwork && dl.variant == v.id -> Row {
                    Text(dl.reason, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { c.models.cancelDownload() }) { Text("일시정지") }
                }
                dl is ModelStore.DownloadState.Retrying && dl.variant == v.id -> {
                    Text("재시도 ${dl.attempt}: ${dl.reason}", style = MaterialTheme.typography.bodySmall)
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (complete) {
                        Button(onClick = { scope.launch { c.settings.update { it.copy(modelFile = v.fileName) } } }, enabled = !active) {
                            Text(if (active) "사용 중" else "사용")
                        }
                        OutlinedButton(onClick = { c.models.delete(c.models.fileFor(v)); onChanged() }) { Text("삭제") }
                    } else {
                        Button(onClick = {
                            LingService.start(ctx)
                            c.appScope.launch {
                                if (c.models.downloadModel(v) && c.settings.current().modelFile.isEmpty()) {
                                    c.settings.update { it.copy(modelFile = v.fileName) }
                                }
                                onChanged()
                            }
                        }, enabled = !c.models.isDownloading) {
                            Text(if (partFile.exists()) "이어받기 (${partFile.length() shr 20} MiB)" else "다운로드")
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                }
            }
            if (dl is ModelStore.DownloadState.Failed && dl.variant == v.id) {
                Text("실패: ${dl.error}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
