package com.kitoko.packer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kitoko.packer.model.ChecklistEntry
import com.kitoko.packer.model.PackingPhase
import com.kitoko.packer.ui.components.BarcodeScannerPreview
import com.kitoko.packer.viewmodel.KitokoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitokoPackerApp(
    uiState: KitokoViewModel.UiState,
    onBarcodeDetected: (String) -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onExportCsv: () -> Unit,
    snackbarHost: @Composable () -> Unit
) {
    if (!uiState.isSignedIn) {
        AuthScreen(onSignIn = onSignIn)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kitoko Packer") },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                    }
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = uiState.phase is PackingPhase.Completed && !uiState.exportInProgress,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FloatingActionButton(onClick = onExportCsv) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Export CSV")
                }
            }
        },
        snackbarHost = snackbarHost
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val phase = uiState.phase) {
                PackingPhase.AwaitingInvoice -> AwaitingInvoiceScreen(onBarcodeDetected)
                is PackingPhase.ReadyToPack -> PackingScreen(phase, onBarcodeDetected)
                is PackingPhase.Completed -> CompletedScreen(phase, onBarcodeDetected)
            }

            AnimatedVisibility(
                visible = uiState.showPackedOverlay,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                PackedOverlay(uiState.phase)
            }

            if (uiState.exportInProgress) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun AuthScreen(onSignIn: (String, String) -> Unit) {
    val emailState = remember { mutableStateOf("") }
    val passwordState = remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Kitoko Packer",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = emailState.value,
            onValueChange = { emailState.value = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = passwordState.value,
            onValueChange = { passwordState.value = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (emailState.value.isNotBlank() && passwordState.value.isNotBlank()) {
                    onSignIn(emailState.value, passwordState.value)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign In")
        }
    }
}

@Composable
private fun AwaitingInvoiceScreen(onBarcodeDetected: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Scan invoice QR",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        BarcodeScannerPreview(onBarcodeDetected = onBarcodeDetected)
    }
}

@Composable
private fun PackingScreen(
    phase: PackingPhase.ReadyToPack,
    onBarcodeDetected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Order ${phase.orderId}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Progress: ${phase.scannedCount}/${phase.totalRequired}",
            style = MaterialTheme.typography.bodyLarge
        )
        BarcodeScannerPreview(onBarcodeDetected = onBarcodeDetected)
        Checklist(entries = phase.checklist)
    }
}

@Composable
private fun CompletedScreen(
    phase: PackingPhase.Completed,
    onBarcodeDetected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Order ${phase.orderId} packed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        BarcodeScannerPreview(onBarcodeDetected = onBarcodeDetected)
        Checklist(entries = phase.checklist)
    }
}

@Composable
private fun Checklist(entries: List<ChecklistEntry>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(entries) { entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (entry.isComplete) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = entry.sku,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${entry.scanned}/${entry.required} scanned",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun PackedOverlay(phase: PackingPhase) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Order Packed",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (phase is PackingPhase.Completed) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = phase.orderId,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }
        }
    }
}
