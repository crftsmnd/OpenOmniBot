package cn.com.omnimind.bot.agent

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.ToolRegistryBuilder
import cn.com.omnimind.baselib.llm.AssistantToolCall
import cn.com.omnimind.baselib.llm.AssistantToolCallFunction
import cn.com.omnimind.baselib.llm.ChatCompletionFunction
import cn.com.omnimind.baselib.llm.ChatCompletionTool
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

internal class OmniKoogToolSession private constructor(
    private val agentToolRegistry: AgentToolRegistry,
    private val toolsByName: Map<String, OmniKoogTool>,
    val registry: ToolRegistry,
    val toolsForModel: List<ChatCompletionTool>,
) {
    fun runtimeDescriptor(toolName: String): AgentToolRegistry.RuntimeToolDescriptor {
        return agentToolRegistry.runtimeDescriptor(toolName)
    }

    fun validateArguments(toolName: String, arguments: JsonObject) {
        agentToolRegistry.validateArguments(toolName, arguments)
    }

    suspend fun execute(toolCall: AssistantToolCall, args: JsonObject): ToolExecutionResult {
        val tool = toolsByName[toolCall.function.name]
            ?: return ToolExecutionResult.Error(toolCall.function.name, "Unknown tool: ${toolCall.function.name}")
        return tool.executeForAgent(args)
    }

    companion object {
        fun create(
            agentToolRegistry: AgentToolRegistry,
            toolRouter: AgentToolRouter,
            eventAdapter: AgentEventAdapter,
            executionEnv: AgentToolRouter.ExecutionEnvironment,
            callback: AgentCallback,
        ): OmniKoogToolSession {
            val tools = agentToolRegistry.definitions().map { definition ->
                OmniKoogTool(
                    runtimeDescriptor = definition.descriptor,
                    parameters = definition.parameters,
                    toolRouter = toolRouter,
                    executionEnv = executionEnv,
                    callback = callback,
                    eventAdapter = eventAdapter,
                )
            }
            val toolsByName = tools.associateBy { it.name }
            val registry = ToolRegistryBuilder()
                .tools(tools)
                .build()
            val toolsForModel = agentToolRegistry.definitions().map { definition ->
                ChatCompletionTool(
                    function = ChatCompletionFunction(
                        name = definition.descriptor.name,
                        description = definition.descriptor.description,
                        parameters = definition.parameters,
                    ),
                )
            }
            return OmniKoogToolSession(
                agentToolRegistry = agentToolRegistry,
                toolsByName = toolsByName,
                registry = registry,
                toolsForModel = toolsForModel,
            )
        }
    }
}

private class OmniKoogTool(
    val runtimeDescriptor: AgentToolRegistry.RuntimeToolDescriptor,
    private val parameters: JsonObject,
    private val toolRouter: AgentToolRouter,
    private val executionEnv: AgentToolRouter.ExecutionEnvironment,
    private val callback: AgentCallback,
    private val eventAdapter: AgentEventAdapter,
) : Tool<JsonObject, String>(
    JsonObject.serializer(),
    String.serializer(),
    buildToolDescriptor(runtimeDescriptor, parameters),
) {
    suspend fun executeForAgent(args: JsonObject): ToolExecutionResult {
        val toolCall = AssistantToolCall(
            id = UUID.randomUUID().toString(),
            function = AssistantToolCallFunction(
                name = runtimeDescriptor.name,
                arguments = args.toString(),
            ),
        )
        return try {
            toolRouter.execute(
                toolCall = toolCall,
                args = args,
                runtimeDescriptor = runtimeDescriptor,
                env = executionEnv,
                callback = callback,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ToolExecutionResult.Error(
                runtimeDescriptor.name,
                error.message ?: "Tool execution failed",
            )
        }
    }

    override suspend fun execute(args: JsonObject): String {
        val result = executeForAgent(args)
        return eventAdapter.toolResultContent(runtimeDescriptor, result)
    }
}

private fun buildToolDescriptor(
    descriptor: AgentToolRegistry.RuntimeToolDescriptor,
    parameters: JsonObject,
): ToolDescriptor {
    val required = (parameters["required"] as? JsonArray)
        ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()
    val properties = (parameters["properties"] as? JsonObject).orEmpty()
    val parameterDescriptors = properties.entries.map { (name, rawSchema) ->
        val schema = rawSchema as? JsonObject ?: JsonObject(emptyMap())
        ToolParameterDescriptor(
            name = name,
            description = schema["description"]?.jsonPrimitive?.contentOrNull ?: name,
            type = mapToolParameterType(schema),
        )
    }
    return ToolDescriptor(
        name = descriptor.name,
        description = descriptor.description,
        requiredParameters = parameterDescriptors.filter { it.name in required },
        optionalParameters = parameterDescriptors.filterNot { it.name in required },
    )
}

private fun mapToolParameterType(schema: JsonObject): ToolParameterType {
    val enumValues = (schema["enum"] as? JsonArray)
        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
        .orEmpty()
    if (enumValues.isNotEmpty()) {
        return ToolParameterType.Enum(enumValues.toTypedArray())
    }

    val type = schema["type"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    return when (type) {
        "string" -> ToolParameterType.String
        "integer" -> ToolParameterType.Integer
        "number" -> ToolParameterType.Float
        "boolean" -> ToolParameterType.Boolean
        "array" -> ToolParameterType.List(
            mapToolParameterType((schema["items"] as? JsonObject) ?: JsonObject(emptyMap())),
        )

        "object" -> {
            val required = (schema["required"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            val properties = (schema["properties"] as? JsonObject).orEmpty()
            val propertyDescriptors = properties.entries.map { (name, rawSchema) ->
                val propertySchema = rawSchema as? JsonObject ?: JsonObject(emptyMap())
                ToolParameterDescriptor(
                    name = name,
                    description = propertySchema["description"]?.jsonPrimitive?.contentOrNull ?: name,
                    type = mapToolParameterType(propertySchema),
                )
            }
            val additionalRaw = schema["additionalProperties"]
            val additionalFlag = (additionalRaw as? JsonPrimitive)?.booleanOrNull
            val additionalType = (additionalRaw as? JsonObject)?.let(::mapToolParameterType)
            ToolParameterType.Object(
                properties = propertyDescriptors,
                requiredProperties = required,
                additionalProperties = additionalFlag,
                additionalPropertiesType = additionalType,
            )
        }

        else -> when {
            schema["properties"] is JsonObject -> mapToolParameterType(
                buildJsonObjectFromUnknownObjectSchema(schema),
            )

            schema["items"] is JsonObject -> ToolParameterType.List(
                mapToolParameterType((schema["items"] as? JsonObject) ?: JsonObject(emptyMap())),
            )

            else -> ToolParameterType.String
        }
    }
}

private fun buildJsonObjectFromUnknownObjectSchema(schema: JsonObject): JsonObject {
    return JsonObject(
        schema.toMutableMap().apply {
            put("type", JsonPrimitive("object"))
        },
    )
}

private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())
