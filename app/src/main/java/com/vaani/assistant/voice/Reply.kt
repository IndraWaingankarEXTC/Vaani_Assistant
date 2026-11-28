package com.vaani.assistant.voice

import com.vaani.assistant.nlu.LanguageDetector

/**
 * Short spoken replies that follow the language you just used. Code-mixed speech gets a
 * code-mixed answer — "Ho gaya" rather than a stiff "Done" or a fully Hindi sentence, because
 * that is how the person in front of the phone actually talks.
 */
object Reply {

    private val table: Map<String, Map<String, String>> = mapOf(
        "ok" to mapOf(
            "en" to "Done",
            "hi" to "Ho gaya",
            "ta" to "Aachu", "te" to "Ayyipoyindi", "bn" to "Hoye geche",
            "kn" to "Aaytu", "ml" to "Aayi", "mr" to "Zale", "gu" to "Thai gayu"
        ),
        "listening" to mapOf(
            "en" to "Yes?", "hi" to "Haan, bolo?", "ta" to "Sollunga",
            "te" to "Cheppandi", "bn" to "Bolo", "kn" to "Heli", "ml" to "Parayu"
        ),
        "not_found" to mapOf(
            "en" to "I couldn't find %s on the screen",
            "hi" to "%s screen pe nahi mila",
            "ta" to "%s screen la illa", "te" to "%s screen lo ledu",
            "bn" to "%s screen e nei", "kn" to "%s screen alli illa",
            "ml" to "%s screen il illa"
        ),
        "no_app" to mapOf(
            "en" to "I couldn't find an app called %s",
            "hi" to "%s naam ka app nahi mila",
            "ta" to "%s app kidaikkala", "te" to "%s app dorakaledu",
            "bn" to "%s app paoa gelo na", "kn" to "%s app sigalilla",
            "ml" to "%s app kittiyilla"
        ),
        "no_contact" to mapOf(
            "en" to "I couldn't find %s in your contacts",
            "hi" to "%s contacts mein nahi mila",
            "ta" to "%s contacts la illa", "te" to "%s contacts lo ledu",
            "bn" to "%s contacts e nei", "kn" to "%s contacts alli illa",
            "ml" to "%s contacts il illa"
        ),
        "blocked_payment" to mapOf(
            "en" to "That's a payment screen, and I don't move money. Stopping here.",
            "hi" to "Ye payment ka screen hai. Main paisa nahi bhejta, ruk gaya.",
            "ta" to "Idhu payment screen. Naan panam anuppa maaten.",
            "te" to "Idi payment screen. Nenu dabbu pampanu.",
            "bn" to "Eta payment screen. Ami taka pathai na.",
            "kn" to "Idu payment screen. Naanu hana kalisalla.",
            "ml" to "Ithu payment screen. Njan panam ayakkilla."
        ),
        "blocked_piracy" to mapOf(
            "en" to "That looks like pirated content, so I won't touch it.",
            "hi" to "Ye pirated lag raha hai, main isse haath nahi lagaunga.",
            "ta" to "Idhu pirated maadhiri irukku, naan thoda maaten.",
            "te" to "Idi pirated la undi, nenu touch cheyanu.",
            "bn" to "Eta pirated mone hocche, ami chhobo na.",
            "kn" to "Idu pirated thara ide, naanu mudiyalla.",
            "ml" to "Ithu pirated pole undu, njan thodilla."
        ),
        "confirm" to mapOf(
            "en" to "%s looks destructive. Should I go ahead?",
            "hi" to "%s khatarnaak lag raha hai. Aage badhoon?",
            "ta" to "%s aabathu. Munna poganuma?",
            "te" to "%s praadhame. Munduku vellalaa?",
            "bn" to "%s bipodjonok. Egiye jaabo?",
            "kn" to "%s apayakari. Munde hogalaa?",
            "ml" to "%s apakaram. Munnot poovano?"
        ),
        "cancelled" to mapOf(
            "en" to "Okay, I left that alone", "hi" to "Theek hai, chhod diya",
            "ta" to "Sari, vittuten", "te" to "Sare, vadilesanu",
            "bn" to "Thik ache, chere dilam", "kn" to "Sari, bittu",
            "ml" to "Sheri, vitte"
        ),
        "need_screen" to mapOf(
            "en" to "Screen control is off. Turn on Vaani screen control in Accessibility settings.",
            "hi" to "Screen control band hai. Accessibility mein Vaani screen control on karo.",
            "ta" to "Screen control off la irukku. Accessibility la on pannunga.",
            "te" to "Screen control off lo undi. Accessibility lo on cheyandi.",
            "bn" to "Screen control bondho. Accessibility e on koro.",
            "kn" to "Screen control off ide. Accessibility alli on maadi.",
            "ml" to "Screen control off aanu. Accessibility il on cheyyu."
        ),
        "music_fail" to mapOf(
            "en" to "I couldn't reach the music player",
            "hi" to "Music player tak nahi pahunch paya",
            "ta" to "Music player kidaikkala", "te" to "Music player dorakaledu",
            "bn" to "Music player paoa gelo na", "kn" to "Music player sigalilla",
            "ml" to "Music player kittiyilla"
        ),
        "nothing_repeat" to mapOf(
            "en" to "There's nothing to repeat yet", "hi" to "Abhi repeat karne ko kuch nahi hai",
            "ta" to "Repeat panna onnum illa", "te" to "Repeat cheyadaniki emi ledu",
            "bn" to "Repeat korar kichu nei", "kn" to "Repeat maadalu enu illa",
            "ml" to "Repeat cheyyan onnumilla"
        ),
        "not_understood" to mapOf(
            "en" to "Sorry, I didn't catch a command in that",
            "hi" to "Sorry, usme koi command samajh nahi aayi",
            "ta" to "Sorry, adhula command puriyala", "te" to "Sorry, command artham kaledu",
            "bn" to "Sorry, command bujhlam na", "kn" to "Sorry, command gothagalilla",
            "ml" to "Sorry, command manassilayilla"
        ),
        "busy" to mapOf(
            "en" to "I'm still working on the last one", "hi" to "Pichhla wala abhi chal raha hai",
            "ta" to "Munnaadiyadhu innum nadakkuthu", "te" to "Modati pani inka jarugutundi",
            "bn" to "Aager ta ekhono cholche", "kn" to "Hinde kelasa innu naditide",
            "ml" to "Mumpathe pani innum nadakkunnu"
        ),
        "error" to mapOf(
            "en" to "Something went wrong with that one", "hi" to "Usme kuch gadbad ho gayi",
            "ta" to "Adhula edho prashna", "te" to "Andulo edho problem",
            "bn" to "Okhane kichu problem holo", "kn" to "Alli eno problem aaytu",
            "ml" to "Athil entho prashnam"
        ),
        "open_fail" to mapOf(
            "en" to "That didn't open. The app may not be installed.",
            "hi" to "Wo khul nahi raha. App install nahi hai shayad."
        ),
        "type_fail" to mapOf(
            "en" to "I couldn't type into that field",
            "hi" to "Us field mein type nahi kar paya"
        ),
        "search_box" to mapOf(
            "en" to "I couldn't find a search box on this screen",
            "hi" to "Screen pe search box nahi mila"
        ),
        "waiting" to mapOf(
            "en" to "I'm still waiting for %s to appear",
            "hi" to "%s ka wait kar raha hoon, abhi tak nahi aaya"
        ),
        "tap_fail" to mapOf(
            "en" to "I found %s but couldn't tap it",
            "hi" to "%s mil gaya par tap nahi ho paya"
        ),
        "dnd_access" to mapOf(
            "en" to "Do not disturb needs notification policy access. Please grant it in Settings.",
            "hi" to "Do not disturb ke liye notification policy access chahiye. Settings mein de do."
        ),
        "sms_fail" to mapOf(
            "en" to "I couldn't send that text",
            "hi" to "Wo message bhej nahi paya"
        ),
        "flashlight_fail" to mapOf(
            "en" to "I couldn't reach the flashlight",
            "hi" to "Flashlight tak nahi pahunch paya"
        ),
        "confirm_needed" to mapOf(
            "en" to "I need a confirmation for that, and I can't ask right now",
            "hi" to "Uske liye confirmation chahiye, aur abhi pooch nahi sakta"
        ),
        "commit_check" to mapOf(
            "en" to "Quick check: %s. Say looks good to continue, or cancel.",
            "hi" to "Ek check kar lo: %s. Theek lage to bolo theek hai, warna cancel.",
            "ta" to "Konjam paaru: %s. Sari nu sollunga, illa cancel.",
            "te" to "Konchem chudandi: %s. Sare ante cheppandi, leda cancel.",
            "bn" to "Ektu dekho: %s. Thik hole bolo, na hole cancel.",
            "kn" to "Swalpa nodi: %s. Sari andre heli, illa cancel.",
            "ml" to "Onnu nokku: %s. Sheri enkil parayu, allenkil cancel."
        ),
        "handover" to mapOf(
            "en" to "Done — finish the payment yourself. I've stopped here.",
            "hi" to "Ho gaya — payment aap khud kar lo. Main yahin ruk gaya.",
            "ta" to "Aachu — payment neenga pannunga. Naan nikkaren.",
            "te" to "Ayyipoyindi — payment meeru cheyandi. Nenu aagipoyanu.",
            "bn" to "Hoye geche — payment apni koro. Ami theme gesi.",
            "kn" to "Aaytu — payment neevu maadi. Naanu ninthide.",
            "ml" to "Aayi — payment nee cheyyu. Njan nirthi."
        ),
        "commit_unavailable" to mapOf(
            "en" to "I can't show the confirmation card, so I won't place the order.",
            "hi" to "Confirmation card nahi dikha sakta, isliye order nahi karunga."
        ),
        "dictation_started" to mapOf(
            "en" to "Dictating. Speak a sentence, or say stop typing when you're done.",
            "hi" to "Likh raha hoon. Sentence bolo, ya stop typing bolo.",
            "ta" to "Ezhuthuren. Sollunga, mudinjadhum stop typing sollunga.",
            "te" to "Rastunnaanu. Cheppandi, ayyaka stop typing cheppandi.",
            "bn" to "Likhchi. Bolo, sesh hole stop typing bolo.",
            "kn" to "Bariyuttiddene. Heli, aadmele stop typing heli.",
            "ml" to "Ezhuthunnu. Parayu, kazhinjal stop typing parayu."
        ),
        "dictation_stopped" to mapOf(
            "en" to "Stopped dictating", "hi" to "Likhna band kar diya",
            "ta" to "Ezhuthu niruthiten", "te" to "Rayatam aapesanu",
            "bn" to "Lekha bondho korlam", "kn" to "Bariyuvudu nilsidenu",
            "ml" to "Ezhuth nirthi"
        ),
        "dictation_timeout" to mapOf(
            "en" to "Stopped dictating after a long pause",
            "hi" to "Kaafi der kuch nahi bola, likhna band kar diya",
            "ta" to "Romba neram pesala, niruthiten",
            "te" to "Chala sepu matladaledu, aapesanu",
            "bn" to "Onek khon bolo ni, bondho korlam",
            "kn" to "Tumba hotu maatadilla, nilsidenu",
            "ml" to "Ere neram samsarichilla, nirthi"
        ),
        "dictation_failed" to mapOf(
            "en" to "I lost the text field, so I stopped dictating",
            "hi" to "Text field nahi mila, likhna band kar diya"
        ),
        "dictation_no_field" to mapOf(
            "en" to "Open a text field first, then start dictating",
            "hi" to "Pehle koi text field kholo, phir dictation shuru karo",
            "ta" to "Modhalla oru text field thora, appuram sollunga",
            "te" to "Modata text field teravandi, taruvata cheppandi",
            "bn" to "Age ekta text field kholo, tarpor bolo",
            "kn" to "Modalu text field tereyiri, aamele heli",
            "ml" to "Aadyam oru text field thurakku, pinne parayu"
        ),
        "not_found_offer" to mapOf(
            "en" to "I can't see %s. I can see %s — shall I tap that?",
            "hi" to "%s nahi dikh raha. %s dikh raha hai — wo dabaun?",
            "ta" to "%s theriyala. %s irukku — adha thadavalaama?",
            "te" to "%s kanipinchaledu. %s undi — adi nokkalana?",
            "bn" to "%s dekhchi na. %s dekhchi — ota chapabo?",
            "kn" to "%s kanstilla. %s ide — adanna ottalaa?",
            "ml" to "%s kaanunnilla. %s undu — athu amukkano?"
        ),
        "not_found_direction" to mapOf(
            "en" to "I can't see %s. %s",
            "hi" to "%s nahi dikh raha. %s",
            "ta" to "%s theriyala. %s",
            "te" to "%s kanipinchaledu. %s",
            "bn" to "%s dekhchi na. %s",
            "kn" to "%s kanstilla. %s",
            "ml" to "%s kaanunnilla. %s"
        ),
        "contacts_needed" to mapOf(
            "en" to "I need contacts access for that. Opening this app's settings — allow Contacts, then ask again.",
            "hi" to "Uske liye contacts access chahiye. App ki settings khol raha hoon — Contacts allow karke phir bolo."
        ),
        "screen_empty" to mapOf(
            "en" to "The screen looks empty to me", "hi" to "Screen khaali lag raha hai",
            "ta" to "Screen kaaliya irukku", "te" to "Screen khali ga undi",
            "bn" to "Screen khali mone hocche", "kn" to "Screen khali ide",
            "ml" to "Screen ozhinju irikkunnu"
        )
    )

    /** Pick the phrase for the language the user just spoke, falling back to Hinglish then English. */
    fun say(event: String, mix: LanguageDetector.LanguageMix?, vararg args: String): String {
        val row = table[event] ?: return event
        val lang = mix?.primary ?: "en"
        // A code-mixed utterance with an English head still reads best in Hinglish.
        val preferred = if (mix?.mixed == true && lang == "en") "hi" else lang
        val template: String = row[preferred] ?: row["hi"] ?: row["en"] ?: event
        return if (args.isEmpty()) template else runCatching { template.format(*args) }.getOrDefault(template)
    }
}
