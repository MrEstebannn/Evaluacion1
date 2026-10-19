package com.example.evaluacion1.ui.components

import com.example.evaluacion1.model.Category
import com.example.evaluacion1.model.HistoryEntry
import com.example.evaluacion1.model.HistoryType
import com.example.evaluacion1.model.Priority
import com.example.evaluacion1.model.Role
import com.example.evaluacion1.model.TicketStatus
import com.example.evaluacion1.repository.AppError
import com.example.evaluacion1.viewmodel.DetailMessage
import com.example.evaluacion1.viewmodel.FieldError
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun TicketStatus.label(): String = when (this) {
    TicketStatus.NEW -> "Nuevo"
    TicketStatus.ASSIGNED -> "Asignado"
    TicketStatus.IN_PROGRESS -> "En progreso"
    TicketStatus.ON_HOLD -> "En espera"
    TicketStatus.RESOLVED -> "Resuelto"
    TicketStatus.CLOSED -> "Cerrado"
}

fun Priority.label(): String = when (this) {
    Priority.LOW -> "Baja"
    Priority.MEDIUM -> "Media"
    Priority.HIGH -> "Alta"
    Priority.CRITICAL -> "Crítica"
}

fun Category.label(): String = when (this) {
    Category.STORE -> "Tienda"
    Category.PHARMACY -> "Farmacia"
    Category.SUPPLIER -> "Proveedor"
    Category.INFRASTRUCTURE -> "Infraestructura"
    Category.INTERNAL_SUPPORT -> "Soporte interno"
    Category.LEGAL -> "Legal"
}

fun Role.label(): String = when (this) {
    Role.REQUESTER -> "Solicitante"
    Role.OPERATOR -> "Resolutor"
    Role.SUPERVISOR -> "Supervisor"
    Role.MANAGEMENT -> "Gerencia"
    Role.ADMIN -> "Administrador"
}

fun FieldError.message(): String = when (this) {
    FieldError.Required -> "Este campo es obligatorio"
    FieldError.InvalidEmail -> "Ingresa un correo válido"
    is FieldError.TooShort -> "Mínimo $min caracteres"
    is FieldError.TooLong -> "Máximo $max caracteres"
}

fun AppError.message(): String = when (this) {
    AppError.INVALID_CREDENTIALS -> "Correo o contraseña incorrectos"
    AppError.INACTIVE_USER -> "Tu usuario está inactivo"
    AppError.PERMISSION_DENIED -> "No tienes permisos para esta acción"
    AppError.NOT_FOUND -> "No se encontró el registro"
    AppError.INVALID_TRANSITION -> "Ese cambio de estado no está permitido"
    AppError.INVALID_INPUT -> "Revisa los datos ingresados"
}

fun DetailMessage.text(): String = when (this) {
    DetailMessage.STATUS_UPDATED -> "Estado actualizado"
    DetailMessage.ASSIGNED -> "Responsable asignado"
    DetailMessage.COMMENT_ADDED -> "Comentario agregado"
    DetailMessage.NO_PERMISSION -> "No tienes permisos para esta acción"
    DetailMessage.INVALID_TRANSITION -> "Ese cambio de estado no está permitido"
    DetailMessage.INVALID_INPUT -> "Revisa los datos ingresados"
    DetailMessage.NOT_FOUND -> "No se encontró la solicitud"
}

fun HistoryEntry.describe(): String = when (type) {
    HistoryType.CREATED -> "$actorName creó la solicitud"
    HistoryType.ASSIGNED ->
        if (detail == actorName) "$actorName tomó la solicitud" else "$actorName asignó la solicitud a $detail"

    HistoryType.STATUS_CHANGED -> {
        val parts = detail.split(">")
        if (parts.size == 2) {
            "$actorName cambió el estado de ${parts[0].statusLabel()} a ${parts[1].statusLabel()}"
        } else {
            "$actorName cambió el estado"
        }
    }
}

private fun String.statusLabel(): String =
    runCatching { TicketStatus.valueOf(this).label() }.getOrDefault(this)

fun formatDateTime(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.forLanguageTag("es-CL")).format(Date(millis))
