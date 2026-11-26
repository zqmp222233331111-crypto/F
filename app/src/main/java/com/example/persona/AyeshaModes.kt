package com.example.persona

enum class AyeshaMode(
    val id: String,
    val titleBn: String,
    val subtitleBn: String,
    val isRelationshipMode: Boolean = false,
    val isIslamicMode: Boolean = false,
    val isCustomMode: Boolean = false,
    val isPrimaryHomeChip: Boolean = true
) {
    ASSISTANT(
        id = "assistant",
        titleBn = "সহকারী",
        subtitleBn = "দৈনন্দিন কাজ ও তথ্য",
        isPrimaryHomeChip = true
    ),
    FRIEND(
        id = "friend",
        titleBn = "বন্ধু",
        subtitleBn = "আন্তরিক আড্ডা ও গল্প",
        isPrimaryHomeChip = true
    ),
    ISLAMIC(
        id = "islamic",
        titleBn = "ইসলামিক",
        subtitleBn = "কুরআন, হাদিস ও আদব",
        isIslamicMode = true,
        isPrimaryHomeChip = true
    ),
    CUSTOM(
        id = "custom",
        titleBn = "কাস্টম",
        subtitleBn = "নিজের তৈরি চরিত্র",
        isCustomMode = true,
        isPrimaryHomeChip = true
    ),
    FUN_COMPANION(
        id = "fun_companion",
        titleBn = "মজার সঙ্গী",
        subtitleBn = "রসিকতা ও হালকা মেজাজ",
        isPrimaryHomeChip = false
    ),
    TEACHER(
        id = "teacher",
        titleBn = "শিক্ষক",
        subtitleBn = "সহজ ভাষায় শেখা",
        isPrimaryHomeChip = false
    ),
    ENGLISH_PRACTICE(
        id = "english_practice",
        titleBn = "ইংরেজি প্র্যাকটিস",
        subtitleBn = "কথোপকথনে ইংরেজি চর্চা",
        isPrimaryHomeChip = false
    ),
    MOTIVATOR(
        id = "motivator",
        titleBn = "মোটিভেটর",
        subtitleBn = "উৎসাহ ও মানসিক শক্তি",
        isPrimaryHomeChip = false
    ),
    STORYTELLER(
        id = "storyteller",
        titleBn = "গল্পকার",
        subtitleBn = "আকর্ষণীয় গল্প বলা",
        isPrimaryHomeChip = false
    ),
    QUIZ_RIDDLE(
        id = "quiz_riddle",
        titleBn = "কুইজ ও ধাঁধা",
        subtitleBn = "মজার প্রশ্নোত্তর খেলা",
        isPrimaryHomeChip = false
    ),
    GIRLFRIEND(
        id = "girlfriend",
        titleBn = "গার্লফ্রেন্ড",
        subtitleBn = "স্নেহশীল সঙ্গী (১৮+)",
        isRelationshipMode = true,
        isPrimaryHomeChip = false
    ),
    WIFE(
        id = "wife",
        titleBn = "ওয়াইফ",
        subtitleBn = "যত্নশীল জীবনসঙ্গী চরিত্র (১৮+)",
        isRelationshipMode = true,
        isPrimaryHomeChip = false
    ),
    BOYFRIEND(
        id = "boyfriend",
        titleBn = "বয়ফ্রেন্ড",
        subtitleBn = "যত্নশীল সঙ্গী চরিত্র (১৮+)",
        isRelationshipMode = true,
        isPrimaryHomeChip = false
    );

    companion object {
        fun fromId(id: String?): AyeshaMode =
            entries.firstOrNull { it.id == id } ?: ASSISTANT

        val homeQuickChips: List<AyeshaMode> = listOf(
            ASSISTANT, FRIEND, ISLAMIC, CUSTOM
        )
    }
}
