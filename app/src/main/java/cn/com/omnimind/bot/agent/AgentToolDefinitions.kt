package cn.com.omnimind.bot.agent

import cn.com.omnimind.bot.mem0.Mem0ToolUtils
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object AgentToolDefinitions {
    val contextAppsQueryTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "context_apps_query")
            put("displayName", "查询已安装应用")
            put("toolType", "builtin")
            put("description", "查询设备已安装应用列表。需要应用包名或确认应用是否已安装时优先调用。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "可选关键词，可匹配应用名或包名。")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "可选，返回数量上限，默认 20，范围 1-100。")
                    }
                }
            }
        }
    }

    val contextTimeNowTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "context_time_now")
            put("displayName", "查询当前时间")
            put("toolType", "builtin")
            put("description", "查询当前时间信息。需要日期、时间、时区或星期信息时调用。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("timezone") {
                        put("type", "string")
                        put("description", "可选 IANA 时区，例如 Asia/Shanghai、America/Los_Angeles。默认使用系统时区。")
                    }
                }
            }
        }
    }

    val vlmTaskTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "vlm_task")
            put("displayName", "视觉执行")
            put("toolType", "builtin")
            put("description", "使用视觉语言模型执行手机屏幕操作任务。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("goal") {
                        put("type", "string")
                        put("description", "任务目标，使用第一人称描述。")
                    }
                    putJsonObject("packageName") {
                        put("type", "string")
                        put("description", "目标应用包名。")
                    }
                    putJsonObject("needSummary") {
                        put("type", "boolean")
                        put("description", "是否在结束后生成总结。")
                    }
                    putJsonObject("startFromCurrent") {
                        put("type", "boolean")
                        put("description", "仅在用户明确要求从当前页面继续时设为 true。")
                    }
                }
                putJsonArray("required") {
                    add("goal")
                }
            }
        }
    }

    val terminalExecuteTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "terminal_execute")
            put("displayName", "终端执行")
            put("toolType", "terminal")
            put(
                "description",
                "通过应用内置的 Ubuntu（proot）环境执行一次性的非交互终端命令。这是默认首选的终端工具，适合文件处理、脚本、网络诊断、git、python、包管理等绝大多数 CLI 任务；不用于手机界面操作，也不用于交互式 TUI。只有明确需要跨多轮保留 cwd、环境或后台进程时，才改用 terminal_session_*。"
            )
            put(
                "postToolRule",
                "terminal_execute 应单独占据当前 tool_calls。该工具会固定在 executionMode=proot（prootDistro=ubuntu）执行，传入 termux/debian 等参数会被忽略。若执行失败，可在下一轮基于 stdout/stderr/errorMessage 再次调用 terminal_execute 修正，最多 3 次；不要在同一个 tool_calls 中串联其他结果依赖型工具。"
            )
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("command") {
                        put("type", "string")
                        put("description", "要执行的单次 shell 命令，必须非交互。")
                    }
                    putJsonObject("executionMode") {
                        put("type", "string")
                        put("description", "可选。兼容字段，当前固定在 proot Ubuntu 执行，传入 termux 也会被自动忽略。")
                        putJsonArray("enum") {
                            add("proot")
                            add("termux")
                        }
                    }
                    putJsonObject("prootDistro") {
                        put("type", "string")
                        put("description", "可选。兼容字段，当前固定使用 ubuntu，传入其他 distro 会被自动忽略。")
                    }
                    putJsonObject("workingDirectory") {
                        put("type", "string")
                        put("description", "可选工作目录，建议使用绝对路径。")
                    }
                    putJsonObject("timeoutSeconds") {
                        put("type", "integer")
                        put("description", "等待结果的超时时间，默认 60 秒，范围 5-300。")
                    }
                }
                putJsonArray("required") {
                    add("command")
                }
            }
        }
    }

    val terminalSessionStartTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "terminal_session_start")
            put("displayName", "启动终端会话")
            put("toolType", "terminal")
            put("description", "启动一个可复用的 Ubuntu 终端会话，仅用于确实需要在后续多轮中保留 cwd、shell 环境、中间文件状态或后台进程的任务。不要为了运行单条命令、检查工具是否存在、读取单个文件或执行一次性脚本而使用它，这些场景应优先用 terminal_execute。")
            put("postToolRule", "启动后等待工具结果，再决定是否继续向该 session 发送命令。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("sessionName") {
                        put("type", "string")
                        put("description", "可选，会话名称。未传时自动生成。")
                    }
                    putJsonObject("workingDirectory") {
                        put("type", "string")
                        put("description", "可选，会话初始工作目录。默认使用当前 workspace cwd。")
                    }
                }
            }
        }
    }

    val terminalSessionExecTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "terminal_session_exec")
            put("displayName", "执行会话命令")
            put("toolType", "terminal")
            put("description", "向已有终端 session 发送一条非交互命令，并等待该命令完成。只在你明确想复用同一个 session 的 cwd、环境变量、后台任务或中间状态时使用。")
            put("postToolRule", "执行后等待结果，再判断是否继续读取日志、再次执行或结束 session。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("sessionId") {
                        put("type", "string")
                        put("description", "terminal_session_start 返回的 sessionId。")
                    }
                    putJsonObject("command") {
                        put("type", "string")
                        put("description", "要执行的单次非交互 shell 命令。")
                    }
                    putJsonObject("workingDirectory") {
                        put("type", "string")
                        put("description", "可选，本次命令执行前要切换到的目录。")
                    }
                    putJsonObject("timeoutSeconds") {
                        put("type", "integer")
                        put("description", "等待该命令完成的超时时间，默认 120 秒，范围 5-600。")
                    }
                }
                putJsonArray("required") {
                    add("sessionId")
                    add("command")
                }
            }
        }
    }

    val terminalSessionReadTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "terminal_session_read")
            put("displayName", "读取会话输出")
            put("toolType", "terminal")
            put("description", "读取终端 session 最近一次命令日志或最近的终端输出。只在已经启动并复用了 terminal_session_* 的前提下使用。")
            put("postToolRule", "读取结果后再决定是否继续执行命令。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("sessionId") {
                        put("type", "string")
                        put("description", "terminal session id。")
                    }
                    putJsonObject("maxChars") {
                        put("type", "integer")
                        put("description", "最多返回多少字符，默认 4000，范围 256-64000。")
                    }
                }
                putJsonArray("required") {
                    add("sessionId")
                }
            }
        }
    }

    val terminalSessionStopTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "terminal_session_stop")
            put("displayName", "结束终端会话")
            put("toolType", "terminal")
            put("description", "停止已有终端 session，并清理对应 tmux 会话。完成状态化终端任务后再调用。")
            put("postToolRule", "结束后等待工具结果，再回复用户。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("sessionId") {
                        put("type", "string")
                        put("description", "terminal session id。")
                    }
                }
                putJsonArray("required") {
                    add("sessionId")
                }
            }
        }
    }

    val browserUseTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "browser_use")
            put("displayName", "浏览器操作")
            put("toolType", "browser")
            put(
                "description",
                "控制一个最多 3 个标签页的离屏浏览器。不要用它打开 App deep link、omnibot:// 非 browser 资源或应用内路由。浏览器只支持访问 http(s) 页面，以及 omnibot://browser/... 资源文件。使用 navigate 打开页面，screenshot 查看当前视口截图，click/type/hover 与元素交互，get_text/get_readable 抽取内容，scroll 导航长页面，scroll_and_collect 在一次调用中滚动并收集无限列表内容，find_elements 发现可交互元素，get_page_info 获取页面元信息，get_backbone 获取 DOM 骨架，execute_js 执行脚本，fetch 复用当前页面 session 下载资源并返回 omnibot://browser/... 产物，new_tab/close_tab/list_tabs 管理标签页，get_cookies 返回 cookie 摘要与可复用的 offload env 脚本路径，set_user_agent 切换 desktop_safari 或 mobile_safari。tool_title 必须是 5-10 个字的简洁摘要，并使用与用户相同的语言。"
            )
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("tool_title") {
                        put("type", "string")
                        put("description", "本次工具调用要做什么的简洁摘要，5-10 个字，展示给用户。")
                    }
                    putJsonObject("action") {
                        put("type", "string")
                        put("description", "浏览器动作。")
                        putJsonArray("enum") {
                            add("navigate")
                            add("screenshot")
                            add("click")
                            add("type")
                            add("get_text")
                            add("scroll")
                            add("get_page_info")
                            add("execute_js")
                            add("find_elements")
                            add("hover")
                            add("get_readable")
                            add("set_user_agent")
                            add("get_backbone")
                            add("fetch")
                            add("new_tab")
                            add("close_tab")
                            add("list_tabs")
                            add("get_cookies")
                            add("scroll_and_collect")
                        }
                    }
                    putJsonObject("url") {
                        put("type", "string")
                        put("description", "navigate 打开的 URL，或 fetch 下载的资源 URL。")
                    }
                    putJsonObject("selector") {
                        put("type", "string")
                        put("description", "CSS selector。适用于 click/type/get_text/scroll/hover/find_elements。")
                    }
                    putJsonObject("text") {
                        put("type", "string")
                        put("description", "type 动作要输入的文本。")
                    }
                    putJsonObject("script") {
                        put("type", "string")
                        put("description", "execute_js 动作要执行的 JavaScript 代码。")
                    }
                    putJsonObject("coordinate_x") {
                        put("type", "integer")
                        put("description", "点击或输入目标的 X 坐标，可替代 selector。")
                    }
                    putJsonObject("coordinate_y") {
                        put("type", "integer")
                        put("description", "点击或输入目标的 Y 坐标，可替代 selector。")
                    }
                    putJsonObject("amount") {
                        put("type", "integer")
                        put("description", "滚动像素量，默认 500。")
                    }
                    putJsonObject("direction") {
                        put("type", "string")
                        put("description", "滚动方向。")
                        putJsonArray("enum") {
                            add("up")
                            add("down")
                        }
                    }
                    putJsonObject("tab_id") {
                        put("type", "integer")
                        put("description", "目标标签页 ID；不传时默认使用最近活跃标签页。")
                    }
                    putJsonObject("item_selector") {
                        put("type", "string")
                        put("description", "scroll_and_collect 的内容项 selector；不传时自动探测。")
                    }
                    putJsonObject("scroll_count") {
                        put("type", "integer")
                        put("description", "scroll_and_collect 的滚动次数，默认 10，最大 20。")
                    }
                    putJsonObject("max_depth") {
                        put("type", "integer")
                        put("description", "get_backbone 的最大深度，默认 5。")
                    }
                    putJsonObject("user_agent") {
                        put("type", "string")
                        put("description", "要切换到的 user agent profile。")
                        putJsonArray("enum") {
                            add("desktop_safari")
                            add("mobile_safari")
                        }
                    }
                    putJsonObject("keywords") {
                        put(
                            "description",
                            "get_cookies 的 cookie 名过滤关键词。可传空格分隔字符串，兼容数组字符串输入。fuzzy=true 时要求所有关键词都包含在 cookie 名中；fuzzy=false 时要求精确命中任一 cookie 名。"
                        )
                    }
                    putJsonObject("fuzzy") {
                        put("type", "boolean")
                        put("description", "get_cookies 的关键词匹配模式，默认 true。")
                    }
                }
                putJsonArray("required") {
                    add("tool_title")
                    add("action")
                }
            }
        }
    }

    val fileReadTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "file_read")
            put("displayName", "读取文件")
            put("toolType", "workspace")
            put("description", "读取 workspace 或 Omnibot 白名单目录中的文件内容。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "文件路径，可使用相对 workspace 路径或 omnibot:// uri。")
                    }
                    putJsonObject("maxChars") {
                        put("type", "integer")
                        put("description", "最多读取字符数，默认 8000，范围 128-64000。")
                    }
                    putJsonObject("offset") {
                        put("type", "integer")
                        put("description", "可选，从指定字符偏移开始读取。")
                    }
                    putJsonObject("lineStart") {
                        put("type", "integer")
                        put("description", "可选，从第几行开始读取，1-based。")
                    }
                    putJsonObject("lineCount") {
                        put("type", "integer")
                        put("description", "可选，读取多少行。")
                    }
                }
                putJsonArray("required") {
                    add("path")
                }
            }
        }
    }

    val fileWriteTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "file_write")
            put("displayName", "写入文件")
            put("toolType", "workspace")
            put("description", "创建或覆盖 workspace 内文件。新建文件优先使用此工具。")
            put("postToolRule", "写入后等待结果，再决定是否继续读取或修改。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "目标文件路径。")
                    }
                    putJsonObject("content") {
                        put("type", "string")
                        put("description", "要写入的完整文本内容。")
                    }
                    putJsonObject("append") {
                        put("type", "boolean")
                        put("description", "是否追加写入，默认 false。")
                    }
                }
                putJsonArray("required") {
                    add("path")
                    add("content")
                }
            }
        }
    }

    val fileEditTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "file_edit")
            put("displayName", "编辑文件")
            put("toolType", "workspace")
            put("description", "对已有文件做精确字符串替换。修改现有文件优先使用此工具。")
            put("postToolRule", "编辑后等待结果，再判断是否继续读取验证。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "目标文件路径。")
                    }
                    putJsonObject("oldText") {
                        put("type", "string")
                        put("description", "要替换的原始文本。")
                    }
                    putJsonObject("newText") {
                        put("type", "string")
                        put("description", "替换后的文本。")
                    }
                    putJsonObject("replaceAll") {
                        put("type", "boolean")
                        put("description", "是否替换全部匹配，默认 false。")
                    }
                }
                putJsonArray("required") {
                    add("path")
                    add("oldText")
                    add("newText")
                }
            }
        }
    }

    val fileListTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "file_list")
            put("displayName", "列出文件")
            put("toolType", "workspace")
            put("description", "列出某个目录下的文件和子目录。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "目录路径。默认当前 workspace。")
                    }
                    putJsonObject("recursive") {
                        put("type", "boolean")
                        put("description", "是否递归列出。默认 false。")
                    }
                    putJsonObject("maxDepth") {
                        put("type", "integer")
                        put("description", "递归时最大深度，默认 2，范围 1-6。")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "最多返回多少项，默认 200，范围 1-1000。")
                    }
                }
            }
        }
    }

    val fileSearchTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "file_search")
            put("displayName", "搜索文件")
            put("toolType", "workspace")
            put("description", "在目录中递归搜索文件名或文本内容。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "搜索起始目录，默认当前 workspace。")
                    }
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "要搜索的关键词。")
                    }
                    putJsonObject("caseSensitive") {
                        put("type", "boolean")
                        put("description", "是否区分大小写，默认 false。")
                    }
                    putJsonObject("maxResults") {
                        put("type", "integer")
                        put("description", "最多返回结果数，默认 50，范围 1-200。")
                    }
                }
                putJsonArray("required") {
                    add("query")
                }
            }
        }
    }

    val fileStatTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "file_stat")
            put("displayName", "查看文件信息")
            put("toolType", "workspace")
            put("description", "查看文件或目录的元信息。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "目标路径。")
                    }
                }
                putJsonArray("required") {
                    add("path")
                }
            }
        }
    }

    val fileMoveTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "file_move")
            put("displayName", "移动文件")
            put("toolType", "workspace")
            put("description", "移动或重命名 workspace 中的文件。")
            put("postToolRule", "移动后等待结果，再决定是否继续读取。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("sourcePath") {
                        put("type", "string")
                        put("description", "源路径。")
                    }
                    putJsonObject("targetPath") {
                        put("type", "string")
                        put("description", "目标路径。")
                    }
                    putJsonObject("overwrite") {
                        put("type", "boolean")
                        put("description", "是否覆盖目标文件，默认 false。")
                    }
                }
                putJsonArray("required") {
                    add("sourcePath")
                    add("targetPath")
                }
            }
        }
    }

    val skillsListTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "skills_list")
            put("displayName", "列出 Skills")
            put("toolType", "skill")
            put("description", "列出当前已安装的 skills 索引，包括 id、名称、可用性、路径和能力目录。用户询问有哪些 skills、某类 skill 是否已安装，或你想先查目录再决定读取 SKILL.md 时优先调用。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "可选关键词，匹配 skill id、名称、描述或路径。")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "返回数量上限，默认 50，范围 1-200。")
                    }
                    putJsonObject("availableOnly") {
                        put("type", "boolean")
                        put("description", "是否只返回当前环境可用的 skills。默认 false。")
                    }
                }
            }
        }
    }

    val skillsReadTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "skills_read")
            put("displayName", "读取 Skill")
            put("toolType", "skill")
            put("description", "按 skill id、名称或路径读取某个已安装 skill 的 SKILL.md 正文和相关目录信息。当你知道某个 skill 可能相关，但本轮只掌握索引信息时调用。")
            put("postToolRule", "读取 skill 后等待结果，再根据返回的正文、scripts、references、assets 路径决定下一步。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("skillId") {
                        put("type", "string")
                        put("description", "skill 的 id、名称、SKILL.md 路径或 skill 根目录路径。建议先用 skills_list 查看。")
                    }
                    putJsonObject("maxChars") {
                        put("type", "integer")
                        put("description", "最多返回多少字符的正文，默认 16000，范围 512-64000。")
                    }
                }
                putJsonArray("required") {
                    add("skillId")
                }
            }
        }
    }

    val scheduleTaskCreateTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "schedule_task_create")
            put("displayName", "创建定时任务")
            put("toolType", "schedule")
            put("description", "创建新的定时任务。执行后等待工具结果，再决定是否回复用户。")
            put("postToolRule", "创建完成后不要在同一轮继续调用其他工具；请等待工具结果，并通过 response 输出最终答复。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("title") { put("type", "string") }
                    putJsonObject("targetKind") {
                        put("type", "string")
                        putJsonArray("enum") {
                            add("vlm")
                        }
                    }
                    putJsonObject("goal") { put("type", "string") }
                    putJsonObject("packageName") { put("type", "string") }
                    putJsonObject("scheduleType") {
                        put("type", "string")
                        putJsonArray("enum") {
                            add("fixed_time")
                            add("countdown")
                        }
                    }
                    putJsonObject("fixedTime") { put("type", "string") }
                    putJsonObject("countdownMinutes") { put("type", "integer") }
                    putJsonObject("repeatDaily") { put("type", "boolean") }
                    putJsonObject("enabled") { put("type", "boolean") }
                }
                putJsonArray("required") {
                    add("title")
                    add("targetKind")
                    add("packageName")
                    add("scheduleType")
                    add("repeatDaily")
                }
            }
        }
    }

    val scheduleTaskListTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "schedule_task_list")
            put("displayName", "查看定时任务")
            put("toolType", "schedule")
            put("description", "查看当前已有的定时任务列表。执行后等待工具结果。")
            put("postToolRule", "查看结果后再决定是否需要修改、删除或向用户总结。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {}
            }
        }
    }

    val scheduleTaskUpdateTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "schedule_task_update")
            put("displayName", "修改定时任务")
            put("toolType", "schedule")
            put("description", "修改已有定时任务的时间、标题、每日重复或启停状态。")
            put("postToolRule", "修改完成后不要同轮回复，等待工具结果。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("taskId") { put("type", "string") }
                    putJsonObject("title") { put("type", "string") }
                    putJsonObject("fixedTime") { put("type", "string") }
                    putJsonObject("countdownMinutes") { put("type", "integer") }
                    putJsonObject("repeatDaily") { put("type", "boolean") }
                    putJsonObject("enabled") { put("type", "boolean") }
                }
                putJsonArray("required") {
                    add("taskId")
                }
            }
        }
    }

    val scheduleTaskDeleteTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "schedule_task_delete")
            put("displayName", "删除定时任务")
            put("toolType", "schedule")
            put("description", "删除已有定时任务。执行后等待工具结果。")
            put("postToolRule", "删除完成后等待工具结果，再输出最终回复。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("taskId") { put("type", "string") }
                }
                putJsonArray("required") {
                    add("taskId")
                }
            }
        }
    }

    val alarmReminderCreateTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "alarm_reminder_create")
            put("displayName", "创建提醒闹钟")
            put("toolType", "alarm")
            put(
                "description",
                "创建提醒闹钟。exact_alarm 模式使用 AlarmManager 精确提醒；clock_app 模式调用系统闹钟应用创建闹钟；若用户未明确指定，优先使用 exact_alarm。用于单纯提醒，不执行自动化任务。"
            )
            put("postToolRule", "创建后等待工具结果，再决定是否继续。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("mode") {
                        put("type", "string")
                        putJsonArray("enum") {
                            add("exact_alarm")
                            add("clock_app")
                        }
                        put("description", "闹钟模式：exact_alarm=应用内精确提醒；clock_app=系统闹钟。")
                    }
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "提醒标题。")
                    }
                    putJsonObject("triggerAt") {
                        put("type", "string")
                        put("description", "触发时间，ISO-8601 格式，例如 2026-03-17T21:30:00+08:00。")
                    }
                    putJsonObject("message") {
                        put("type", "string")
                        put("description", "可选提醒内容。")
                    }
                    putJsonObject("timezone") {
                        put("type", "string")
                        put("description", "可选 IANA 时区，未传默认系统时区。")
                    }
                    putJsonObject("allowWhileIdle") {
                        put("type", "boolean")
                        put("description", "仅 exact_alarm 模式生效，是否在待机时也精确触发。默认 true。")
                    }
                    putJsonObject("skipUi") {
                        put("type", "boolean")
                        put("description", "仅 clock_app 模式生效，是否尝试跳过系统闹钟界面。默认 false。")
                    }
                }
                putJsonArray("required") {
                    add("mode")
                    add("title")
                    add("triggerAt")
                }
            }
        }
    }

    val alarmReminderListTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "alarm_reminder_list")
            put("displayName", "查看提醒闹钟")
            put("toolType", "alarm")
            put("description", "查看由本应用创建并托管的 exact_alarm 提醒闹钟列表。")
            put("postToolRule", "查看结果后再决定是否删除或继续创建。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {}
            }
        }
    }

    val alarmReminderDeleteTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "alarm_reminder_delete")
            put("displayName", "删除提醒闹钟")
            put("toolType", "alarm")
            put("description", "按 alarmId 删除本应用创建并托管的 exact_alarm 提醒闹钟。")
            put("postToolRule", "删除后等待工具结果，再向用户确认。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("alarmId") {
                        put("type", "string")
                        put("description", "闹钟 ID。")
                    }
                }
                putJsonArray("required") {
                    add("alarmId")
                }
            }
        }
    }

    val calendarListTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "calendar_list")
            put("displayName", "查看日历列表")
            put("toolType", "calendar")
            put("description", "查询设备日历账户列表，可用于选择 calendarId。")
            put("postToolRule", "查看结果后再决定新建或管理日程。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("writableOnly") {
                        put("type", "boolean")
                        put("description", "是否仅返回可写日历。默认 true。")
                    }
                    putJsonObject("visibleOnly") {
                        put("type", "boolean")
                        put("description", "是否仅返回可见日历。默认 true。")
                    }
                }
            }
        }
    }

    val calendarEventCreateTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "calendar_event_create")
            put("displayName", "创建日程")
            put("toolType", "calendar")
            put("description", "创建日历事件。用于管理日程，不触发自动化任务。")
            put("postToolRule", "创建后等待工具结果，再向用户确认。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("title") { put("type", "string") }
                    putJsonObject("startAt") {
                        put("type", "string")
                        put("description", "开始时间，ISO-8601。")
                    }
                    putJsonObject("endAt") {
                        put("type", "string")
                        put("description", "结束时间，ISO-8601。")
                    }
                    putJsonObject("calendarId") {
                        put("type", "string")
                        put("description", "可选，目标日历 ID。")
                    }
                    putJsonObject("description") { put("type", "string") }
                    putJsonObject("location") { put("type", "string") }
                    putJsonObject("timezone") {
                        put("type", "string")
                        put("description", "可选 IANA 时区，未传默认系统时区。")
                    }
                    putJsonObject("allDay") { put("type", "boolean") }
                    putJsonObject("reminderMinutes") {
                        put("type", "array")
                        put("description", "提醒分钟列表，例如 [10, 30]。")
                        putJsonObject("items") {
                            put("type", "integer")
                        }
                    }
                }
                putJsonArray("required") {
                    add("title")
                    add("startAt")
                    add("endAt")
                }
            }
        }
    }

    val calendarEventListTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "calendar_event_list")
            put("displayName", "查询日程")
            put("toolType", "calendar")
            put("description", "按时间范围、关键字、calendarId 查询日历事件。")
            put("postToolRule", "查看结果后再决定是否更新或删除。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("calendarId") { put("type", "string") }
                    putJsonObject("startAt") {
                        put("type", "string")
                        put("description", "可选，查询起始时间，ISO-8601。")
                    }
                    putJsonObject("endAt") {
                        put("type", "string")
                        put("description", "可选，查询结束时间，ISO-8601。")
                    }
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "可选关键词，匹配标题或地点。")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "可选返回上限，默认 50，范围 1-200。")
                    }
                }
            }
        }
    }

    val calendarEventUpdateTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "calendar_event_update")
            put("displayName", "修改日程")
            put("toolType", "calendar")
            put("description", "按 eventId 修改日历事件。")
            put("postToolRule", "修改后等待工具结果，再向用户同步。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("eventId") {
                        put("type", "string")
                        put("description", "事件 ID。")
                    }
                    putJsonObject("title") { put("type", "string") }
                    putJsonObject("startAt") { put("type", "string") }
                    putJsonObject("endAt") { put("type", "string") }
                    putJsonObject("description") { put("type", "string") }
                    putJsonObject("location") { put("type", "string") }
                    putJsonObject("timezone") { put("type", "string") }
                    putJsonObject("allDay") { put("type", "boolean") }
                    putJsonObject("reminderMinutes") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "integer")
                        }
                    }
                }
                putJsonArray("required") {
                    add("eventId")
                }
            }
        }
    }

    val calendarEventDeleteTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "calendar_event_delete")
            put("displayName", "删除日程")
            put("toolType", "calendar")
            put("description", "按 eventId 删除日历事件。")
            put("postToolRule", "删除后等待工具结果，再向用户确认。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("eventId") { put("type", "string") }
                }
                putJsonArray("required") {
                    add("eventId")
                }
            }
        }
    }

    val mem0ConfigureTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", Mem0ToolUtils.TOOL_CONFIGURE)
            put("displayName", Mem0ToolUtils.displayName(Mem0ToolUtils.TOOL_CONFIGURE))
            put("toolType", "mem0")
            put("description", "调用 Mem0 /configure 接口调整服务端配置。谨慎使用，仅在用户明确要求调整记忆服务配置时使用。")
            put("postToolRule", "配置完成后等待工具结果，再决定是否继续。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("payload") {
                        put("type", "object")
                        put("description", "原样透传到 /configure 的配置对象。")
                    }
                }
                putJsonArray("required") {
                    add("payload")
                }
            }
        }
    }

    val mem0AddTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", Mem0ToolUtils.TOOL_ADD)
            put("displayName", Mem0ToolUtils.displayName(Mem0ToolUtils.TOOL_ADD))
            put("toolType", "mem0")
            put("description", "将长期偏好、稳定身份信息或长期约束写入 Mem0。对明显有长期价值的用户事实要更积极使用；不要写入一次性临时信息。若已存在同主题记忆，优先改用 mem0_update 合并，而不是新增近重复记忆。")
            put("postToolRule", "写入后等待工具结果，再向用户确认已记住。若已存在同主题记忆，优先改用 mem0_update 合并。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("memory") {
                        put("type", "string")
                        put("description", "要写入的记忆文本。")
                    }
                    putJsonObject("metadata") {
                        put("type", "object")
                        put("description", "附加元数据。系统会自动补充 run_id。")
                    }
                    putJsonObject("categories") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "string")
                        }
                    }
                    putJsonObject("payload") {
                        put("type", "object")
                        put("description", "需要透传给 /memories 的其他字段。")
                    }
                }
                putJsonArray("required") {
                    add("memory")
                }
            }
        }
    }

    val mem0ListTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", Mem0ToolUtils.TOOL_LIST)
            put("displayName", Mem0ToolUtils.displayName(Mem0ToolUtils.TOOL_LIST))
            put("toolType", "mem0")
            put("description", "读取当前 Mem0 记忆空间下的记忆列表。")
            put("postToolRule", "读取后等待工具结果，再决定是否总结、删除或继续检索。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("limit") { put("type", "integer") }
                    putJsonObject("filters") {
                        put("type", "object")
                        put("description", "附加筛选条件。")
                    }
                    putJsonObject("payload") {
                        put("type", "object")
                        put("description", "透传给 /memories 的其他查询参数。")
                    }
                }
            }
        }
    }

    val mem0GetTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", Mem0ToolUtils.TOOL_GET)
            put("displayName", Mem0ToolUtils.displayName(Mem0ToolUtils.TOOL_GET))
            put("toolType", "mem0")
            put("description", "按 ID 读取单条记忆详情。")
            put("postToolRule", "读取后等待工具结果，再继续。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("memoryId") {
                        put("type", "string")
                        put("description", "记忆 ID。")
                    }
                    putJsonObject("payload") {
                        put("type", "object")
                        put("description", "透传给 /memories/{id} 的附加参数。")
                    }
                }
                putJsonArray("required") {
                    add("memoryId")
                }
            }
        }
    }

    val mem0UpdateTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", Mem0ToolUtils.TOOL_UPDATE)
            put("displayName", Mem0ToolUtils.displayName(Mem0ToolUtils.TOOL_UPDATE))
            put("toolType", "mem0")
            put("description", "按 ID 修改已有记忆。若本轮事实与已有记忆属于同一主题或同一偏好簇，应优先用它来合并信息，避免新增重复记忆。")
            put("postToolRule", "更新后等待工具结果，再向用户同步。更新时应尽量保留原有稳定信息，并把本轮新增事实合并到同一条记忆。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("memoryId") { put("type", "string") }
                    putJsonObject("memory") { put("type", "string") }
                    putJsonObject("metadata") { put("type", "object") }
                    putJsonObject("categories") {
                        put("type", "array")
                        putJsonObject("items") { put("type", "string") }
                    }
                    putJsonObject("payload") {
                        put("type", "object")
                        put("description", "透传给 /memories/{id} 的其他字段。")
                    }
                }
                putJsonArray("required") {
                    add("memoryId")
                }
            }
        }
    }

    val mem0DeleteTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", Mem0ToolUtils.TOOL_DELETE)
            put("displayName", Mem0ToolUtils.displayName(Mem0ToolUtils.TOOL_DELETE))
            put("toolType", "mem0")
            put("description", "按 ID 删除单条记忆。")
            put("postToolRule", "删除后等待工具结果，再告知用户。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("memoryId") { put("type", "string") }
                    putJsonObject("payload") {
                        put("type", "object")
                        put("description", "透传给 /memories/{id} 的附加参数。")
                    }
                }
                putJsonArray("required") {
                    add("memoryId")
                }
            }
        }
    }

    val mem0HistoryTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", Mem0ToolUtils.TOOL_HISTORY)
            put("displayName", Mem0ToolUtils.displayName(Mem0ToolUtils.TOOL_HISTORY))
            put("toolType", "mem0")
            put("description", "读取某条记忆的历史版本。")
            put("postToolRule", "读取后等待工具结果，再继续。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("memoryId") { put("type", "string") }
                    putJsonObject("payload") {
                        put("type", "object")
                        put("description", "透传给 /memories/{id}/history 的附加参数。")
                    }
                }
                putJsonArray("required") {
                    add("memoryId")
                }
            }
        }
    }

    val mem0SearchTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", Mem0ToolUtils.TOOL_SEARCH)
            put("displayName", Mem0ToolUtils.displayName(Mem0ToolUtils.TOOL_SEARCH))
            put("toolType", "mem0")
            put("description", "按查询语句检索与当前 Mem0 记忆空间相关的长期记忆。若不确定该新增还是更新，可先搜索相似记忆，再决定是否调用 mem0_update。")
            put("postToolRule", "读取后等待工具结果，再判断是否继续。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "检索语句。")
                    }
                    putJsonObject("limit") { put("type", "integer") }
                    putJsonObject("filters") { put("type", "object") }
                    putJsonObject("payload") {
                        put("type", "object")
                        put("description", "透传给 /search 的其他字段。")
                    }
                }
                putJsonArray("required") {
                    add("query")
                }
            }
        }
    }

    val mem0DeleteAllTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", Mem0ToolUtils.TOOL_DELETE_ALL)
            put("displayName", Mem0ToolUtils.displayName(Mem0ToolUtils.TOOL_DELETE_ALL))
            put("toolType", "mem0")
            put("description", "清空当前 Mem0 记忆空间下的全部记忆。必须在用户明确二次确认后再调用。")
            put("postToolRule", "只有用户明确确认且 confirm=true 时才允许执行。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("confirm") {
                        put("type", "boolean")
                        put("description", "用户已明确二次确认时设为 true。")
                    }
                    putJsonObject("filters") { put("type", "object") }
                    putJsonObject("payload") {
                        put("type", "object")
                        put("description", "透传给 DELETE /memories 的其他字段。")
                    }
                }
            }
        }
    }

    val mem0ResetTool: JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", Mem0ToolUtils.TOOL_RESET)
            put("displayName", Mem0ToolUtils.displayName(Mem0ToolUtils.TOOL_RESET))
            put("toolType", "mem0")
            put("description", "重置当前 Mem0 记忆空间。必须在用户明确二次确认后再调用。")
            put("postToolRule", "只有用户明确确认且 confirm=true 时才允许执行。")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("confirm") {
                        put("type", "boolean")
                        put("description", "用户已明确二次确认时设为 true。")
                    }
                    putJsonObject("payload") {
                        put("type", "object")
                        put("description", "透传给 /reset 的附加字段。")
                    }
                }
            }
        }
    }

    val builtinTools: List<JsonObject> = listOf(
        contextAppsQueryTool,
        contextTimeNowTool,
        vlmTaskTool,
        terminalExecuteTool,
        terminalSessionStartTool,
        terminalSessionExecTool,
        terminalSessionReadTool,
        terminalSessionStopTool,
        browserUseTool,
        fileReadTool,
        fileWriteTool,
        fileEditTool,
        fileListTool,
        fileSearchTool,
        fileStatTool,
        fileMoveTool,
        skillsListTool,
        skillsReadTool
    )

    val scheduleTools: List<JsonObject> = listOf(
        scheduleTaskCreateTool,
        scheduleTaskListTool,
        scheduleTaskUpdateTool,
        scheduleTaskDeleteTool
    )

    val alarmTools: List<JsonObject> = listOf(
        alarmReminderCreateTool,
        alarmReminderListTool,
        alarmReminderDeleteTool
    )

    val calendarTools: List<JsonObject> = listOf(
        calendarListTool,
        calendarEventCreateTool,
        calendarEventListTool,
        calendarEventUpdateTool,
        calendarEventDeleteTool
    )

    val mem0Tools: List<JsonObject> = listOf(
        mem0ConfigureTool,
        mem0AddTool,
        mem0ListTool,
        mem0GetTool,
        mem0UpdateTool,
        mem0DeleteTool,
        mem0HistoryTool,
        mem0SearchTool,
        mem0DeleteAllTool,
        mem0ResetTool
    )

    fun staticTools(): List<JsonObject> = builtinTools + scheduleTools + alarmTools + calendarTools
}
