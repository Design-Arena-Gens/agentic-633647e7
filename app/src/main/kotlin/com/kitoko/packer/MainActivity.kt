package com.kitoko.packer

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitoko.packer.data.AuthRepository
import com.kitoko.packer.data.PackedOrderStore
import com.kitoko.packer.data.PackingLogRepository
import com.kitoko.packer.ui.KitokoPackerApp
import com.kitoko.packer.viewmodel.KitokoViewModel
import com.kitoko.packer.viewmodel.KitokoViewModelFactory
import com.kitoko.packer.ui.theme.KitokoTheme
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var textToSpeech: TextToSpeech
    private val ttsReady = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        textToSpeech = TextToSpeech(this, this)

        val viewModelFactory = KitokoViewModelFactory(
            application = application,
            authRepository = AuthRepository(),
            packedOrderStore = PackedOrderStore(applicationContext),
            packingLogRepository = PackingLogRepository()
        )

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
            }
        }

        permissionLauncher.launch(Manifest.permission.CAMERA)

        setContent {
            val viewModel = viewModel<KitokoViewModel>(factory = viewModelFactory)
            val uiState by viewModel.uiState.collectAsState()
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            val context = LocalContext.current

            DisposableEffect(uiState.showPackedOverlay) {
                if (uiState.showPackedOverlay && ttsReady.value) {
                    speak("Order packed")
                }
                onDispose { }
            }

            LaunchedEffect(uiState.snackbarMessage) {
                uiState.snackbarMessage?.let { message ->
                    snackbarHostState.showSnackbar(message)
                    viewModel.consumeMessage()
                }
            }

            LaunchedEffect(uiState.showPackedOverlay) {
                if (uiState.showPackedOverlay) {
                    delay(2000)
                    viewModel.consumeOverlay()
                }
            }

            KitokoTheme {
                KitokoPackerApp(
                    uiState = uiState,
                    onBarcodeDetected = viewModel::onBarcodeScanned,
                    onSignIn = { email, password ->
                        scope.launch {
                            runCatching { viewModel.signIn(email, password) }
                                .onFailure { snackbarHostState.showSnackbar(it.message ?: "Authentication failed") }
                        }
                    },
                    onSignOut = { viewModel.signOut() },
                    onExportCsv = {
                        scope.launch {
                            runCatching {
                                val exportDir = File(context.cacheDir, "export")
                                val csvFile = viewModel.exportCsv(exportDir)
                                shareCsv(csvFile)
                                snackbarHostState.showSnackbar("CSV exported")
                            }.onFailure { error ->
                                snackbarHostState.showSnackbar(error.message ?: "Export failed")
                            }
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                )
            }
        }
    }

    override fun onDestroy() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale.US
            ttsReady.value = true
        }
    }

    private fun speak(text: String) {
        if (::textToSpeech.isInitialized) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kitoko_packer")
        }
    }

    private fun shareCsv(file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            this,
            "com.kitoko.packer.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.action_export_csv)))
    }
}
