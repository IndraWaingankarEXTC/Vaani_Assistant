package com.vaani.assistant.nlu

import com.vaani.assistant.core.Text

/**
 * Code-mixing, not language switching.
 *
 * Real Indian speech does not pick one language per sentence — it mixes inside the sentence:
 * "spotify kholo yaar, lofi bajao", "Obsidian में daily note खोलो", "swiggy la biryani order pannu".
 * So this is a token-level rewriter, not a language selector: every word, whatever its language or
 * script, is mapped to one canonical English token the rule grammar already understands. Nothing
 * downstream needs to know which language was spoken.
 *
 * Coverage: Hindi/Urdu, Marathi, Bengali, Tamil, Telugu, Kannada, Malayalam, Gujarati, Punjabi —
 * in Roman script and, for Devanagari, in native script (transliterated first).
 */
object CodeMixLexicon {

    // ------------------------------------------------------------------ script -> language

    private val scriptRanges = listOf(
        "hi" to ('\u0900'..'\u097F'),   // Devanagari: Hindi, Marathi
        "bn" to ('\u0980'..'\u09FF'),   // Bengali, Assamese
        "pa" to ('\u0A00'..'\u0A7F'),   // Gurmukhi
        "gu" to ('\u0A80'..'\u0AFF'),   // Gujarati
        "ta" to ('\u0B80'..'\u0BFF'),   // Tamil
        "te" to ('\u0C00'..'\u0C7F'),   // Telugu
        "kn" to ('\u0C80'..'\u0CFF'),   // Kannada
        "ml" to ('\u0D00'..'\u0D7F'),   // Malayalam
        "ur" to ('\u0600'..'\u06FF')    // Arabic script: Urdu
    )

    fun scriptsIn(input: String): Set<String> =
        scriptRanges.filter { (_, range) -> input.any { it in range } }.map { it.first }.toSet()

    // ------------------------------------------------------------------ Devanagari -> Latin

