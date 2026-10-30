package com.example.saferoute.ui.viewmodel

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Error(val message: String) : AuthUiState
}

enum class SosPhase {
    COUNTDOWN,
    PERMISSION_RATIONALE,
    SENDING,
    RESULT
}

enum class SosStepStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED
}

data class SosStep(
    val id: String,
    val title: String,
    val status: SosStepStatus = SosStepStatus.PENDING,
    val detail: String? = null
)

data class SosUiState(
    val phase: SosPhase = SosPhase.COUNTDOWN,
    val steps: List<SosStep> = emptyList(),
    val summary: String? = null,
    val isSending: Boolean = false,
    val canRetry: Boolean = false,
    val alertStarted: Boolean = false
)
