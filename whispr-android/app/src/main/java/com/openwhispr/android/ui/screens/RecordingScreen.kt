package com.openwhispr.android.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.openwhispr.android.api.GroqApiClient
import com.openwhispr.android.audio.AudioRecorder
import com.openwhispr.android.data.SettingsRepository
import com.openwhispr.android.ui.theme.RecordingRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class RecState { IDLE, RECORDING, TRANSCRIBING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(onNavigateToSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val settingsRepo = remember { SettingsRepository(context) }
    val apiKey by settingsRepo.groqApiKey.collectAsState(initial = "")
    val whisperModel by settingsRepo.whisperModel.collectAsState(initial = SettingsRepository.DEFAULT_MODEL)

    var state by remember { mutableStateOf(RecState.IDLE) }
    var transcription by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var recordingSeconds by remember { mutableLongStateOf(0L) }

    val snackbarHostState = remember { SnackbarHostState() }
    val recorder = remember { AudioRecorder(context) }

    // Clean up recorder on dispose
    DisposableEffect(Unit) {
        onDispose { recorder.release() }
    }

    // Recording timer
    LaunchedEffect(state) {
        if (state == RecState.RECORDING) {
            recordingSeconds = 0L
            while (true) {
                delay(1_000)
                recordingSeconds++
            }
        }
    }

    // Permission launcher
    var pendingAction by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingAction) {
            pendingAction = false
            try {
                recorder.start()
                state = RecState.RECORDING
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = "Mic error: ${e.message}"
            }
        } else if (!granted) {
            errorMessage = "Microphone permission is required"
        }
    }

    fun startRecording() {
        if (apiKey.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("Set your Groq API key in Settings first") }
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            pendingAction = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        try {
            recorder.start()
            state = RecState.RECORDING
            errorMessage = null
            transcription = ""
        } catch (e: Exception) {
            errorMessage = "Mic error: ${e.message}"
        }
    }

    fun stopRecording() {
        val audioFile = recorder.stop()
        if (audioFile == null || !audioFile.exists()) {
            state = RecState.IDLE
            errorMessage = "Recording failed"
            return
        }

        state = RecState.TRANSCRIBING
        scope.launch {
            GroqApiClient(apiKey).transcribe(audioFile, whisperModel)
                .onSuccess { text ->
                    transcription = text
                    clipboardManager.setText(AnnotatedString(text))
                    state = RecState.IDLE
                    errorMessage = null
                    snackbarHostState.showSnackbar("Copied to clipboard")
                    audioFile.delete()
                }
                .onFailure { e ->
                    state = RecState.IDLE
                    errorMessage = e.message ?: "Transcription failed"
                    audioFile.delete()
                }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Whispr") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Record button
            when (state) {
                RecState.TRANSCRIBING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(120.dp),
                        strokeWidth = 6.dp,
                    )
                }
                else -> {
                    RecordButton(
                        isRecording = state == RecState.RECORDING,
                        onClick = {
                            when (state) {
                                RecState.RECORDING -> stopRecording()
                                RecState.IDLE -> startRecording()
                                else -> {}
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Status text
            Text(
                text = when (state) {
                    RecState.IDLE -> if (transcription.isNotEmpty()) "Tap to record again" else "Tap to record"
                    RecState.RECORDING -> formatDuration(recordingSeconds)
                    RecState.TRANSCRIBING -> "Transcribing..."
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (state == RecState.RECORDING) RecordingRed
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Error
            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            // Last transcription
            if (transcription.isNotEmpty() && state != RecState.TRANSCRIBING) {
                Spacer(Modifier.height(32.dp))
                Text(
                    text = transcription,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun RecordButton(isRecording: Boolean, onClick: () -> Unit) {
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val scale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(120.dp)
            .scale(if (isRecording) scale else 1f)
            .clip(CircleShape)
            .background(
                if (isRecording) RecordingRed
                else MaterialTheme.colorScheme.primaryContainer
            )
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (isRecording) "Stop recording" else "Start recording",
            modifier = Modifier.size(48.dp),
            tint = if (isRecording) MaterialTheme.colorScheme.onError
                   else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
