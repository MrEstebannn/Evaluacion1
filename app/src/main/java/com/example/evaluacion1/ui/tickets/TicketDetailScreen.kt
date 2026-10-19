package com.example.evaluacion1.ui.tickets

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.evaluacion1.model.Comment
import com.example.evaluacion1.model.HistoryEntry
import com.example.evaluacion1.model.Ticket
import com.example.evaluacion1.model.User
import com.example.evaluacion1.ui.components.PriorityChip
import com.example.evaluacion1.ui.components.StatusChip
import com.example.evaluacion1.ui.components.ValidatedTextField
import com.example.evaluacion1.ui.components.describe
import com.example.evaluacion1.ui.components.formatDateTime
import com.example.evaluacion1.ui.components.label
import com.example.evaluacion1.ui.components.text
import com.example.evaluacion1.viewmodel.TicketDetailUiState
import com.example.evaluacion1.viewmodel.TicketDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(
    user: User,
    ticketId: String,
    onBack: () -> Unit,
    viewModel: TicketDetailViewModel = viewModel(
        factory = TicketDetailViewModel.factory(user, ticketId)
    )
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val form = state.form

    // Show the feedback first and consume it afterwards (consuming cancels this effect).
    LaunchedEffect(form.message) {
        form.message?.let { message ->
            snackbarHostState.showSnackbar(message.text())
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ticketId) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val ticket = state.ticket
        when {
            state.isLoading -> CenteredBox(Modifier.padding(padding)) { CircularProgressIndicator() }
            ticket == null -> CenteredBox(Modifier.padding(padding)) {
                Text("No se encontró la solicitud o no tienes acceso.")
            }

            else -> DetailContent(
                ticket = ticket,
                state = state,
                viewModel = viewModel,
                modifier = Modifier.padding(padding)
            )
        }
    }

    form.pendingStatus?.let { status ->
        AlertDialog(
            onDismissRequest = viewModel::cancelStatusChange,
            title = { Text("Pasar a ${status.label()}") },
            text = {
                ValidatedTextField(
                    value = form.note,
                    onValueChange = viewModel::onNoteChange,
                    label = "Nota del cambio",
                    error = form.noteError,
                    hint = "Obligatoria, mínimo 5 caracteres",
                    singleLine = false,
                    minLines = 2
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmStatusChange, enabled = !form.isBusy) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelStatusChange) { Text("Cancelar") }
            }
        )
    }

    if (form.showAssignDialog) {
        AlertDialog(
            onDismissRequest = viewModel::closeAssignDialog,
            title = { Text("Asignar responsable") },
            text = {
                Column {
                    state.operators.forEach { operator ->
                        TextButton(
                            onClick = { viewModel.assign(operator) },
                            enabled = !form.isBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(operator.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::closeAssignDialog) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    ticket: Ticket,
    state: TicketDetailUiState,
    viewModel: TicketDetailViewModel,
    modifier: Modifier = Modifier
) {
    val form = state.form

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") { HeaderCard(ticket) }

        if (state.availableStatuses.isNotEmpty() || state.canAssign) {
            item(key = "actions") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Acciones", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.availableStatuses.forEach { status ->
                                Button(
                                    onClick = { viewModel.startStatusChange(status) },
                                    enabled = !form.isBusy
                                ) { Text("Pasar a ${status.label()}") }
                            }
                            if (state.canAssign) {
                                OutlinedButton(
                                    onClick = viewModel::openAssignDialog,
                                    enabled = !form.isBusy
                                ) { Text("Asignar responsable") }
                            }
                        }
                    }
                }
            }
        }

        item(key = "comments-title") {
            Text("Comentarios (${state.comments.size})", style = MaterialTheme.typography.titleMedium)
        }
        if (state.comments.isEmpty()) {
            item(key = "comments-empty") {
                Text(
                    text = "Aún no hay comentarios.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(state.comments, key = { "comment-${it.id}" }) { comment ->
                CommentCard(comment, Modifier.animateItem())
            }
        }
        if (state.canComment) {
            item(key = "comment-input") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    ValidatedTextField(
                        value = form.commentText,
                        onValueChange = viewModel::onCommentChange,
                        label = "Escribe un comentario",
                        error = form.commentError,
                        hint = "${form.commentText.length}/${TicketDetailViewModel.COMMENT_MAX}",
                        singleLine = false,
                        minLines = 2
                    )
                    Button(onClick = viewModel::sendComment, enabled = !form.isBusy) {
                        Text("Enviar")
                    }
                }
            }
        }

        item(key = "history-title") {
            Text("Historial", style = MaterialTheme.typography.titleMedium)
        }
        items(state.history, key = { "history-${it.id}" }) { entry ->
            HistoryRow(entry, Modifier.animateItem())
        }
    }
}

@Composable
private fun HeaderCard(ticket: Ticket) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedContent(targetState = ticket.status, label = "status") { status ->
                    StatusChip(status)
                }
                PriorityChip(ticket.priority)
            }
            Text(ticket.title, style = MaterialTheme.typography.headlineSmall)
            Text(ticket.description, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            InfoRow("Categoría", ticket.category.label())
            InfoRow("Sucursal", ticket.branch)
            InfoRow("Solicitante", ticket.requesterName)
            InfoRow("Responsable", ticket.assigneeName ?: "Sin asignar")
            InfoRow("Creada", formatDateTime(ticket.createdAt))
            InfoRow("Actualizada", formatDateTime(ticket.updatedAt))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CommentCard(comment: Comment, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(comment.authorName, style = MaterialTheme.typography.labelLarge)
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = formatDateTime(comment.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(entry.describe(), style = MaterialTheme.typography.bodyMedium)
        entry.note?.let { note ->
            Text(
                text = "“$note”",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic
            )
        }
        Text(
            text = formatDateTime(entry.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CenteredBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
