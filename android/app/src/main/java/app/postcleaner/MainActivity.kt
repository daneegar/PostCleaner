package app.postcleaner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val model: CleanerModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model.callback(intent?.data)
        setContent { CleanerApp(model) { model.login()?.let { startActivity(it) } } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); model.callback(intent.data) }
}
private val Ink = Color(0xFF17251F)
private val Green = Color(0xFF246749)
@Composable
fun CleanerApp(model: CleanerModel, login: () -> Unit) {
    var showQueue by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = lightColorScheme(primary = Green, background = Color(0xFFF3F5EE), surface = Color.White, onSurface = Ink)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.background(Brush.verticalGradient(listOf(Color(0xFFEAF0E4), Color(0xFFF9F8F3))))
                .safeDrawingPadding().imePadding().fillMaxSize().padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("postcleaner", fontWeight = FontWeight.Bold, fontSize = 25.sp, color = Ink, modifier = Modifier.weight(1f))
                    if (model.connected) TextButton(onClick = { confirmLogout = true }, enabled = !model.busy) { Text("Выйти") }
                }
                Text("МЕНЬШЕ ШУМА. БОЛЬШЕ ВАС.", fontSize = 10.sp, letterSpacing = 2.sp, color = Green)
                Spacer(Modifier.height(24.dp))
                if (!model.connected) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                        Text("Прошлое —\nна ваш выбор.", fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Spacer(Modifier.height(18.dp))
                        Text("Посмотрите на свои посты свежим взглядом. Оставьте важное, остальное отправьте в очередь на удаление.", color = Ink, lineHeight = 25.sp)
                        Spacer(Modifier.height(32.dp))
                        OutlinedTextField(value = model.clientId, onValueChange = { model.clientId = it }, label = { Text("X OAuth 2.0 Client ID") }, singleLine = true, enabled = !model.busy, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        Text("Для первого запуска: Native App в X Developer Console. Callback: ${Pkce.redirect}", fontSize = 12.sp, color = Green)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = login, enabled = !model.busy, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Подключить X", fontSize = 16.sp) }
                        Text("Только ваши посты. Удаление после подтверждения. После закрытия процесса приложения нужен повторный вход.", modifier = Modifier.padding(top = 16.dp), fontSize = 12.sp, color = Green)
                    }
                } else {
                    Text("Разберите свою историю", fontSize = 29.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("@${model.username} · сначала новые посты", color = Green, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("Просмотрено", model.reviewed)
                        Stat("В очереди", model.queue.size)
                        Stat("Удалено", model.deleted)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val post = model.remaining.firstOrNull()
                        if (post != null) PostCard(post, !model.busy, model::choose)
                        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (model.busy) "Загружаем историю…" else "Эта подборка разобрана", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("Доступная история зависит от ограничений X API.", modifier = Modifier.padding(vertical = 12.dp), fontSize = 13.sp)
                            Button(onClick = model::load, enabled = !model.busy) { Text(if (model.more) "Загрузить следующие" else "Проверить посты") }
                        }
                    }
                    Text("← В очередь                         Оставить →", modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp), fontSize = 12.sp, color = Green)
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { model.choose(true) }, enabled = !model.busy && model.remaining.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Убрать") }
                        Button(onClick = { model.choose(false) }, enabled = !model.busy && model.remaining.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Оставить") }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = model::undo, enabled = !model.busy && model.canUndo) { Text("Отменить свайп") }
                        TextButton(onClick = { showQueue = true }, enabled = !model.busy && model.queue.isNotEmpty()) { Text("Очередь · ${model.queue.size}") }
                    }
                }
                if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                model.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp)) }
                model.notice?.let { Text(it, color = Green, modifier = Modifier.padding(vertical = 12.dp)) }
                Spacer(Modifier.height(12.dp))
            }
        }
        if (showQueue) AlertDialog(onDismissRequest = { showQueue = false }, title = { Text("На удаление: ${model.queue.size}") }, text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text("Проверьте выбранные посты. Можно вернуть любой в разбор.")
                model.queue.toList().forEach { post ->
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text(post.text, maxLines = 4)
                    TextButton(onClick = { model.restore(post) }) { Text("Оставить на проверку") }
                }
            }
        }, confirmButton = { TextButton(onClick = { showQueue = false; confirm = true }, enabled = model.queue.isNotEmpty()) { Text("Удалить выбранные") } }, dismissButton = { TextButton(onClick = { showQueue = false }) { Text("Назад") } })
        if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Удалить ${model.queue.size} постов из X?") }, text = { Text("Это действие необратимо. Свайпы можно отменить только до удаления. При ошибке неудалённые посты останутся в очереди.") }, confirmButton = { TextButton(onClick = { confirm = false; model.deleteConfirmed() }) { Text("Удалить навсегда", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } })
        if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text("Выйти из аккаунта?") }, text = { Text("Очередь и прогресс этой сессии будут сброшены. Посты в X останутся без изменений.") }, confirmButton = { TextButton(onClick = { confirmLogout = false; model.logout() }) { Text("Выйти") } }, dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Отмена") } })
    }
}
@Composable private fun Stat(label: String, count: Int) {
    Column { Text("$count", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink); Text(label, fontSize = 11.sp, color = Green) }
}
@Composable private fun PostCard(post: Post, enabled: Boolean, choose: (Boolean) -> Unit) {
    var drag by remember(post.id) { mutableFloatStateOf(0f) }
    val offset by animateFloatAsState(drag, label = "swipe")
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(3.dp),
        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp).graphicsLayer { translationX = offset; rotationZ = offset / 45f }
            .pointerInput(post.id, enabled) {
                if (enabled) detectHorizontalDragGestures(onDragEnd = {
                    if (kotlin.math.abs(drag) > threshold) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); choose(drag < 0) }
                    drag = 0f
                }, onDragCancel = { drag = 0f }) { change, amount -> change.consume(); drag += amount }
            }) {
        Column(Modifier.padding(24.dp).fillMaxSize()) {
            val date = runCatching { DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(post.date)) }.getOrDefault(post.date)
            Text(date, color = Green, fontSize = 13.sp)
            if (drag < -40) Text("В ОЧЕРЕДЬ", color = Color(0xFFB33C49), fontWeight = FontWeight.Bold)
            if (drag > 40) Text("ОСТАВИТЬ", color = Green, fontWeight = FontWeight.Bold)
            Text(post.text, fontSize = 22.sp, lineHeight = 31.sp, color = Ink, modifier = Modifier.weight(1f).padding(vertical = 20.dp).verticalScroll(rememberScrollState()))
            HorizontalDivider(color = Color(0xFFE4E9E1))
            Text("${post.likes} лайков  ·  ${post.replies} ответов  ·  ${post.reposts} репостов", modifier = Modifier.padding(top = 16.dp), fontSize = 12.sp, color = Green)
        }
    }
}
