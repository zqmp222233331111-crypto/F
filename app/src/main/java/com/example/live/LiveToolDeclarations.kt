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
        add("web_search")
        add("get_latest_shorts_draft")
        add("get_trending_creator_ideas")
        add("save_creator_audit")
        add("get_creator_audit_history")
        add("change_ayesha_outfit")
        add("set_ayesha_emotion")
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
        @Suppress("UNUSED_PARAMETER") includeGoogleSearch: Boolean
    ): JSONArray {
        val toolsArray = JSONArray()
        val functionDeclarations = JSONArray()

        functionDeclarations.put(
            fn(
                name = "web_search",
                description = "Search Google live for real-time facts: today's news, weather, live sports/cricket scores, product prices, exchange rates, recent events, or verifying Quran/Hadith references. Works in all modes.",
                properties = mapOf(
                    "query" to prop("STRING", "Clear search query in Bengali or English")
                ),
                required = listOf("query")
            )
        )

        functionDeclarations.put(
            fn(
                name = "get_latest_shorts_draft",
                description = "Fetch the latest analyzed video pack (YouTube Shorts, Facebook Reels, TikTok titles, descriptions, hashtags, viral score, weaknesses, and rescue fixes) so you can advise or type it into YouTube, YouTube Studio, Facebook, or TikTok.",
                properties = emptyMap(),
                required = emptyList()
            )
        )

        functionDeclarations.put(
            fn(
                name = "get_trending_creator_ideas",
                description = "Search live trending topics, hooks, tags, and best posting times across YouTube Shorts, Facebook Reels, and TikTok for any video niche (only call when user asks about content/trends or shares a creator app screen).",
                properties = mapOf(
                    "niche" to prop("STRING", "Video category/topic, e.g. '2D cartoon comedy', 'tech review', 'islamic', 'cooking'"),
                    "platform" to prop("STRING", "Optional: YouTube Shorts, Facebook Reels, TikTok, or All")
                ),
                required = listOf("niche")
            )
        )

        functionDeclarations.put(
            fn(
                name = "save_creator_audit",
                description = "Save a local Creator Growth Log entry after checking YouTube Studio / TikTok / Facebook analytics or updating a video's title/description so future calls can compare performance.",
                properties = mapOf(
                    "platform" to prop("STRING", "e.g. YouTube Studio, YouTube Shorts, Facebook Reels, TikTok"),
                    "video_title" to prop("STRING", "Title or topic of the audited video"),
                    "category" to prop("STRING", "Detected content niche/category"),
                    "viral_score" to prop("INTEGER", "Optional 0-100 score (-1 if Studio analytics)"),
                    "views_count" to prop("INTEGER", "Observed views count (-1 if not shown)"),
                    "ctr_percent" to prop("NUMBER", "Observed CTR percentage like 4.8 (-1 if not shown)"),
                    "retention_percent" to prop("NUMBER", "Observed average % viewed / retention like 72.5 (-1 if not shown)"),
                    "problem_summary" to prop("STRING", "Concise Bengali summary of what issue was spotted"),
                    "action_taken" to prop("STRING", "Concise Bengali summary of fix suggested or applied")
                ),
                required = listOf("platform", "video_title", "problem_summary", "action_taken")
            )
        )

        functionDeclarations.put(
            fn(
                name = "get_creator_audit_history",
                description = "Retrieve previous Creator Growth Log audits (scores, past YouTube Studio views/CTR/retention, and past actions) to compare progress during follow-up.",
                properties = mapOf(
                    "limit" to prop("INTEGER", "Optional number of recent entries (default 8)")
                ),
                required = emptyList()
            )
        )

        functionDeclarations.put(
            fn(
                name = "change_ayesha_outfit",
                description = "Change Ayesha's visible dress/outfit on the live call screen right now when the user asks to wear another dress, dislikes the current outfit, or asks for yellow, white_floral, black_heart, or red_rose.",
                properties = mapOf(
                    "outfit_id" to prop("STRING", "One of: yellow, white_floral, black_heart, red_rose, or next")
                ),
                required = listOf("outfit_id")
            )
        )

        functionDeclarations.put(
            fn(
                name = "set_ayesha_emotion",
                description = "Update Ayesha's live facial expression and body mood on screen to match your vocal emotion: warm, happy, pouting (অভিমান), teary (কান্না-কান্না আবেগ), or shy (লাজুক ভালোবাসা).",
                properties = mapOf(
                    "emotion" to prop("STRING", "One of: warm, happy, pouting, teary, shy")
                ),
                required = listOf("emotion")
            )
        )

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
                    "Open an installed app by name (English or Bengali) or package name, wait briefly for it to load, and return initial screen elements. Refuses banking/wallet apps.",
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
                    "Type Bengali or English Unicode text into a non-sensitive input field. Optionally set submit=true to press Search/Enter/Done right after typing.",
                    mapOf(
                        "node_id" to prop("STRING", "Editable element id from read_screen (optional if already focused or first visible input box)"),
                        "text" to prop("STRING", "Text to type"),
                        "submit" to prop("BOOLEAN", "Optional: set true to trigger Search/Enter/Send after typing")
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

        return toolsArray
    }

    private fun fn(
        name: String,
        description: String,
        properties: Map<String, JSONObject>,
        required: List<String>
    ): JSONObject {
        val obj = JSONObject()
            .put("name", name)
            .put("description", description)

        if (properties.isNotEmpty()) {
            val propsObj = JSONObject()
            properties.forEach { (k, v) -> propsObj.put(k, v) }
            val params = JSONObject()
                .put("type", "OBJECT")
                .put("properties", propsObj)
            if (required.isNotEmpty()) {
                params.put("required", JSONArray(required))
            }
            obj.put("parameters", params)
        }
        return obj
    }

    private fun prop(type: String, description: String): JSONObject =
        JSONObject()
            .put("type", type)
            .put("description", description)
}
