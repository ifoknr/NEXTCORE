/*
 * Copyright (C) 2026-2027 NextCore
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zx.azenith.ui.component


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import zx.azenith.R


/**
 * Section title with a help button that opens a short sheet explaining what
 * the section shows and where the values come from, like RvSystem Monitor's
 * help sheets. [help] is plain text; blank lines separate paragraphs.
 */
@Composable
fun NcHelpSection(
    text: String,
    help: String,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NcSheetSection(text, Modifier.weight(1f))
        IconButton(onClick = { open = true }, modifier = Modifier.padding(top = 10.dp).size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Rounded.HelpOutline,
                contentDescription = stringResource(R.string.nc_help),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    RootAppDialog {
        NcHelpSheet(visible = open, title = text, body = help, onDismiss = { open = false })
    }
}

@Composable
fun NcHelpSheet(visible: Boolean, title: String, body: String, onDismiss: () -> Unit) {
    CustomBottomSheet(visible = visible, onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            body.split("\n\n").forEach { paragraph ->
                Text(
                    paragraph.trim(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            FilledTonalButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.nc_help_ok))
            }
        }
    }
}
