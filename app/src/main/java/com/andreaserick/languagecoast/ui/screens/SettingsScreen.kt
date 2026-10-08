package com.andreaserick.languagecoast.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreaserick.languagecoast.data.SettingsManager
import com.andreaserick.languagecoast.ui.theme.SandBeige
import kotlinx.coroutines.launch

/**
 * The "Settings" screen. Configures native/target languages, the Gemini model,
 * and the user's Google Gemini API key.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val coroutineScope = rememberCoroutineScope()

    val nativeLang by settingsManager.nativeLanguageFlow.collectAsState(initial = SettingsManager.DEFAULT_NATIVE_LANGUAGE)
    val targetLang by settingsManager.targetLanguageFlow.collectAsState(initial = SettingsManager.DEFAULT_TARGET_LANGUAGE)
    val savedGeminiModel by settingsManager.geminiModelFlow.collectAsState(initial = SettingsManager.DEFAULT_GEMINI_MODEL)
    val savedApiKey by settingsManager.apiKeyFlow.collectAsState(initial = "")

    var currentInput by remember(savedApiKey) { mutableStateOf(savedApiKey) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showSuccessMessage by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Settings",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        SectionLabel("Native Language")
        SettingsDropdown(
            options = SettingsManager.SUPPORTED_LANGUAGES,
            selected = nativeLang,
            onSelected = { coroutineScope.launch { settingsManager.saveNativeLanguage(it) } }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel("Target Language")
        SettingsDropdown(
            options = SettingsManager.SUPPORTED_LANGUAGES,
            selected = targetLang,
            onSelected = { coroutineScope.launch { settingsManager.saveTargetLanguage(it) } }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel("Gemini Model")
        SettingsDropdown(
            options = SettingsManager.GEMINI_MODELS,
            selected = savedGeminiModel,
            onSelected = { coroutineScope.launch { settingsManager.saveGeminiModel(it) } }
        )

        Spacer(modifier = Modifier.height(40.dp))

        SectionLabel("Google Gemini API")
        Text(
            text = "Enter your own API key from Google AI Studio to power translations and generation.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = currentInput,
            onValueChange = {
                currentInput = it
                showSuccessMessage = false
            },
            label = { Text("API Key") },
            placeholder = { Text("AIzaSy...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = {
                Icon(Icons.Default.Key, contentDescription = null)
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide API key" else "Show API key"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    settingsManager.saveApiKey(currentInput.trim())
                    showSuccessMessage = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Save API Key", fontSize = 16.sp)
        }

        if (showSuccessMessage) {
            Text(
                text = "API Key saved successfully!",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = SandBeige,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/**
 * A read-only dropdown for picking one value from [options].
 *
 * @param options The values to choose from.
 * @param selected The currently selected value.
 * @param onSelected Called when the user picks a value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdown(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
