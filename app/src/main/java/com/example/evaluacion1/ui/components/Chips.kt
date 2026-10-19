package com.example.evaluacion1.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.evaluacion1.model.Priority
import com.example.evaluacion1.model.TicketStatus
import com.example.evaluacion1.ui.theme.PriorityCritical
import com.example.evaluacion1.ui.theme.PriorityHigh
import com.example.evaluacion1.ui.theme.PriorityLow
import com.example.evaluacion1.ui.theme.PriorityMedium
import com.example.evaluacion1.ui.theme.StatusAssigned
import com.example.evaluacion1.ui.theme.StatusClosed
import com.example.evaluacion1.ui.theme.StatusInProgress
import com.example.evaluacion1.ui.theme.StatusNew
import com.example.evaluacion1.ui.theme.StatusOnHold
import com.example.evaluacion1.ui.theme.StatusResolved

@Composable
fun StatusChip(status: TicketStatus, modifier: Modifier = Modifier) {
    TagChip(text = status.label(), color = status.color(), modifier = modifier)
}

@Composable
fun PriorityChip(priority: Priority, modifier: Modifier = Modifier) {
    TagChip(text = priority.label(), color = priority.color(), modifier = modifier)
}

@Composable
private fun TagChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.16f),
        contentColor = color
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private fun TicketStatus.color(): Color = when (this) {
    TicketStatus.NEW -> StatusNew
    TicketStatus.ASSIGNED -> StatusAssigned
    TicketStatus.IN_PROGRESS -> StatusInProgress
    TicketStatus.ON_HOLD -> StatusOnHold
    TicketStatus.RESOLVED -> StatusResolved
    TicketStatus.CLOSED -> StatusClosed
}

private fun Priority.color(): Color = when (this) {
    Priority.LOW -> PriorityLow
    Priority.MEDIUM -> PriorityMedium
    Priority.HIGH -> PriorityHigh
    Priority.CRITICAL -> PriorityCritical
}
