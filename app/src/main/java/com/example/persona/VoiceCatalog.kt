package com.example.persona

enum class VoiceGender(val labelBn: String) {
    FEMALE("নারী কণ্ঠ"),
    MALE("পুরুষ কণ্ঠ")
}

data class VoiceOption(
    val name: String,
    val labelBn: String,
    val descriptionBn: String,
    val gender: VoiceGender
)

object VoiceCatalog {
    // All 14 official Gemini Live prebuilt female voices
    val femaleVoices: List<VoiceOption> = listOf(
        VoiceOption("Sulafat", "সুলাফাত (ডিফল্ট)", "উষ্ণ, স্বাভাবিক ও প্রাণবন্ত", VoiceGender.FEMALE),
        VoiceOption("Zephyr", "জেফির", "উজ্জ্বল, মিষ্টি ও চনমনে", VoiceGender.FEMALE),
        VoiceOption("Despina", "ডেসপিনা", "কোমল, মিষ্টি ও আন্তরিক", VoiceGender.FEMALE),
        VoiceOption("Aoede", "আওইডে", "সুরেলা, হালকা ও কথোপকথনমুখী", VoiceGender.FEMALE),
        VoiceOption("Leda", "লেদা", "তরুণ, মিষ্টি ও প্রাণচঞ্চল", VoiceGender.FEMALE),
        VoiceOption("Kore", "কোর", "দৃঢ়, স্পষ্ট ও আত্মবিশ্বাসী", VoiceGender.FEMALE),
        VoiceOption("Autonoe", "অটোনো", "প্রাণবন্ত, উজ্জ্বল ও উৎসাহী", VoiceGender.FEMALE),
        VoiceOption("Vindemiatrix", "ভিন্ডেমিয়াট্রিক্স", "মৃদু, শান্ত ও স্নিগ্ধ", VoiceGender.FEMALE),
        VoiceOption("Erinome", "এরিনোম", "পরিষ্কার, মার্জিত ও সাবলীল", VoiceGender.FEMALE),
        VoiceOption("Laomedeia", "লাওমিডিয়া", "উচ্ছ্বসিত, হাসিখুশি ও চটপটে", VoiceGender.FEMALE),
        VoiceOption("Callirrhoe", "ক্যালিরহো", "সহজ, ঘরোয়া ও বন্ধুত্বপূর্ণ", VoiceGender.FEMALE),
        VoiceOption("Achernar", "আচেরনার", "কোমল কিন্তু স্পষ্ট ও পেশাদার", VoiceGender.FEMALE),
        VoiceOption("Gacrux", "গ্যাক্রাক্স", "পরিণত, গভীর ও শান্ত", VoiceGender.FEMALE),
        VoiceOption("Pulcherrima", "পালকেরিমা", "উজ্জ্বল, সপ্রতিভ ও স্পষ্টভাষী", VoiceGender.FEMALE)
    )

    val allVoices: List<VoiceOption> = femaleVoices

    fun findByName(name: String?): VoiceOption =
        allVoices.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: femaleVoices.first()
}
