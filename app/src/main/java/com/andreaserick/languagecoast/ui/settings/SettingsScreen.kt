package com.andreaserick.languagecoast.ui.settings

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreaserick.languagecoast.data.Languages
import com.andreaserick.languagecoast.data.SettingsDefaults
import com.andreaserick.languagecoast.ui.components.LanguageField
import com.andreaserick.languagecoast.ui.components.SelectionDropdown
import com.andreaserick.languagecoast.ui.theme.SandBeige

/**
 * The "Settings" screen. Configures the native language, the Gemini model,
 * and the user's Google Gemini API key.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var currentInput by remember(uiState.savedApiKey) { mutableStateOf(uiState.savedApiKey) }
    var passwordVisible by remember { mutableStateOf(false) }

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
        LanguageField(
            language = uiState.nativeLanguage,
            languages = uiState.nativeLanguageOptions,
            onPick = { viewModel.setNativeLanguage(it.name) },
            pickerTitle = "Native Language",
            pickerSubtitle = "Languages you study on a coast aren't listed."
                .takeIf { uiState.nativeLanguageOptions.size < Languages.ALL.size }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel("Gemini Model")
        SelectionDropdown(
            options = SettingsDefaults.GEMINI_MODELS,
            selected = uiState.geminiModel,
            onSelected = viewModel::setGeminiModel
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
                viewModel.onApiKeyEdited()
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
            onClick = { viewModel.saveApiKey(currentInput) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Save API Key", fontSize = 16.sp)
        }

        if (uiState.apiKeySaved) {
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
