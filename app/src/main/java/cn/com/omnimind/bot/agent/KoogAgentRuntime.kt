package cn.com.omnimind.bot.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import cn.com.omnimind.baselib.llm.AssistantToolCall
import cn.com.omnimind.baselib.llm.ChatCompletionMessage
import cn.com.omnimind.baselib.llm.ChatCompletionRequest
import cn.com.omnimind.baselib.llm.ChatCompletionStreamOptions
import cn.com.omnimind.baselib.llm.ModelProviderConfigStore
import cn.com.omnimind.baselib.llm.ModelSceneRegistry
import cn.com.omnimind.baselib.llm.SceneModelBindingStore
import cn.com.omnimind.baselib.llm.contentText
import cn.com.omnimind.baselib.util.OmniLog
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

class KoogAgentRuntime(
    private val llmClient: AgentLlmClient,
    private val toolRegistry: AgentToolRegistry,
    private val toolRouter: AgentToolRouter,
    private val eventAdapter: AgentEventAdapter,
    private val model: String,
    private val modelOverride: AgentModelOverride? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }
) {
    data class Input(
        val callback: AgentCallback,
        val initialMessages: List<ChatCompletionMessage>,
        val executionEnv: AgentToolRouter.ExecutionEnvironment,
    )

    private val tag = "KoogAgentRuntime"
    private val koogBootstrap by lazy { resolveKoogBootstrap() }

    suspend fun run(input: Input): AgentResult {
        val planService = AgentPlanningService(
            llmClient = llmClient,
            model = model,
            json = json,
            modelOverride = modelOverride,
        )
        val koogToolSession = OmniKoogToolSession.create(
            agentToolRegistry = toolRegistry,
            toolRouter = toolRouter,
            eventAdapter = eventAdapter,
            executionEnv = input.executionEnv,
            callback = input.callback,
        )
        val runState = AgentRunStateEmitter(
            callback = input.callback,
            runId = input.executionEnv.agentRunId,
            contextWindow = modelOverride?.contextWindow ?: 128000,
        )
        val frame = AgentExecutionFrame(
            runtimeInput = input,
            messages = input.initialMessages.toMutableList(),
            planService = planService,
            toolSession = koogToolSession,
            runState = runState,
        )
        val agent = buildAgent(frame)

        return try {
            agent.run(frame)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            buildUnexpectedErrorResult(frame, error)
        } finally {
            runCatching { agent.close() }
                .onFailure { OmniLog.w(tag, "close Koog agent failed: ${it.message}") }
        }
    }

    private fun buildAgent(frame: AgentExecutionFrame): AIAgent<AgentExecutionFrame, AgentResult> {
        val strategy = strategy<AgentExecutionFrame, AgentResult>("normal-agent-koog-graph") {
            val prepareInput by node<AgentExecutionFrame, AgentExecutionFrame>(
                AgentRunStateEmitter.NODE_PREPARE_INPUT,
            ) { current ->
                prepareInputNode(current)
            }
            val planTask by node<AgentExecutionFrame, AgentExecutionFrame>(
                AgentRunStateEmitter.NODE_PLAN_TASK,
            ) { current ->
                planTaskNode(current)
            }
            val executeStep by node<AgentExecutionFrame, AgentExecutionFrame>(
                AgentRunStateEmitter.NODE_EXECUTE_STEP,
            ) { current ->
                executeStepNode(current)
            }
            val evaluateProgress by node<AgentExecutionFrame, AgentExecutionFrame>(
                AgentRunStateEmitter.NODE_EVALUATE_PROGRESS,
            ) { current ->
                evaluateProgressNode(current)
            }
            val compressHistory by node<AgentExecutionFrame, AgentExecutionFrame>(
                AgentRunStateEmitter.NODE_COMPRESS_HISTORY,
            ) { current ->
                compressHistoryNode(current)
            }
            val finalizeAnswer by node<AgentExecutionFrame, AgentResult>(
                AgentRunStateEmitter.NODE_FINALIZE_ANSWER,
            ) { current ->
                finalizeAnswerNode(current)
            }

            edge(nodeStart forwardTo prepareInput)
            edge(prepareInput forwardTo planTask)
            edge(planTask forwardTo evaluateProgress)
            edge(
                (evaluateProgress forwardTo finalizeAnswer)
                    .onCondition { current -> current.isTerminal() },
            )
            edge(finalizeAnswer forwardTo nodeFinish)
            edge(
                (evaluateProgress forwardTo compressHistory)
                    .onCondition { current -> current.shouldCompressBeforeNextTurn() },
            )
            edge(
                (evaluateProgress forwardTo planTask)
                    .onCondition { current -> current.needsReplan },
            )
            edge(evaluateProgress forwardTo executeStep)
            edge(executeStep forwardTo evaluateProgress)
            edge(compressHistory forwardTo evaluateProgress)
        }

        return AIAgent.builder()
            .id("koog-normal-agent")
            .maxIterations(128)
            .promptExecutor(koogBootstrap.promptExecutor)
            .llmModel(koogBootstrap.model)
            .toolRegistry(frame.toolSession.registry)
            .graphStrategy(strategy)
            .install(EventHandler.Feature) { config ->
                config.onNodeExecutionStarting { event ->
                    val current = event.input as? AgentExecutionFrame ?: return@onNodeExecutionStarting
                    current.runState.activateWorkflowNode(
                        nodeId = event.node.name,
                        phase = phaseForNode(event.node.name, current),
                    )
                }
                config.onNodeExecutionFailed { event ->
                    val current = event.input as? AgentExecutionFrame ?: return@onNodeExecutionFailed
                    val message = event.throwable.message ?: "Agent execution failed"
                    current.runState.failCurrentStep(message)
                    current.errorMessage = current.errorMessage ?: message
                    current.errorException = current.errorException
                        ?: (event.throwable as? Exception ?: IllegalStateException(message, event.throwable))
                }
            }
            .build()
    }

    @OptIn(kotlin.time.ExperimentalTime::class)
    private fun resolveKoogBootstrap(): KoogBootstrap {
        val descriptor = resolveKoogModelDescriptor()
        val koogModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = descriptor.modelId,
            capabilities = listOf(
                LLMCapability.Completion,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.MultipleChoices,
                LLMCapability.OpenAIEndpoint.Completions,
            ),
            contextLength = descriptor.contextWindow.toLong(),
            maxOutputTokens = 16384L,
        )
        val executor = PromptExecutor.builder()
            .openAI(
                descriptor.apiKey.ifBlank { "unused-koog-key" },
                OpenAIClientSettings(
                    baseUrl = descriptor.apiBase,
                    timeoutConfig = ConnectionTimeoutConfig(),
                    chatCompletionsPath = "v1/chat/completions",
                    responsesAPIPath = "v1/responses",
                    embeddingsPath = "v1/embeddings",
                    moderationsPath = "v1/moderations",
                    modelsPath = "v1/models",
                ),
            )
            .fallback(koogModel)
            .build()

        OmniLog.i(
            tag,
            "koog bootstrap model=${descriptor.modelId} base=${descriptor.apiBase} contextWindow=${descriptor.contextWindow}",
        )

        return KoogBootstrap(
            model = koogModel,
            promptExecutor = executor,
        )
    }

    private fun resolveKoogModelDescriptor(): KoogModelDescriptor {
        modelOverride?.let { override ->
            return KoogModelDescriptor(
                modelId = override.modelId.trim().ifEmpty { model },
                apiBase = normalizeKoogApiBase(override.apiBase),
                apiKey = override.apiKey.trim(),
                contextWindow = override.contextWindow ?: 128000,
            )
        }

        val normalizedModel = model.trim()
        if (normalizedModel.startsWith("scene.")) {
            val binding = SceneModelBindingStore.getBinding(normalizedModel)
            val boundProfile = binding?.providerProfileId?.let(ModelProviderConfigStore::getProfile)
            if (
                binding != null &&
                    boundProfile != null &&
                    binding.modelId.isNotBlank() &&
                    boundProfile.baseUrl.isNotBlank()
            ) {
                return KoogModelDescriptor(
                    modelId = binding.modelId.trim(),
                    apiBase = normalizeKoogApiBase(boundProfile.baseUrl),
                    apiKey = boundProfile.apiKey.trim(),
                    contextWindow = 128000,
                )
            }

            val fallbackProfile = ModelProviderConfigStore.getConfig()
            val resolvedModelId = runCatching {
                ModelSceneRegistry.resolveModel(normalizedModel)
            }.getOrElse {
                OmniLog.w(tag, "resolve Koog scene model failed: ${it.message}")
                normalizedModel
            }

            return KoogModelDescriptor(
                modelId = resolvedModelId,
                apiBase = normalizeKoogApiBase(fallbackProfile.baseUrl),
                apiKey = fallbackProfile.apiKey.trim(),
                contextWindow = 128000,
            )
        }

        val fallbackProfile = ModelProviderConfigStore.getConfig()
        return KoogModelDescriptor(
            modelId = normalizedModel.ifEmpty { model },
            apiBase = normalizeKoogApiBase(fallbackProfile.baseUrl),
            apiKey = fallbackProfile.apiKey.trim(),
            contextWindow = 128000,
        )
    }

    private fun normalizeKoogApiBase(raw: String?): String {
        val normalized = raw?.let(ModelProviderConfigStore::normalizeBaseUrl)?.trim().orEmpty()
        return normalized.ifEmpty { "https://api.openai.com" }
    }

    private fun phaseForNode(
        nodeName: String,
        frame: AgentExecutionFrame,
    ): AgentRunPhase {
        return when (nodeName) {
            AgentRunStateEmitter.NODE_PREPARE_INPUT -> AgentRunPhase.PLANNING
            AgentRunStateEmitter.NODE_PLAN_TASK -> if (frame.needsInitialPlan) {
                AgentRunPhase.PLANNING
            } else {
                AgentRunPhase.REPLANNING
            }

            AgentRunStateEmitter.NODE_EXECUTE_STEP -> AgentRunPhase.EXECUTING
            AgentRunStateEmitter.NODE_EVALUATE_PROGRESS -> when {
                frame.isTerminal() -> AgentRunPhase.FINALIZING
                frame.needsReplan -> AgentRunPhase.REPLANNING
                frame.shouldCompressBeforeNextTurn() -> AgentRunPhase.COMPRESSING
                else -> AgentRunPhase.EXECUTING
            }

            AgentRunStateEmitter.NODE_COMPRESS_HISTORY -> AgentRunPhase.COMPRESSING
            AgentRunStateEmitter.NODE_FINALIZE_ANSWER -> if (frame.errorMessage != null) {
                AgentRunPhase.FAILED
            } else {
                AgentRunPhase.FINALIZING
            }

            else -> AgentRunPhase.EXECUTING
        }
    }

    private suspend fun prepareInputNode(frame: AgentExecutionFrame): AgentExecutionFrame {
        frame.runState.updateContextUsage(estimateTokens(frame.messages))
        return frame
    }

    private suspend fun planTaskNode(frame: AgentExecutionFrame): AgentExecutionFrame {
        when {
            frame.needsInitialPlan -> {
                frame.runState.replacePlan(
                    frame.planService.buildInitialPlan(
                        userMessage = frame.executionEnv.userMessage,
                        initialMessages = frame.runtimeInput.initialMessages,
                    ),
                )
                frame.needsInitialPlan = false
                frame.needsReplan = false
            }

            frame.needsReplan -> {
                val failure = frame.pendingRecoverableToolFailure
                if (failure != null) {
                    frame.runState.replan(
                        frame.planService.replanAfterToolFailure(
                            userMessage = frame.executionEnv.userMessage,
                            currentSteps = frame.runState.stepsSnapshot(),
                            failure = failure,
                        ),
                    )
                }
                frame.needsReplan = false
            }
        }
        frame.runState.updateContextUsage(estimateTokens(frame.messages))
        return frame
    }

    private suspend fun evaluateProgressNode(frame: AgentExecutionFrame): AgentExecutionFrame {
        frame.runState.updateContextUsage(estimateTokens(frame.messages))
        return frame
    }

    private suspend fun compressHistoryNode(frame: AgentExecutionFrame): AgentExecutionFrame {
        val compressedMessages = compressHistory(
            messages = frame.messages,
            stepSnapshot = frame.runState.stepsSnapshot(),
            userMessage = frame.executionEnv.userMessage,
        )
        frame.messages.clear()
        frame.messages.addAll(compressedMessages)
        frame.runState.markCompressed(estimateTokens(frame.messages))
        if (frame.runState.isHardLimited()) {
            val errorMessage = "上下文压缩后仍超过模型窗口限制，已停止本轮请求。"
            setFatalError(frame, errorMessage, IllegalStateException(errorMessage))
        }
        return frame
    }

    private suspend fun executeStepNode(frame: AgentExecutionFrame): AgentExecutionFrame {
        if (frame.isTerminal()) {
            return frame
        }

        frame.completedModelRounds += 1
        val round = frame.completedModelRounds
        frame.runState.markNextStepRunning()
        frame.callback.onThinkingStart()

        val toolChoiceForRound = if (frame.messages.lastOrNull()?.role == "tool") {
            null
        } else {
            JsonPrimitive("auto")
        }

        OmniLog.i(
            tag,
            "round=$round request_tools=${frame.toolSession.toolsForModel.size} tool_exec_count=${frame.toolExecutionCount} execution_intent=${frame.executionIntent}",
        )
        val turn = llmClient.streamTurn(
            request = ChatCompletionRequest(
                messages = frame.messages,
                model = model,
                maxCompletionTokens = 16384,
                stream = true,
                streamOptions = ChatCompletionStreamOptions(includeUsage = true),
                tools = frame.toolSession.toolsForModel,
                toolChoice = toolChoiceForRound,
                parallelToolCalls = false,
            ),
            onReasoningUpdate = { reasoning ->
                if (reasoning.isNotBlank()) {
                    frame.callback.onThinkingUpdate(normalizeThinkingText(reasoning))
                }
            },
            onContentUpdate = { content ->
                if (
                    content.isNotBlank() &&
                        !AgentExecutionIntentPolicy.containsPseudoToolMarkup(content)
                ) {
                    frame.callback.onChatMessage(content, false)
                }
            },
        )

        frame.lastFinishReason = turn.finishReason
        frame.lastAssistantContent = turn.message.contentText().trim()
        val toolCalls = turn.message.toolCalls.orEmpty()
        val hasPseudoToolMarkup = AgentExecutionIntentPolicy.containsPseudoToolMarkup(
            frame.lastAssistantContent,
        )
        frame.runState.updateContextUsage(
            turn.usage?.totalTokens ?: estimateTokens(frame.messages) + (frame.lastAssistantContent.length / 4),
        )
        OmniLog.i(
            tag,
            "round=$round parsed_tool_calls=${toolCalls.size} finish_reason=${frame.lastFinishReason.orEmpty()} assistant_content_len=${frame.lastAssistantContent.length} pseudo_tool_markup=$hasPseudoToolMarkup",
        )

        frame.messages.add(
            ChatCompletionMessage(
                role = "assistant",
                content = normalizeAssistantContentForNextRound(
                    content = turn.message.content,
                    toolCalls = toolCalls,
                ),
                toolCalls = toolCalls.ifEmpty { null },
            ),
        )
        frame.runState.updateContextUsage(
            turn.usage?.totalTokens ?: estimateTokens(frame.messages),
        )

        if (toolCalls.isEmpty()) {
            return handleNoToolCallTurn(frame, hasPseudoToolMarkup, round)
        }

        handleToolCalls(frame, toolCalls)
        return frame
    }

    private suspend fun handleNoToolCallTurn(
        frame: AgentExecutionFrame,
        hasPseudoToolMarkup: Boolean,
        round: Int,
    ): AgentExecutionFrame {
        val failure = frame.pendingRecoverableToolFailure
        if (failure != null) {
            if (frame.toolFailureRecoveryRetryCount < 2) {
                frame.toolFailureRecoveryRetryCount += 1
                frame.needsReplan = true
                frame.messages.add(
                    ChatCompletionMessage(
                        role = "user",
                        content = JsonPrimitive(
                            AgentFailureRecovery.buildToolFailureRetryPrompt(
                                userMessage = frame.executionEnv.userMessage,
                                failure = failure,
                            ),
                        ),
                    ),
                )
                OmniLog.w(
                    tag,
                    "round=$round tool failure recovery retry=${frame.toolFailureRecoveryRetryCount}/2 tool=${failure.toolName}",
                )
                return frame
            }

            val fallbackMessage = AgentFailureRecovery.buildToolFailureExhaustedMessage(failure)
            frame.runState.failCurrentStep(failure.summary)
            frame.finalChatMessage = fallbackMessage
            frame.executedTools.add(ToolExecutionResult.ChatMessage(fallbackMessage))
            frame.outputKind = AgentOutputKind.CHAT_MESSAGE
            frame.hasUserFacingOutput = true
            frame.terminated = true
            return frame
        }

        if (hasPseudoToolMarkup) {
            if (
                AgentExecutionIntentPolicy.shouldRetryNoToolCall(
                    executionIntent = true,
                    toolExecutionCount = frame.toolExecutionCount,
                    retryCount = frame.executionIntentRetryCount,
                    maxRetries = 1,
                )
            ) {
                frame.executionIntentRetryCount += 1
                frame.messages.add(
                    ChatCompletionMessage(
                        role = "user",
                        content = JsonPrimitive(
                            buildPseudoToolMarkupRetryPrompt(frame.executionEnv.userMessage),
                        ),
                    ),
                )
                OmniLog.w(
                    tag,
                    "round=$round pseudo tool markup detected; retry=${frame.executionIntentRetryCount}/1",
                )
                return frame
            }
            val errorMessage = "协议或模型不支持标准工具调用：模型输出了伪工具标签，而不是 assistant.tool_calls"
            setFatalError(frame, errorMessage, IllegalStateException(errorMessage))
            return frame
        }

        if (
            AgentExecutionIntentPolicy.shouldRetryNoToolCall(
                executionIntent = frame.executionIntent,
                toolExecutionCount = frame.toolExecutionCount,
                retryCount = frame.executionIntentRetryCount,
                maxRetries = 1,
            )
        ) {
            frame.executionIntentRetryCount += 1
            frame.messages.add(
                ChatCompletionMessage(
                    role = "user",
                    content = JsonPrimitive(
                        buildExecutionIntentToolCallRetryPrompt(frame.executionEnv.userMessage),
                    ),
                ),
            )
            OmniLog.w(
                tag,
                "round=$round execution-intent without tool_calls; retry=${frame.executionIntentRetryCount}/1",
            )
            return frame
        }

        if (
            AgentExecutionIntentPolicy.shouldFailNoToolCall(
                executionIntent = frame.executionIntent,
                toolExecutionCount = frame.toolExecutionCount,
                retryCount = frame.executionIntentRetryCount,
                maxRetries = 1,
            )
        ) {
            val errorMessage = "协议或模型不支持工具调用：执行型请求未返回 tool_calls（finish_reason=${frame.lastFinishReason.orEmpty()}）"
            setFatalError(frame, errorMessage, IllegalStateException(errorMessage))
            return frame
        }

        val fallbackMessage = frame.lastAssistantContent.ifBlank {
            "我已完成思考，但暂时无法生成回复，请重试。"
        }
        frame.runState.completeCurrentStep(frame.lastAssistantContent)
        frame.finalChatMessage = fallbackMessage
        frame.executedTools.add(ToolExecutionResult.ChatMessage(fallbackMessage))
        frame.outputKind = AgentOutputKind.CHAT_MESSAGE
        frame.hasUserFacingOutput = true
        frame.terminated = true
        return frame
    }

    private suspend fun handleToolCalls(
        frame: AgentExecutionFrame,
        toolCalls: List<AssistantToolCall>,
    ) {
        for (toolCall in toolCalls) {
            val descriptor = frame.toolSession.runtimeDescriptor(toolCall.function.name)
            val parsedArgs = try {
                parseToolArguments(toolCall.function.arguments)
            } catch (error: Exception) {
                val result = ToolExecutionResult.Error(
                    toolCall.function.name,
                    error.message ?: "Invalid tool arguments JSON",
                )
                frame.executedTools.add(result)
                frame.callback.onToolCallComplete(toolCall.function.name, result)
                frame.messages.add(
                    ChatCompletionMessage(
                        role = "tool",
                        toolCallId = toolCall.id,
                        content = JsonPrimitive(eventAdapter.toolResultContent(descriptor, result)),
                    ),
                )
                frame.runState.updateContextUsage(estimateTokens(frame.messages))
                setFatalError(
                    frame,
                    result.message,
                    error as? Exception ?: IllegalArgumentException(result.message, error),
                )
                return
            }

            val validationError = runCatching {
                frame.toolSession.validateArguments(toolCall.function.name, parsedArgs)
            }.exceptionOrNull()
            if (validationError != null) {
                val result = ToolExecutionResult.Error(
                    toolCall.function.name,
                    validationError.message ?: "Tool arguments validation failed",
                )
                frame.executedTools.add(result)
                frame.callback.onToolCallComplete(toolCall.function.name, result)
                frame.messages.add(
                    ChatCompletionMessage(
                        role = "tool",
                        toolCallId = toolCall.id,
                        content = JsonPrimitive(eventAdapter.toolResultContent(descriptor, result)),
                    ),
                )
                frame.runState.updateContextUsage(estimateTokens(frame.messages))
                setFatalError(
                    frame,
                    result.message,
                    validationError as? Exception ?: IllegalArgumentException(result.message, validationError),
                )
                return
            }

            frame.runState.markNextStepRunning(toolCall.function.name)
            frame.callback.onToolCallStart(toolCall.function.name, parsedArgs)
            val isTerminalToolCall = toolCall.function.name == "terminal_execute"
            val result = if (isTerminalToolCall) {
                val retryDecision = TerminalRetryPolicy.beforeTerminalExecution(frame.terminalRetryState)
                frame.terminalRetryState = retryDecision.nextState
                if (!retryDecision.shouldExecute) {
                    frame.callback.onToolCallProgress(
                        toolCall.function.name,
                        "终端自动修正已达到上限，返回诊断结果",
                    )
                    toolRouter.buildTerminalRetryBudgetExhaustedResult(
                        args = parsedArgs,
                        retryState = frame.terminalRetryState,
                    )
                } else {
                    frame.toolSession.execute(
                        toolCall = toolCall,
                        args = parsedArgs,
                    ).also { toolResult ->
                        if (toolResult is ToolExecutionResult.TerminalResult) {
                            frame.terminalRetryState = TerminalRetryPolicy.afterTerminalResult(
                                frame.terminalRetryState,
                                toolResult.success,
                            )
                        }
                    }
                }
            } else {
                frame.toolSession.execute(
                    toolCall = toolCall,
                    args = parsedArgs,
                )
            }

            frame.executedTools.add(result)
            frame.toolExecutionCount += 1
            frame.callback.onToolCallComplete(toolCall.function.name, result)
            AgentFailureRecovery.extractRecoverableToolFailure(toolCall.function.name, result)
                ?.let { failure ->
                    frame.pendingRecoverableToolFailure = failure
                    frame.toolFailureRecoveryRetryCount = 0
                    frame.runState.failCurrentStep(failure.summary)
                } ?: run {
                frame.pendingRecoverableToolFailure = null
                frame.toolFailureRecoveryRetryCount = 0
                if (result !is ToolExecutionResult.Error) {
                    frame.runState.completeCurrentStep(resultSummary(result))
                }
            }
            frame.messages.add(
                ChatCompletionMessage(
                    role = "tool",
                    toolCallId = toolCall.id,
                    content = JsonPrimitive(eventAdapter.toolResultContent(descriptor, result)),
                ),
            )
            frame.runState.updateContextUsage(estimateTokens(frame.messages))

            if (eventAdapter.hasUserVisibleOutput(result)) {
                frame.hasUserFacingOutput = true
            }
            val mappedKind = eventAdapter.mapOutputKind(result)
            if (mappedKind != AgentOutputKind.NONE) {
                frame.outputKind = mappedKind
            }

            if (eventAdapter.isConversationStoppingResult(result)) {
                frame.terminated = true
                return
            }
            if (isTerminalToolCall) {
                return
            }
        }
    }

    private suspend fun finalizeAnswerNode(frame: AgentExecutionFrame): AgentResult {
        frame.errorMessage?.let { message ->
            frame.callback.onError(message)
            return AgentResult.Error(
                message = message,
                exception = frame.errorException ?: IllegalStateException(message),
            )
        }

        if (!frame.hasUserFacingOutput) {
            val fallbackMessage = frame.lastAssistantContent.ifBlank {
                "我已完成思考，但暂时无法生成回复，请重试。"
            }
            frame.runState.completeCurrentStep(frame.lastAssistantContent)
            frame.finalChatMessage = fallbackMessage
            frame.executedTools.add(ToolExecutionResult.ChatMessage(fallbackMessage))
            frame.outputKind = AgentOutputKind.CHAT_MESSAGE
            frame.hasUserFacingOutput = true
        }

        frame.finalChatMessage?.let { message ->
            frame.callback.onChatMessage(message, true)
        }
        frame.runState.finishSuccessfully()
        val result = AgentResult.Success(
            response = AgentFinalResponse(
                content = frame.lastAssistantContent,
                finishReason = frame.lastFinishReason,
            ),
            executedTools = frame.executedTools.toList(),
            outputKind = frame.outputKind.value,
            hasUserVisibleOutput = frame.hasUserFacingOutput,
        )
        frame.callback.onComplete(result)
        return result
    }

    private suspend fun buildUnexpectedErrorResult(
        frame: AgentExecutionFrame,
        error: Exception,
    ): AgentResult {
        val message = error.message ?: "Agent execution failed"
        return runCatching {
            frame.runState.failCurrentStep(message)
            frame.callback.onError("Agent execution failed: $message")
            AgentResult.Error("Agent execution failed", error)
        }.getOrElse {
            AgentResult.Error("Agent execution failed", error)
        }
    }

    private suspend fun setFatalError(
        frame: AgentExecutionFrame,
        message: String,
        exception: Exception,
    ) {
        frame.runState.failCurrentStep(message)
        frame.errorMessage = message
        frame.errorException = exception
    }

    private fun normalizeAssistantContentForNextRound(
        content: JsonElement?,
        toolCalls: List<AssistantToolCall>,
    ): JsonElement? {
        if (toolCalls.isEmpty()) {
            return content
        }
        return when (content) {
            null -> JsonPrimitive("")
            is JsonPrimitive -> {
                if (content.isString && content.content.isBlank()) {
                    JsonPrimitive("")
                } else {
                    content
                }
            }

            else -> content
        }
    }

    private fun parseToolArguments(argumentsJson: String): JsonObject {
        val normalized = argumentsJson.trim()
        if (normalized.isEmpty()) return JsonObject(emptyMap())
        val parsed = json.decodeFromString<JsonElement>(normalized)
        return parsed as? JsonObject
            ?: throw IllegalArgumentException("tool arguments must be a JSON object")
    }

    private fun normalizeThinkingText(text: String, maxLen: Int = 3000): String {
        val normalized = text.replace("\r\n", "\n").trim()
        return if (normalized.length <= maxLen) normalized else normalized.take(maxLen) + "\n..."
    }

    private fun buildExecutionIntentToolCallRetryPrompt(userMessage: String): String {
        return buildString {
            appendLine("系统检查到你上一轮未调用工具，但该请求属于执行型任务。")
            appendLine("请在本轮严格使用原生 tool_calls，从请求的 tools 字段中选择至少一个工具执行。")
            appendLine("不要直接输出最终文本答复。若缺失关键信息，请直接向用户提问。")
            appendLine("严禁输出 <tool_call>、<function=...>、<parameter=...> 这类伪工具标签。")
            appendLine("用户原始请求：$userMessage")
        }.trim()
    }

    private fun buildPseudoToolMarkupRetryPrompt(userMessage: String): String {
        return buildString {
            appendLine("你上一轮把工具调用写成了文本标签，这不符合协议。")
            appendLine("下一轮必须返回标准 assistant.tool_calls。")
            appendLine("禁止输出任何 <tool_call>、<function=...>、<parameter=...>、XML、HTML 或伪 JSON 工具标签。")
            appendLine("若需要使用工具，请把 function.name 和 function.arguments 放入原生 tool_calls 字段。")
            appendLine("用户原始请求：$userMessage")
        }.trim()
    }

    private fun estimateTokens(messages: List<ChatCompletionMessage>): Int {
        return messages.sumOf { message ->
            val contentLength = message.contentText().length
            val toolCallLength = message.toolCalls.orEmpty().sumOf { toolCall ->
                toolCall.function.name.length + toolCall.function.arguments.length
            }
            ((contentLength + toolCallLength) / 4).coerceAtLeast(8) + 12
        }.coerceAtLeast(1)
    }

    private fun compressHistory(
        messages: List<ChatCompletionMessage>,
        stepSnapshot: List<AgentPlanStepSnapshot>,
        userMessage: String,
    ): List<ChatCompletionMessage> {
        return compressAgentHistory(
            messages = messages,
            stepSnapshot = stepSnapshot,
            userMessage = userMessage,
        )
    }

    private fun resultSummary(result: ToolExecutionResult): String = when (result) {
        is ToolExecutionResult.ChatMessage -> result.message
        is ToolExecutionResult.Clarify -> result.question
        is ToolExecutionResult.Error -> result.message
        is ToolExecutionResult.PermissionRequired -> result.missing.joinToString("、")
        is ToolExecutionResult.ScheduleResult -> result.summaryText
        is ToolExecutionResult.McpResult -> result.summaryText
        is ToolExecutionResult.Mem0Result -> result.summaryText
        is ToolExecutionResult.TerminalResult -> result.summaryText
        is ToolExecutionResult.ContextResult -> result.summaryText
        is ToolExecutionResult.VlmTaskStarted -> result.goal
    }

    private data class KoogModelDescriptor(
        val modelId: String,
        val apiBase: String,
        val apiKey: String,
        val contextWindow: Int,
    )

    private data class KoogBootstrap(
        val model: LLModel,
        val promptExecutor: PromptExecutor,
    )

    private class AgentExecutionFrame(
        val runtimeInput: Input,
        val messages: MutableList<ChatCompletionMessage>,
        val planService: AgentPlanningService,
        val toolSession: OmniKoogToolSession,
        val runState: AgentRunStateEmitter,
    ) {
        val callback: AgentCallback
            get() = runtimeInput.callback

        val executionEnv: AgentToolRouter.ExecutionEnvironment
            get() = runtimeInput.executionEnv

        val executedTools = mutableListOf<ToolExecutionResult>()
        var outputKind: AgentOutputKind = AgentOutputKind.NONE
        var hasUserFacingOutput: Boolean = false
        var toolExecutionCount: Int = 0
        var lastAssistantContent: String = ""
        var lastFinishReason: String? = null
        var terminalRetryState: TerminalRetryState = TerminalRetryState()
        val executionIntent: Boolean = AgentExecutionIntentPolicy.isExecutionIntent(
            runtimeInput.executionEnv.userMessage,
        )
        var executionIntentRetryCount: Int = 0
        var pendingRecoverableToolFailure: RecoverableToolFailure? = null
        var toolFailureRecoveryRetryCount: Int = 0
        var completedModelRounds: Int = 0
        var terminated: Boolean = false
        var needsInitialPlan: Boolean = true
        var needsReplan: Boolean = false
        var errorMessage: String? = null
        var errorException: Exception? = null
        var finalChatMessage: String? = null

        fun shouldCompressBeforeNextTurn(): Boolean = !isTerminal() && runState.shouldCompress()

        fun isTerminal(): Boolean = terminated || errorMessage != null
    }
}

