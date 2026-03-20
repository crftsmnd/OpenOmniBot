package cn.com.omnimind.bot.agent

internal const val MAX_TERMINAL_AUTO_RETRIES = 3

internal data class TerminalRetryState(
    val retryCount: Int = 0,
    val lastTerminalFailed: Boolean = false,
    val budgetExhausted: Boolean = false
) {
    val remainingRetries: Int
        get() = (MAX_TERMINAL_AUTO_RETRIES - retryCount).coerceAtLeast(0)
}

internal data class TerminalRetryDecision(
    val shouldExecute: Boolean,
    val nextState: TerminalRetryState,
    val isRetryAttempt: Boolean,
    val rejectReason: String? = null
)

internal object TerminalRetryPolicy {

    fun beforeTerminalExecution(state: TerminalRetryState): TerminalRetryDecision {
        if (state.budgetExhausted) {
            return TerminalRetryDecision(
                shouldExecute = false,
                nextState = state.copy(lastTerminalFailed = false, budgetExhausted = true),
                isRetryAttempt = false,
                rejectReason = "terminal_retry_budget_exhausted"
            )
        }

        if (!state.lastTerminalFailed) {
            return TerminalRetryDecision(
                shouldExecute = true,
                nextState = state,
                isRetryAttempt = false
            )
        }

        if (state.retryCount >= MAX_TERMINAL_AUTO_RETRIES) {
            return TerminalRetryDecision(
                shouldExecute = false,
                nextState = state.copy(lastTerminalFailed = false, budgetExhausted = true),
                isRetryAttempt = false,
                rejectReason = "terminal_retry_budget_exhausted"
            )
        }

        return TerminalRetryDecision(
            shouldExecute = true,
            nextState = state.copy(retryCount = state.retryCount + 1),
            isRetryAttempt = true
        )
    }

    fun afterTerminalResult(
        state: TerminalRetryState,
        success: Boolean
    ): TerminalRetryState {
        return if (success) {
            TerminalRetryState()
        } else {
            state.copy(lastTerminalFailed = true)
        }
    }
}
