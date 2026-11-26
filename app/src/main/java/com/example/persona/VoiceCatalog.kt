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
    val femaleVoices: List<VoiceOption> = listOf(
        VoiceOption("Sulafat", "সুলাফাত (ডিফল্ট)", "উষ্ণ, স্বাভাবিক ও প্রাণবন্ত", VoiceGender.FEMALE),
        VoiceOption("Achernar", "আচেরনার", "স্পষ্ট ও আত্মবিশ্বাসী", VoiceGender.FEMALE),
        VoiceOption("Vindemiatrix", "ভিন্ডেমিয়াট্রিক্স", "মৃদু ও শান্ত", VoiceGender.FEMALE),
        VoiceOption("Despina", "ডেসপিনা", "কোমল ও আন্তরিক", VoiceGender.FEMALE),
        VoiceOption("Leda", "লেদা", "তরুণ ও চনমনে", VoiceGender.FEMALE),
        VoiceOption("Aoede", "আওইডে", "সুরেলা ও কথোপকথনমুখী", VoiceGender.FEMALE),
        VoiceOption("Laomedeia", "লাওমিডিয়া", "উচ্ছ্বসিত ও হাসিখুশি", VoiceGender.FEMALE),
        VoiceOption("Callirrhoe", "ক্যালিরহো", "সহজ ও ঘরোয়া", VoiceGender.FEMALE)
    )

    val maleVoices: List<VoiceOption> = listOf(
        VoiceOption("Puck", "পাক", "চনমনে ও বন্ধুত্বপূর্ণ", VoiceGender.MALE),
        VoiceOption("Charon", "শ্যারন", "গম্ভীর ও স্থির", VoiceGender.MALE),
        VoiceOption("Achird", "আচির্ড", "সহজ ও সাবলীল", VoiceGender.MALE),
        VoiceOption("Algieba", "আলজিবা", "ভরাট ও স্পষ্ট", VoiceGender.MALE)
    )

    val allVoices: List<VoiceOption> = femaleVoices + maleVoices

    fun findByName(name: String?): VoiceOption =
        allVoices.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: femaleVoices.first()
}
