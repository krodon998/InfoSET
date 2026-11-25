package me.rerere.rikkahub.ui.pages.desktop

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.MediaStore
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Camera01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.utils.getActivity
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LockBlue = Color(0xFF58A9C8)
private val LockInk = Color(0xFF327F9E)

@Composable
fun InfoSetLockScreenPage(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val unlockThresholdPx = with(density) { 88.dp.toPx() }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta ->
        dragOffsetY = (dragOffsetY + delta).coerceAtMost(0f)
    }

    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(1_000)
        }
    }

    val locale = Locale.getDefault()
    val timePattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val datePattern = remember(locale) {
        DateFormat.getBestDateTimePattern(locale, "MMMMdEEEE")
    }
    val timeText = remember(now, timePattern, locale) {
        now.format(DateTimeFormatter.ofPattern(timePattern, locale))
    }
    val dateText = remember(now, datePattern, locale) {
        now.format(DateTimeFormatter.ofPattern(datePattern, locale))
    }

    val cameraManager = remember {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    val torchCameraId = remember(cameraManager) {
        runCatching {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }
    var torchEnabled by remember { mutableStateOf(false) }
    val currentTorchEnabled by rememberUpdatedState(torchEnabled)

    fun setTorch(enabled: Boolean) {
        val cameraId = torchCameraId ?: return
        runCatching { cameraManager.setTorchMode(cameraId, enabled) }
            .onSuccess { torchEnabled = enabled }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) setTorch(true)
    }

    DisposableEffect(cameraManager, torchCameraId) {
        onDispose {
            if (currentTorchEnabled) setTorch(false)
        }
    }

    val activity = view.context.getActivity()
    if (!view.isInEditMode && activity != null) {
        DisposableEffect(view, activity) {
            val controller = WindowCompat.getInsetsController(activity.window, view)
            val previousLightStatusBars = controller.isAppearanceLightStatusBars
            val previousLightNavigationBars = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
            onDispose {
                controller.isAppearanceLightStatusBars = previousLightStatusBars
                controller.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
        }
    }

    val unlockLabel = stringResource(R.string.infoset_lock_swipe_up)
    val flashlightLabel = stringResource(R.string.infoset_lock_flashlight)
    val cameraLabel = stringResource(R.string.infoset_lock_camera)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFFDDF2FC),
                        0.44f to Color(0xFFF9FDFF),
                        1f to Color(0xFFBDE9FB),
                    )
                )
            )
            .graphicsLayer { translationY = dragOffsetY }
            .draggable(
                orientation = Orientation.Vertical,
                state = dragState,
                onDragStopped = {
                    if (dragOffsetY <= -unlockThresholdPx) {
                        dragOffsetY = 0f
                        onUnlock()
                    } else {
                        animate(
                            initialValue = dragOffsetY,
                            targetValue = 0f,
                        ) { value, _ -> dragOffsetY = value }
                    }
                }
            )
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(260.dp)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.72f),
                        Color.Transparent,
                    )
                ),
                radius = size.minDimension / 2f,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LockGlyph(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(23.dp),
            )

            Text(
                text = dateText,
                modifier = Modifier.padding(top = 28.dp),
                color = LockInk.copy(alpha = 0.82f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )

            Text(
                text = timeText,
                modifier = Modifier.padding(top = 2.dp),
                color = LockBlue,
                fontSize = 78.sp,
                lineHeight = 82.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-2).sp,
            )

            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.48f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                color = LockInk.copy(alpha = 0.74f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp,
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LockShortcutButton(
                    label = flashlightLabel,
                    active = torchEnabled,
                    onClick = {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA,
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            setTorch(!torchEnabled)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                ) {
                    FlashlightGlyph(
                        modifier = Modifier.size(23.dp),
                        color = if (torchEnabled) Color.White else LockInk,
                    )
                }

                LockShortcutButton(
                    label = cameraLabel,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                            )
                        }
                    },
                ) {
                    Icon(
                        imageVector = HugeIcons.Camera01,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = LockInk,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(top = 22.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .semantics(mergeDescendants = true) {
                        contentDescription = unlockLabel
                    }
                    .clickable(role = Role.Button, onClick = onUnlock)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = unlockLabel,
                    color = LockInk.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .size(width = 112.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(LockInk.copy(alpha = 0.64f))
                )
            }
        }
    }
}

@Composable
private fun LockShortcutButton(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(
                if (active) LockBlue.copy(alpha = 0.9f)
                else Color.White.copy(alpha = 0.52f)
            )
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun LockGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.09f
        drawArc(
            color = LockInk.copy(alpha = 0.78f),
            startAngle = 190f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height * 0.04f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.5f, size.height * 0.58f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawRoundRect(
            color = LockInk.copy(alpha = 0.78f),
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.16f, size.height * 0.42f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.68f, size.height * 0.48f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.12f),
        )
    }
}

@Composable
private fun FlashlightGlyph(
    modifier: Modifier = Modifier,
    color: Color,
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.085f
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.3f, size.height * 0.18f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.7f, size.height * 0.18f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.25f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.46f, size.height * 0.45f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.25f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.54f, size.height * 0.45f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.41f, size.height * 0.42f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.18f, size.height * 0.42f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.07f),
        )
    }
}
