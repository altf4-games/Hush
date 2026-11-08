package com.altf4.hush.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Outcome of checking what was typed: the cleaned [value] to store, or an [error] to show. */
class EditorResult(val value: String?, val error: String? = null)

/**
 * A card with a text box to add entries and a removable list of the ones already added. What
 * counts as valid is decided by [validate], so it serves both domains and patterns.
 */
@Composable
fun DomainEditor(
    description: String,
    hint: String,
    items: Set<String>,
    validate: (String) -> EditorResult,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Uri,
    monospace: Boolean = false,
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val result = validate(text)
        val value = result.value
        if (value == null) {
            error = result.error ?: "Not valid"
        } else {
            onAdd(value)
            text = ""
            error = null
        }
    }

    HushCard {
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(hint) },
                isError = error != null,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { submit() }) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
        }
        error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
        items.sorted().forEach { entry ->
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemove(entry) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove $entry")
                }
            }
        }
    }
}