private class AgentPlanningService(
    private val llmClient: AgentLlmClient,
    private val model: String,
    private val json: Json,
    private val modelOverride: AgentModelOverride? = null,
) {
    suspend fun buildInitialPlan(
        userMessage: String,
        initialMessages: List<ChatCompletionMessage>,
    ): List<AgentPlanStepSnapshot> {
        val promptMessages = buildList {
            add(initialMessages.first())
            add(
                ChatCompletionMessage(
                    role = "user",
                    content = JsonPrimitive(
                        buildString {
                            appendLine("请把当前用户请求拆解成 2 到 5 个执行步骤，并返回 JSON 数组。")
                            appendLine("每个元素格式：{\"title\": string, \"summary\": string}。")
                            appendLine("不要输出 Markdown 代码块，不要解释。")
                            appendLine("用户请求：$userMessage")
                        }.trim(),
                    ),
                ),
            )
        }
        val response = runCatching {
            llmClient.streamTurn(
                request = ChatCompletionRequest(
                    messages = promptMessages,
                    model = model,
                    maxCompletionTokens = 1200,
                    stream = true,
                    streamOptions = ChatCompletionStreamOptions(includeUsage = false),
                ),
            )
        }.getOrNull()

        val raw = response?.message?.contentText().orEmpty()
        val parsed = parsePlan(raw)
        if (parsed.isNotEmpty()) {
            return parsed
        }
        return fallbackPlan(userMessage)
    }

    suspend fun replanAfterToolFailure(
        userMessage: String,
        currentSteps: List<AgentPlanStepSnapshot>,
        failure: RecoverableToolFailure,
    ): List<AgentPlanStepSnapshot> {
        val fallback = currentSteps.mapIndexed { index, step ->
            if (index == currentSteps.indexOfFirst { it.status == AgentPlanStepStatus.RUNNING }) {
                step.copy(status = AgentPlanStepStatus.FAILED, summary = failure.summary)
            } else {
                step
            }
        }
        val promptMessages = listOf(
            ChatCompletionMessage(
                role = "system",
                content = JsonPrimitive("你负责更新执行计划，输出 JSON 数组。"),
            ),
            ChatCompletionMessage(
                role = "user",
                content = JsonPrimitive(
                    buildString {
                        appendLine("基于失败信息更新步骤，返回 JSON 数组。")
                        appendLine("每个元素格式：{\"title\": string, \"summary\": string}。")
                        appendLine("用户请求：$userMessage")
                        appendLine("失败工具：${failure.toolName}")
                        appendLine("失败摘要：${failure.summary}")
                        appendLine("当前步骤：")
                        currentSteps.forEach { step ->
                            appendLine("- [${step.status.wireName}] ${step.title}${step.summary?.let { ": $it" } ?: ""}")
                        }
                    }.trim(),
                ),
            ),
        )
        val response = runCatching {
            llmClient.streamTurn(
                request = ChatCompletionRequest(
                    messages = promptMessages,
                    model = model,
                    maxCompletionTokens = 1200,
                    stream = true,
                    streamOptions = ChatCompletionStreamOptions(includeUsage = false),
                ),
            )
        }.getOrNull()
        val parsed = parsePlan(response?.message?.contentText().orEmpty())
        return if (parsed.isNotEmpty()) parsed else fallback
    }

    private fun parsePlan(raw: String): List<AgentPlanStepSnapshot> {
        val normalized = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        if (normalized.isEmpty()) {
            return emptyList()
        }
        val element = runCatching { json.decodeFromString<JsonElement>(normalized) }.getOrNull()
        val array = element as? JsonArray ?: return emptyList()
        return array.mapIndexedNotNull { index, item ->
            val obj = item as? JsonObject ?: return@mapIndexedNotNull null
            val title = obj["title"]?.let { (it as? JsonPrimitive)?.content }?.trim().orEmpty()
            if (title.isEmpty()) {
                return@mapIndexedNotNull null
            }
            val summary = obj["summary"]?.let { (it as? JsonPrimitive)?.content }?.trim()
            AgentPlanStepSnapshot(
                id = buildStepId(index, title),
                title = title,
                status = AgentPlanStepStatus.PENDING,
                order = index,
                summary = summary,
            )
        }
    }

    private fun fallbackPlan(userMessage: String): List<AgentPlanStepSnapshot> {
        val compactGoal = userMessage.replace(Regex("\\s+"), " ").trim().take(48)
        return listOf(
            AgentPlanStepSnapshot(
                id = buildStepId(0, "理解目标"),
                title = "理解目标",
                status = AgentPlanStepStatus.PENDING,
                order = 0,
                summary = compactGoal,
            ),
            AgentPlanStepSnapshot(
                id = buildStepId(1, "执行所需工具"),
                title = "执行所需工具",
                status = AgentPlanStepStatus.PENDING,
                order = 1,
                summary = "调用合适工具完成主要任务",
            ),
            AgentPlanStepSnapshot(
                id = buildStepId(2, "整理并输出结果"),
                title = "整理并输出结果",
                status = AgentPlanStepStatus.PENDING,
                order = 2,
                summary = "向用户返回最终结论",
            ),
        )
    }

    private fun buildStepId(index: Int, title: String): String {
        val normalized = title.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]+"), "-")
            .trim('-')
            .ifEmpty { "step-${index + 1}" }
        return "step-${index + 1}-$normalized"
    }
}
