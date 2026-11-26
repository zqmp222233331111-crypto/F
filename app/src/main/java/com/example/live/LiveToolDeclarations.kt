package com.example.live

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds Gemini Live API tool declarations for Memory, Phone Control (15 tools),
 * and Google Search grounding (Islamic Mode).
 */
object LiveToolDeclarations {

    val phoneToolNames: List<String> = listOf(
        "list_apps", "open_app", "read_screen", "tap", "long_press",
        "scroll", "swipe", "type_text", "clear_text", "press_back",
        "go_home", "open_recents", "open_notifications", "wait_for_screen", "stop_task"
    )

    fun expectedFunctionToolNames(
        includeMemoryTools: Boolean,
        includePhoneControlTools: Boolean
    ): List<String> = buildList {
        if (includeMemoryTools) {
            add("save_memory")
            add("delete_memory")
        }
        if (includePhoneControlTools) {
            addAll(phoneToolNames)
        }
    }

    fun buildToolsArray(
        includeMemoryTools: Boolean,
        includePhoneControlTools: Boolean,
        includeGoogleSearch: Boolean
    ): JSONArray {
        val toolsArray = JSONArray()
        val functionDeclarations = JSONArray()

        if (includeMemoryTools) {
            functionDeclarations.put(
                fn(
                    name = "save_memory",
                    description = "Save an important personal fact, preference, or life event about the user locally. NEVER save passwords, OTPs, PINs, or card numbers.",
                    properties = mapOf(
                        "key" to prop("STRING", "Short topic key, e.g. user_name, favorite_food, exam_date"),
                        "value" to prop("STRING", "The fact value to remember")
                    ),
                    required = listOf("key", "value")
                )
            )
            functionDeclarations.put(
                fn(
                    name = "delete_memory",
                    description = "Delete a previously saved memory fact when the user asks to forget it.",
                    properties = mapOf(
                        "key" to prop("STRING", "The topic key to remove")
                    ),
                    required = listOf("key")
                )
            )
        }

        if (includePhoneControlTools) {
            functionDeclarations.put(
                fn("list_apps", "List installed non-banking launcher apps on the phone.", emptyMap(), emptyList())
            )
            functionDeclarations.put(
                fn(
                    "open_app",
                    "Open an installed app by name or package name. Refuses banking/wallet apps.",
                    mapOf("app_name" to prop("STRING", "Visible app name or package name")),
                    listOf("app_name")
                )
            )
            functionDeclarations.put(
                fn("read_screen", "Read visible UI elements with node_id, text, clickable, editable, bounds. Redacts password/OTP/PIN fields.", emptyMap(), emptyList())
            )
            functionDeclarations.put(
                fn(
                    "tap",
                    "Tap a UI element by node_id (preferred) or x,y coordinates.",
                    mapOf(
                        "node_id" to prop("STRING", "Element id from read_screen"),
                        "x" to prop("NUMBER", "Optional screen X coordinate"),
                        "y" to prop("NUMBER", "Optional screen Y coordinate")
                    ),
                    emptyList()
                )
            )
            functionDeclarations.put(
                fn(
                    "long_press",
                    "Long-press a UI element by node_id or x,y coordinates.",
                    mapOf(
                        "node_id" to prop("STRING", "Element id from read_screen"),
                        "x" to prop("NUMBER", "Optional screen X coordinate"),
                        "y" to prop("NUMBER", "Optional screen Y coordinate")
                    ),
                    emptyList()
                )
            )
            functionDeclarations.put(
                fn(
                    "scroll",
                    "Scroll the screen or a scrollable container.",
                    mapOf(
                        "direction" to prop("STRING", "up, down, left, or right"),
                        "node_id" to prop("STRING", "Optional scrollable node_id")
                    ),
                    listOf("direction")
                )
            )
            functionDeclarations.put(
                fn(
                    "swipe",
                    "Perform a custom swipe gesture between coordinates.",
                    mapOf(
                        "x1" to prop("NUMBER", "Start X"),
                        "y1" to prop("NUMBER", "Start Y"),
                        "x2" to prop("NUMBER", "End X"),
                        "y2" to prop("NUMBER", "End Y"),
                        "duration_ms" to prop("INTEGER", "Gesture duration in ms")
                    ),
                    listOf("x1", "y1", "x2", "y2")
                )
            )
            functionDeclarations.put(
                fn(
                    "type_text",
                    "Type Bengali or English Unicode text into a non-sensitive input field.",
                    mapOf(
                        "node_id" to prop("STRING", "Editable element id from read_screen"),
                        "text" to prop("STRING", "Text to type")
                    ),
                    listOf("text")
                )
            )
            functionDeclarations.put(
                fn(
                    "clear_text",
                    "Clear text in an editable input field.",
                    mapOf("node_id" to prop("STRING", "Editable element id")),
                    emptyList()
                )
            )
            functionDeclarations.put(fn("press_back", "Press the Android Back button.", emptyMap(), emptyList()))
            functionDeclarations.put(fn("go_home", "Go to the Android Home screen.", emptyMap(), emptyList()))
            functionDeclarations.put(fn("open_recents", "Open Android Recent Apps screen.", emptyMap(), emptyList()))
            functionDeclarations.put(fn("open_notifications", "Pull down the notification shade.", emptyMap(), emptyList()))
            functionDeclarations.put(
                fn(
                    "wait_for_screen",
                    "Wait briefly for UI transition and return updated read_screen.",
                    mapOf("ms" to prop("INTEGER", "Milliseconds to wait (200-5000)")),
                    emptyList()
                )
            )
            functionDeclarations.put(fn("stop_task", "Immediately stop the current phone automation task.", emptyMap(), emptyList()))
        }

        if (functionDeclarations.length() > 0) {
            toolsArray.put(JSONObject().put("functionDeclarations", functionDeclarations))
        }

        if (includeGoogleSearch) {
            toolsArray.put(JSONObject().put("googleSearch", JSONObject()))
        }

        return toolsArray
    }

    private fun fn(
        name: String,
        description: String,
        properties: Map<String, JSONObject>,
        required: List<String>
    ): JSONObject {
        val propsObj = JSONObject()
        properties.forEach { (k, v) -> propsObj.put(k, v) }
        val params = JSONObject()
            .put("type", "OBJECT")
            .put("properties", propsObj)
        if (required.isNotEmpty()) {
            params.put("required", JSONArray(required))
        }
        return JSONObject()
            .put("name", name)
            .put("description", description)
            .put("parameters", params)
    }

    private fun prop(type: String, description: String): JSONObject =
        JSONObject()
            .put("type", type)
            .put("description", description)
}