    private val devanagari = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o",
        'ा' to "a", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo", 'ृ' to "ri",
        'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o", 'ॅ' to "e",
        'ं' to "n", 'ँ' to "n", 'ः' to "h", '्' to "", '़' to "",
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh", 'ष' to "sh",
        'स' to "s", 'ह' to "h", 'ळ' to "l",
        '०' to "0", '१' to "1", '२' to "2", '३' to "3", '४' to "4",
        '५' to "5", '६' to "6", '७' to "7", '८' to "8", '९' to "9"
    )

    /**
     * Nukta letters (क़ ज़ ड़ फ़) are a base consonant plus a combining nukta, so they are two
     * code points and cannot live in the Char map above. They are replaced as sequences first.
     */
    private val nuktaSequences = listOf(
        "क़" to "q", "ख़" to "kh", "ग़" to "g", "ज़" to "z",
        "ड़" to "r", "ढ़" to "rh", "फ़" to "f", "य़" to "y"
    )

    /** Best-effort transliteration so a Devanagari app name still fuzzy-matches its Latin name. */
    fun transliterate(input: String): String {
        var source = input
        for ((sequence, latin) in nuktaSequences) source = source.replace(sequence, latin)
        val out = StringBuilder(source.length + 8)
        for (ch in source) {
            val mapped = devanagari[ch]
            if (mapped != null) out.append(mapped) else out.append(ch)
        }
        return out.toString()
    }

    // ------------------------------------------------------------------ phrases

    /** Multi-word forms, applied before single tokens, in every language we cover. */
    private val phrases: List<Triple<String, String, String>> = listOf(
        Triple("band kar do", "turn off", "hi"), Triple("band karo", "turn off", "hi"),
        Triple("band kar", "turn off", "hi"), Triple("बंद करो", "turn off", "hi"),
        Triple("बंद कर दो", "turn off", "hi"), Triple("off kar do", "turn off", "hi"),
        Triple("chalu kar do", "turn on", "hi"), Triple("chalu karo", "turn on", "hi"),
        Triple("चालू करो", "turn on", "hi"), Triple("on kar do", "turn on", "hi"),
        Triple("awaaz badhao", "volume up", "hi"), Triple("awaaz badha do", "volume up", "hi"),
        Triple("आवाज़ बढ़ाओ", "volume up", "hi"), Triple("awaaz kam karo", "volume down", "hi"),
        Triple("awaaz kam", "volume down", "hi"), Triple("आवाज़ कम करो", "volume down", "hi"),
        Triple("sound kootu", "volume up", "ta"), Triple("saththam kootu", "volume up", "ta"),
        Triple("call pannu", "call", "ta"), Triple("call chey", "call", "te"),
        Triple("call koro", "call", "bn"), Triple("call maadu", "call", "kn"),
        Triple("off pannu", "turn off", "ta"), Triple("off chey", "turn off", "te"),
        Triple("bondho koro", "turn off", "bn"), Triple("off maadu", "turn off", "kn"),
        Triple("on pannu", "turn on", "ta"), Triple("on chey", "turn on", "te"),
        Triple("chalu koro", "turn on", "bn"), Triple("on maadu", "turn on", "kn"),
        Triple("wake me up", "wake me up", "en"), Triple("utha do", "wake me up", "hi"),
        Triple("जगाओ", "wake me up", "hi"), Triple("उठाओ", "wake me up", "hi"),
        Triple("dhoondh do", "search", "hi"), Triple("ढूंढो", "search", "hi"),
        Triple("khoj do", "search", "hi"), Triple("thedu", "search", "ta"),
        Triple("vethuku", "search", "te"), Triple("huduku", "search", "kn"),
        Triple("thirakka", "open", "ta"), Triple("thurakku", "open", "ml"),
        Triple("bol ke likho", "dictate", "hi"), Triple("bolkar likho", "dictate", "hi"),
        Triple("likhna shuru karo", "dictate", "hi"), Triple("likhna band karo", "stop dictation", "hi"),
        Triple("टाइप करो", "type", "hi"), Triple("बोलकर लिखो", "dictate", "hi"),
        Triple("बोल के लिखो", "dictate", "hi"), Triple("baja do", "play", "hi"), Triple("khol do", "open", "hi"),
        Triple("chala do", "play", "hi"), Triple("set kar do", "set", "hi"),
        Triple("padh do", "read", "hi"), Triple("बजाओ", "play", "hi"), Triple("चलाओ", "play", "hi")
    )

    // ------------------------------------------------------------------ single tokens

    private data class Entry(val canonical: String, val language: String)

    private fun e(canonical: String, language: String) = Entry(canonical, language)

    /** surface form -> canonical token. Roman script, all languages, plus native scripts. */
    private val tokens: Map<String, Entry> = buildMap {
        fun add(canonical: String, language: String, vararg forms: String) {
            forms.forEach { put(it, e(canonical, language)) }
        }

        // open
        add("open", "hi", "kholo", "khol", "kholna", "kholiye", "kholo na", "kholo yaar")
        add("open", "mr", "ughado", "ughada", "khola")
        add("open", "bn", "khulo", "kholun")
        add("open", "ta", "thira", "thirandhu", "thirappu")
        add("open", "te", "theeyu", "theruvu", "theeyandi")
        add("open", "kn", "there", "thegi", "thereyu")
        add("open", "ml", "thura", "thurakku")
        add("open", "hi", "खोलो", "खोल", "खोलिए")

        // play / stream
        add("play", "hi", "bajao", "baja", "chalao", "chala", "sunao")
        add("play", "mr", "vajava", "lava", "chalu kara")
        add("play", "bn", "bajao", "cholao")
        add("play", "ta", "vaasi", "isai", "podhu")
        add("play", "te", "vaayinchu", "vinu")
        add("play", "kn", "vaayisu", "kelu")
        add("play", "ml", "vaayikku", "kelkku")
        add("play", "hi", "बजा", "सुनाओ")

        // send
        add("send", "hi", "bhejo", "bhej", "bhej do", "pathao", "patha do")
        add("send", "mr", "pathva", "pathva do")
        add("send", "bn", "pathao", "pathiye")
        add("send", "ta", "anuppu", "anu")
        add("send", "te", "pampu", "pampandi")
        add("send", "kn", "kaluhisu", "kalisu")
        add("send", "ml", "ayakku")
        add("send", "hi", "भेजो", "भेज", "भेज दो")

        // call
        add("call", "hi", "milao", "mila", "baat kara", "baat karo")
        add("call", "ta", "pesu", "pesu da")
        add("call", "te", "matladu", "phone chey")
        add("call", "bn", "phone koro", "kotha bolo")
        add("call", "kn", "maatu maadu", "phone maadu")
        add("call", "ml", "vilikku", "phone cheyyu")
        add("call", "hi", "फोन करो", "बात करो", "मिलाओ")

        // set / put
        add("set", "hi", "lagao", "laga", "laga do", "thoro")
        add("set", "ta", "vai", "vaithu", "vaithidu")
        add("set", "te", "pettu", "petu")
        add("set", "kn", "haaku", "haaki")
        add("set", "ml", "vaikku", "vekkuka")
        add("set", "hi", "लगाओ", "लगा दो", "सेट करो")

        // on / off (single word forms)
        add("off", "hi", "band", "बंद", "बन्द")
        add("on", "hi", "chalu", "चालू", "चालु")
        add("off", "ta", "anai", "anaithu")
        add("off", "te", "aapu", "aapesi")
        add("off", "bn", "bondho", "bondho koro")
        add("off", "kn", "aarisu", "nillisu")

        // stop / pause
        add("pause", "hi", "ruko", "ruk", "bas", "rok do", "thambao")
        add("pause", "ta", "niruthu", "nillu")
        add("pause", "te", "aagu", "nillu")
        add("pause", "bn", "thamo", "darao")
        add("pause", "kn", "nillu", "thade")
        add("pause", "ml", "nirthu", "nilku")
        add("pause", "hi", "रुको", "बस", "रोक दो")

        // search / find
        add("search", "hi", "dhoondo", "dhoondh", "khojo", "talash karo", "dhundo")
        add("search", "mr", "shodha", "shodh")
        add("search", "bn", "khojo", "khuje ber koro")
        add("search", "ta", "thedu", "thedi")
        add("search", "te", "vethuku", "vethiki")
        add("search", "kn", "huduku", "huduki")
        add("search", "ml", "thappu", "anweshi")
        add("search", "hi", "खोजो", "ढूंढ", "तलाश करो")

        // volume
        add("volume", "hi", "awaaz", "awaz", "आवाज़", "आवाज", "sound")
        add("volume", "ta", "saththam", "satham")
        add("volume", "te", "shabdam", "volume")
        add("up", "hi", "badhao", "badha", "बढ़ाओ", "बढ़ा")
        add("down", "hi", "kam", "kam karo", "कम", "कम करो")
        add("up", "ta", "kootu", "koothu")
        add("down", "ta", "kammi", "kurainthu")

        // read
        add("read", "hi", "padho", "padh", "पढ़ो", "पढ़")
        add("read", "ta", "padi", "vaasi")
        add("read", "te", "chaduvu", "vinu")
        add("read", "bn", "podo", "poro")

        // time words
        add("baje", "hi", "baje", "bje", "बजे")
        add("baje", "ta", "mani")
        add("baje", "te", "gante", "gantalaki")
        add("baje", "kn", "gante", "gantegalu")
        add("baje", "ml", "mani", "manikku")
        add("baje", "bn", "tay", "টায়")
        add("baje", "pa", "vaje", "वजे")
        add("alarm", "hi", "अलार्म", "alarrm")
        add("timer", "hi", "टाइमर", "taimer")

        // screen
        add("screen", "hi", "स्क्रीन", "skrin")
        add("what", "hi", "kya", "क्या")
        add("what", "ta", "enna", "என்ன")
        add("what", "te", "enti", "ఏంటి")
        add("what", "bn", "ki", "কি")
        add("what", "kn", "enu", "ಏನು")
        add("what", "ml", "entha", "എന്ത്")

        // connectors and politeness (dropped)
        add("", "hi", "aur", "और")
        add("and", "hi", "aur", "और")
        add("then", "hi", "phir", "fir", "फिर")
        add("", "hi", "yaar", "bhai", "bro", "dost", "ji", "zara", "thoda", "abhi", "na")
        add("", "ta", "da", "la", "machan", "thala", "ippo", "konjam")
        add("", "te", "ra", "andi", "garu", "ippudu", "konchem")
        add("", "bn", "da", "dada", "ekhon", "ektu")
        add("", "kn", "ra", "anna", "iga", "swalpa")
        add("", "ml", "eda", "chetta", "ippol")
        add("", "hi", "kripya", "please", "pls", "plz", "zara", "सुनो", "suno", "सुनिए")
        add("", "hi", "hai", "hain", "ho", "है", "हैं")
        add("", "hi", "ko", "ka", "ki", "ke", "mein", "me", "se", "par", "को", "का", "की", "के", "में", "से")
        add("", "hi", "kar", "karo", "karna", "karke", "करो", "कर", "करना")
        add("", "hi", "dena")
        add("", "hi", "mujhe", "mera", "meri", "main", "hum", "aap", "tum", "मुझे", "मेरा", "मैं")
        add("", "ta", "pannu", "pannunga", "chey", "sey")
        add("", "te", "chey", "cheyandi", "kavali")
        add("", "bn", "koro", "kore", "din")
        add("", "kn", "maadu", "maadi", "beku")
    }

    // ------------------------------------------------------------------ numbers

    private val numbers: Map<String, Int> = mapOf(
        // Hindi / Urdu
        "ek" to 1, "do" to 2, "teen" to 3, "char" to 4, "paanch" to 5, "chhe" to 6,
        "saat" to 7, "aath" to 8, "nau" to 9, "das" to 10, "gyarah" to 11, "barah" to 12,
        "एक" to 1, "दो" to 2, "तीन" to 3, "चार" to 4, "पांच" to 5, "छह" to 6, "सात" to 7,
        "आठ" to 8, "नौ" to 9, "दस" to 10,
        // Tamil
        "onnu" to 1, "rendu" to 2, "moonu" to 3, "naalu" to 4, "anju" to 5, "aaru" to 6,
        "ezhu" to 7, "ettu" to 8, "ombodhu" to 9, "pathu" to 10,
        // Telugu
        "okati" to 1, "rendu" to 2, "moodu" to 3, "naalugu" to 4, "aidu" to 5, "aaru" to 6,
        "edu" to 7, "enimidi" to 8, "tommidi" to 9, "padi" to 10,
        // Bengali
        "ek" to 1, "dui" to 2, "tin" to 3, "char" to 4, "panch" to 5, "choy" to 6,
        "shat" to 7, "aat" to 8, "noy" to 9, "dosh" to 10,
        // Kannada / Malayalam
        "ondu" to 1, "eradu" to 2, "mooru" to 3, "nalku" to 4, "aidu" to 5, "aaru" to 6,
        "elu" to 7, "entu" to 8, "ombattu" to 9, "hattu" to 10,
        "onnu" to 1, "randu" to 2, "moonnu" to 3, "naalu" to 4, "anchu" to 5
    )

    fun numberOf(token: String): Int? = numbers[token.lowercase()]

    // ------------------------------------------------------------------ rewrite

    /**
     * Native-script verbs for the non-Devanagari languages. These are matched against the raw
     * utterance, because normalising first would strip the vowel signs that carry the meaning.
     */
    private val nativePhrases: List<Triple<String, String, String>> = listOf(
        // Devanagari: matched on the raw string. The transliterator is a fallback for unknown
        // words (app names); command words are matched directly so matras and nuktas stay intact.
        Triple("आवाज़ बढ़ाओ", "volume up", "hi"), Triple("आवाज बढ़ाओ", "volume up", "hi"),
        Triple("आवाज़ बढ़ा दो", "volume up", "hi"), Triple("आवाज़ कम करो", "volume down", "hi"),
        Triple("आवाज कम करो", "volume down", "hi"), Triple("आवाज़ कम", "volume down", "hi"),
        Triple("बंद कर दो", "turn off", "hi"), Triple("बंद करो", "turn off", "hi"),
        Triple("चालू कर दो", "turn on", "hi"), Triple("चालू करो", "turn on", "hi"),
        Triple("ऑफ करो", "turn off", "hi"), Triple("ऑन करो", "turn on", "hi"),
        Triple("फोन करो", "call", "hi"), Triple("कॉल करो", "call", "hi"),
        Triple("बात करो", "call", "hi"), Triple("फ़ोन करो", "call", "hi"),
        Triple("भेज दो", "send", "hi"), Triple("भेजो", "send", "hi"), Triple("भेज", "send", "hi"),
        Triple("सर्च करो", "search", "hi"), Triple("ढूंढो", "search", "hi"),
        Triple("खोजो", "search", "hi"), Triple("ढूँढो", "search", "hi"),
        Triple("सेट करो", "set", "hi"), Triple("लगा दो", "set", "hi"), Triple("लगाओ", "set", "hi"),
        Triple("बजा दो", "play", "hi"), Triple("बजाओ", "play", "hi"), Triple("चलाओ", "play", "hi"),
        Triple("सुना दो", "play", "hi"), Triple("गाना", "", "hi"), Triple("संगीत", "", "hi"),
        Triple("खोल दो", "open", "hi"), Triple("खोलो", "open", "hi"), Triple("खोल", "open", "hi"),
        Triple("खोलिए", "open", "hi"),
        Triple("रोक दो", "pause", "hi"), Triple("रोको", "pause", "hi"), Triple("रुको", "pause", "hi"),
        Triple("जगाओ", "wake me up", "hi"), Triple("उठाओ", "wake me up", "hi"),
        Triple("पढ़कर सुनाओ", "read", "hi"), Triple("पढ़ो", "read", "hi"),
        Triple("अलार्म", "alarm", "hi"), Triple("टाइमर", "timer", "hi"),
        Triple("स्क्रीन", "screen", "hi"), Triple("वॉल्यूम", "volume", "hi"),
        Triple("बढ़ाओ", "up", "hi"), Triple("बढ़ा", "up", "hi"), Triple("कम", "down", "hi"),
        Triple("क्या", "what", "hi"), Triple("बजे", "baje", "hi"),
        // Devanagari particles and politeness: dropped
        Triple("कर दो", "", "hi"), Triple("करो", "", "hi"), Triple("कर", "", "hi"),
        Triple("दीजिए", "", "hi"), Triple("देना", "", "hi"),
        Triple("और", "and", "hi"), Triple("फिर", "then", "hi"),
        Triple("में", "", "hi"), Triple("पर", "", "hi"), Triple("पे", "", "hi"),
        Triple("को", "", "hi"), Triple("का", "", "hi"), Triple("की", "", "hi"), Triple("के", "", "hi"),
        Triple("से", "", "hi"), Triple("है", "", "hi"), Triple("हैं", "", "hi"),
        Triple("ज़रा", "", "hi"), Triple("थोड़ा", "", "hi"), Triple("यार", "", "hi"),
        Triple("भाई", "", "hi"), Triple("कृपया", "", "hi"), Triple("सुनो", "", "hi"),
        Triple("सुनिए", "", "hi"), Triple("मुझे", "", "hi"), Triple("मेरा", "", "hi"),
        Triple("मेरी", "", "hi"), Triple("मैं", "", "hi"), Triple("हम", "", "hi"),
        // Tamil
        Triple("திற", "open", "ta"), Triple("இயக்கு", "play", "ta"),
        Triple("அனுப்பு", "send", "ta"), Triple("அழை", "call", "ta"),
        Triple("தேடு", "search", "ta"), Triple("நிறுத்து", "pause", "ta"),
        Triple("அணை", "off", "ta"), Triple("படி", "read", "ta"),
        // Telugu
        Triple("తెరువు", "open", "te"), Triple("వాయించు", "play", "te"),
        Triple("పంపు", "send", "te"), Triple("కాల్ చేయి", "call", "te"),
        Triple("వెతుకు", "search", "te"), Triple("ఆపు", "pause", "te"),
        // Bengali
        Triple("খোলো", "open", "bn"), Triple("বাজাও", "play", "bn"),
        Triple("পাঠাও", "send", "bn"), Triple("কল করো", "call", "bn"),
        Triple("খোঁজো", "search", "bn"), Triple("বন্ধ", "off", "bn"),
        Triple("চালু", "on", "bn"), Triple("থামো", "pause", "bn"),
        // Kannada
        Triple("ತೆರೆ", "open", "kn"), Triple("ಪ್ಲೇ ಮಾಡು", "play", "kn"),
        Triple("ಕಳುಹಿಸು", "send", "kn"), Triple("ಕರೆ ಮಾಡು", "call", "kn"),
        Triple("ಹುಡುಕು", "search", "kn"), Triple("ನಿಲ್ಲಿಸು", "pause", "kn"),
        // Malayalam
        Triple("തുറക്കുക", "open", "ml"), Triple("പ്ലേ ചെയ്യുക", "play", "ml"),
        Triple("അയക്കുക", "send", "ml"), Triple("വിളിക്കുക", "call", "ml"),
        Triple("തിരയുക", "search", "ml"), Triple("നിർത്തുക", "pause", "ml"),
        // Gujarati
        Triple("ખોલો", "open", "gu"), Triple("વગાડો", "play", "gu"),
        Triple("મોકલો", "send", "gu"), Triple("ફોન કરો", "call", "gu"),
        Triple("શોધો", "search", "gu"), Triple("બંધ", "off", "gu"),
        // Punjabi
        Triple("ਖੋਲ੍ਹੋ", "open", "pa"), Triple("ਚਲਾਓ", "play", "pa"),
        Triple("ਭੇਜੋ", "send", "pa"), Triple("ਫੋਨ ਕਰੋ", "call", "pa"),
        Triple("ਲੱਭੋ", "search", "pa"), Triple("ਬੰਦ", "off", "pa")
    )

    private val SPACES = Regex("\\s+")

    /**
     * Rewrite any mix of languages and scripts into canonical tokens.
     * Native script is transliterated first so one token table serves both.
     */
    fun rewrite(input: String): String {
        // 1. Native-script verbs first, on the untouched string.
        var raw = input.lowercase()
        for ((phrase, canonical, _) in nativePhrases) {
            if (phrase.isNotEmpty() && raw.contains(phrase)) raw = raw.replace(phrase, canonical)
        }
        // 2. Transliterate, then normalise: Text.norm() strips combining marks, which in
        //    Devanagari are the matras — normalising first would turn "खोलो" into "खल".
        var s = Text.norm(if (scriptsIn(raw).isNotEmpty()) transliterate(raw) else raw)

        for ((phrase, canonical, _) in phrases) {
            val p = Text.norm(phrase)
            if (p.isNotEmpty() && s.contains(p)) s = s.replace(p, canonical)
        }

        val out = StringBuilder(s.length)
        for (token in s.split(' ')) {
            if (token.isBlank()) continue
            if (token.none { it.code < 128 }) continue   // leftover unhandled script fragment
            val hit = tokens[token]
            when {
                hit == null -> out.append(token).append(' ')
                hit.canonical.isEmpty() -> Unit            // filler: drop it
                else -> out.append(hit.canonical).append(' ')
            }
        }
        return SPACES.replace(out.toString(), " ").trim()
    }

    /** Which languages did this utterance actually touch? */
    fun languageHits(input: String): Map<String, Int> {
        val counts = HashMap<String, Int>()
        val norm = Text.norm(if (scriptsIn(input).isNotEmpty()) transliterate(input) else input)
        for (script in scriptsIn(input)) counts.merge(script, 2, Int::plus)
        for (token in norm.split(' ')) {
            tokens[token]?.let { if (it.language.isNotEmpty()) counts.merge(it.language, 1, Int::plus) }
            if (numbers.containsKey(token)) counts.merge("num", 1, Int::plus)
        }
        if (Regex("[a-z]").containsMatchIn(norm)) counts.merge("en", 1, Int::plus)
        return counts
    }
}
