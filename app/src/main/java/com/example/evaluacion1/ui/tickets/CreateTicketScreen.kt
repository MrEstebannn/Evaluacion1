package com.example.evaluacion1.ui.tickets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.evaluacion1.model.Category
import com.example.evaluacion1.model.Priority
import com.example.evaluacion1.model.User
import com.example.evaluacion1.ui.components.ValidatedTextField
import com.example.evaluacion1.ui.components.label
import com.example.evaluacion1.ui.components.message
import com.example.evaluacion1.viewmodel.CreateTicketViewModel
import com.example.evaluacion1.viewmodel.FieldError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTicketScreen(
    user: User,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CreateTicketViewModel = viewModel(factory = CreateTicketViewModel.factory(user))
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdTicketId) {
        state.createdTicketId?.let(onCreated)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nueva solicitud") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ValidatedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = "Título",
                error = state.titleError,
                hint = "Mínimo ${CreateTicketViewModel.TITLE_MIN} caracteres"
            )
            ValidatedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = "Descripción",
                error = state.descriptionError,
                hint = "${state.description.length}/${CreateTicketViewModel.DESCRIPTION_MAX}",
                singleLine = false,
                minLines = 4
            )

            ChoiceGroup(
                title = "Categoría",
                options = Category.entries,
                selected = state.category,
                label = { it.label() },
                onSelect = viewModel::onCategoryChange
            )
            ChoiceGroup(
                title = "Prioridad",
                options = Priority.entries,
                selected = state.priority,
                label = { it.label() },
                onSelect = viewModel::onPriorityChange
            )
            ChoiceGroup(
                title = "Sucursal",
                options = CreateTicketViewModel.BRANCHES,
                selected = state.branch.ifEmpty { null },
                label = { it },
                onSelect = viewModel::onBranchChange,
                error = state.branchError
            )

            state.submitError?.let { error ->
                Text(
                    text = error.message(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = viewModel::submit,
                enabled = !state.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current
                    )
                } else {
                    Text("Crear solicitud")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceGroup(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    error: FieldError? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) }
                )
            }
        }
        error?.let {
            Text(
                text = it.message(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
