package me.rerere.rikkahub.ui.pages.mailbox

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.dao.ArchivedLetterProjection
import me.rerere.rikkahub.data.db.dao.MailboxLetterDAO
import me.rerere.rikkahub.data.db.dao.ReadyLetterProjection
import me.rerere.rikkahub.data.db.dao.UpcomingLetterProjection
import me.rerere.rikkahub.data.db.entity.MailboxLetterEntity
import me.rerere.rikkahub.data.gifts.MailboxArchiveImporter
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.koin.compose.koinInject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val Paper = Color(0xFFFBF7F0)
private val PaperCard = Color(0xFFFFFCF8)
private val PaperInk = Color(0xFF40372F)
private val PaperAccent = Color(0xFFC8A97E)
private val ShanghaiZone = ZoneId.of("Asia/Shanghai")

private enum class MailboxSection { Ready, Upcoming, Archive }

@Composable
fun MailboxPage(
    onOpenLetter: (String) -> Unit,
    dao: MailboxLetterDAO = koinInject(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importer = remember(dao) { MailboxArchiveImporter(dao) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var section by remember { mutableStateOf(MailboxSection.Ready) }
    var isImporting by remember { mutableStateOf(false) }
    var importDialogTitle by remember { mutableStateOf<String?>(null) }
    var importDialogMessage by remember { mutableStateOf<String?>(null) }
    val readyFlow = remember(now) { dao.observeReady(now) }
    val upcomingFlow = remember(now) { dao.observeUpcoming(now) }
    val archiveFlow = remember(dao) { dao.observeArchived() }
    val ready by readyFlow.collectAsStateWithLifecycle(emptyList())
    val upcoming by upcomingFlow.collectAsStateWithLifecycle(emptyList())
    val archive by archiveFlow.collectAsStateWithLifecycle(emptyList())

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val readError = context.getString(R.string.mailbox_import_read_error)
        isImporting = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri) ?: error(readError)
                    input.use { importer.importZip(it) }
                }
            }
            result.onSuccess { report ->
                importDialogTitle = context.getString(R.string.mailbox_import_complete_title)
                importDialogMessage = context.getString(
                    R.string.mailbox_import_complete_message,
                    report.imported,
                    report.duplicates,
                    report.ignored,
                )
            }.onFailure { error ->
                importDialogTitle = context.getString(R.string.mailbox_import_failed_title)
                importDialogMessage = context.getString(
                    R.string.mailbox_import_failed_message,
                    error.message ?: readError,
                )
            }
            isImporting = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }

    Scaffold(
        containerColor = Paper,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.mailbox_title),
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = { BackButton() },
                actions = {
                    TextButton(
                        enabled = !isImporting,
                        onClick = {
                            importLauncher.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/octet-stream",
                                )
                            )
                        },
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = PaperAccent,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.mailbox_import_delayed_box), color = PaperInk)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = stringResource(R.string.mailbox_subtitle),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = PaperInk.copy(alpha = 0.68f),
                fontSize = 13.sp,
            )

            MailboxTabs(
                section = section,
                onSectionChange = { section = it },
                readyCount = ready.size,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )

            when (section) {
                MailboxSection.Ready -> LetterList(
                    entries = ready,
                    emptyText = stringResource(R.string.mailbox_empty_ready),
                    key = { it.id },
                ) { letter ->
                    ReadyLetterCard(letter = letter, onClick = { onOpenLetter(letter.id) })
                }

                MailboxSection.Upcoming -> LetterList(
                    entries = upcoming,
                    emptyText = stringResource(R.string.mailbox_empty_upcoming),
                    key = { it.id },
                ) { letter ->
                    UpcomingLetterCard(letter = letter, now = now)
                }

                MailboxSection.Archive -> LetterList(
                    entries = archive,
                    emptyText = stringResource(R.string.mailbox_empty_archive),
                    key = { it.id },
                ) { letter ->
                    ArchivedLetterCard(letter = letter, onClick = { onOpenLetter(letter.id) })
                }
            }
        }
    }

    if (importDialogMessage != null) {
        AlertDialog(
            onDismissRequest = {
                importDialogTitle = null
                importDialogMessage = null
            },
            title = { Text(importDialogTitle.orEmpty()) },
            text = { Text(importDialogMessage.orEmpty()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        importDialogTitle = null
                        importDialogMessage = null
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}

@Composable
private fun MailboxTabs(
    section: MailboxSection,
    onSectionChange: (MailboxSection) -> Unit,
    readyCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.62f),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            MailboxSection.entries.forEach { item ->
                val title = when (item) {
                    MailboxSection.Ready -> stringResource(R.string.mailbox_tab_ready, readyCount)
                    MailboxSection.Upcoming -> stringResource(R.string.mailbox_tab_upcoming)
                    MailboxSection.Archive -> stringResource(R.string.mailbox_tab_archive)
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSectionChange(item) },
                    color = if (section == item) Color(0xFFF1E5D8) else Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(vertical = 9.dp),
                        color = PaperInk,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> LetterList(
    entries: List<T>,
    emptyText: String,
    key: (T) -> Any,
    content: @Composable (T) -> Unit,
) {
    if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = emptyText,
                modifier = Modifier.padding(40.dp),
                color = PaperInk.copy(alpha = 0.52f),
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries, key = key) { content(it) }
        }
    }
}

@Composable
private fun ReadyLetterCard(letter: ReadyLetterProjection, onClick: () -> Unit) {
    LetterCard(onClick = onClick) {
        Text(letter.title, color = PaperInk, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
        letter.summary?.takeIf { it.isNotBlank() }?.let {
            Text(it, modifier = Modifier.padding(top = 5.dp), color = PaperInk.copy(alpha = 0.64f), fontSize = 13.sp)
        }
        Text(
            stringResource(R.string.mailbox_ready_to_open),
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 12.dp),
            color = PaperAccent,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun UpcomingLetterCard(letter: UpcomingLetterProjection, now: Long) {
    val today = Instant.ofEpochMilli(now).atZone(ShanghaiZone).toLocalDate()
    val unlockDate = Instant.ofEpochMilli(letter.unlockAt).atZone(ShanghaiZone).toLocalDate()
    val days = ChronoUnit.DAYS.between(today, unlockDate).coerceAtLeast(1)
    LetterCard(onClick = null) {
        Text(
            text = stringResource(R.string.mailbox_locked_letter),
            color = PaperInk.copy(alpha = 0.36f),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.mailbox_days_until, days),
            modifier = Modifier.align(Alignment.End),
            color = PaperInk.copy(alpha = 0.56f),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ArchivedLetterCard(letter: ArchivedLetterProjection, onClick: () -> Unit) {
    LetterCard(onClick = onClick) {
        Text(letter.title, color = PaperInk, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
        letter.summary?.takeIf { it.isNotBlank() }?.let {
            Text(it, modifier = Modifier.padding(top = 5.dp), color = PaperInk.copy(alpha = 0.64f), fontSize = 13.sp)
        }
        Text(
            formatDate(letter.openedAt),
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 10.dp),
            color = PaperInk.copy(alpha = 0.45f),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun LetterCard(onClick: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PaperCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box {
            EnvelopeLines(
                modifier = Modifier
                    .matchParentSize()
                    .padding(6.dp)
            )
            Column(modifier = Modifier.padding(18.dp), content = content)
        }
    }
}

@Composable
private fun EnvelopeLines(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width / 2f, size.height * 0.62f)
            lineTo(size.width, 0f)
        }
        drawPath(path, color = PaperAccent.copy(alpha = 0.1f), style = Stroke(1.dp.toPx()))
    }
}

@Composable
fun MailboxLetterPage(
    id: String,
    dao: MailboxLetterDAO = koinInject(),
) {
    var letter by remember(id) { mutableStateOf<MailboxLetterEntity?>(null) }
    var loaded by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        letter = dao.open(id, System.currentTimeMillis())
        loaded = true
    }

    Scaffold(
        containerColor = Paper,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mailbox_letter_title), fontFamily = FontFamily.Serif) },
                navigationIcon = { BackButton() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper),
            )
        },
    ) { padding ->
        when {
            !loaded -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = PaperAccent) }

            letter == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.mailbox_letter_unavailable),
                    color = PaperInk.copy(alpha = 0.58f),
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
            ) {
                item {
                    val item = requireNotNull(letter)
                    Text(
                        text = item.title,
                        color = PaperInk,
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    item.salutation?.takeIf { it.isNotBlank() }?.let {
                        Text(it, modifier = Modifier.padding(top = 30.dp), color = PaperInk.copy(alpha = 0.72f))
                    }
                    Text(
                        text = item.body,
                        modifier = Modifier.padding(top = 18.dp),
                        color = PaperInk,
                        fontFamily = FontFamily.Serif,
                        fontSize = 17.sp,
                        lineHeight = 29.sp,
                    )
                    item.signature?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 30.dp),
                            color = PaperInk.copy(alpha = 0.72f),
                            textAlign = TextAlign.End,
                        )
                    }
                    Text(
                        text = formatDate(item.writtenAt),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        color = PaperInk.copy(alpha = 0.42f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.getDefault())
    return Instant.ofEpochMilli(timestamp).atZone(ShanghaiZone).format(formatter)
}
