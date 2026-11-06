package com.alan.ripdpi.model

import java.util.Locale

data class CountryThemeSpec(
    val code: String,
    val nameFa: String,
    val nameEn: String,
    val flagEmoji: String,
    val stripeColors: List<Long>,
    val accentPrimary: Long,
    val accentSecondary: Long
)

object CountryPaletteRegistry {

    private data class RawCountryEntry(
        val code: String,
        val nameFa: String,
        val nameEn: String,
        val stripes: List<Long>,
        val accentPrimary: Long,
        val accentSecondary: Long,
        val keywords: List<String>
    )

    private val ENTRIES: List<RawCountryEntry> = listOf(
        RawCountryEntry("UA", "اوکراین", "Ukraine", listOf(0xFF0057B7L, 0xFFFFDD00L, 0xFF0057B7L), 0xFF38BDF8L, 0xFFFACC15L, listOf("ukraine", "kyiv")),
        RawCountryEntry("IQ", "عراق", "Iraq", listOf(0xFFCE1126L, 0xFFF8FAFCL, 0xFF141414L), 0xFFEF4444L, 0xFF10B981L, listOf("iraq", "baghdad")),
        RawCountryEntry("SA", "عربستان", "Saudi Arabia", listOf(0xFF006C35L, 0xFFF8FAFCL, 0xFF006C35L), 0xFF10B981L, 0xFFFACC15L, listOf("saudi", "riyadh")),
        RawCountryEntry("ID", "اندونزی", "Indonesia", listOf(0xFFCE1126L, 0xFFF8FAFCL, 0xFFCE1126L), 0xFFEF4444L, 0xFFF8FAFCL, listOf("indonesia", "jakarta")),
        RawCountryEntry("RS", "صربستان", "Serbia", listOf(0xFFC6363CL, 0xFF0C4076L, 0xFFF8FAFCL), 0xFF3B82F6L, 0xFFEF4444L, listOf("serbia", "belgrade")),
        RawCountryEntry("CY", "قبرس", "Cyprus", listOf(0xFFF8FAFCL, 0xFFD57800L, 0xFF4E5B31L), 0xFFF59E0BL, 0xFF10B981L, listOf("cyprus", "limassol")),
        RawCountryEntry("SI", "اسلوونی", "Slovenia", listOf(0xFFF8FAFCL, 0xFF005DA4L, 0xFFED1C24L), 0xFF38BDF8L, 0xFFEF4444L, listOf("slovenia", "ljubljana")),
        RawCountryEntry("SK", "اسلواکی", "Slovakia", listOf(0xFFF8FAFCL, 0xFF0B4EA2L, 0xFFEE1C25L), 0xFF38BDF8L, 0xFFEF4444L, listOf("slovakia", "bratislava")),
        RawCountryEntry("AT", "اتریش", "Austria", listOf(0xFFED2939L, 0xFFF8FAFCL, 0xFFED2939L), 0xFFEF4444L, 0xFFF8FAFCL, listOf("austria", "vienna")),
        RawCountryEntry("PL", "لهستان", "Poland", listOf(0xFFF8FAFCL, 0xFFDC143CL, 0xFFDC143CL), 0xFFEF4444L, 0xFFF8FAFCL, listOf("poland", "warsaw")),
        RawCountryEntry("AL", "آلبانی", "Albania", listOf(0xFFE41E20L, 0xFF181818L, 0xFFE41E20L), 0xFFEF4444L, 0xFFF5C451L, listOf("albania", "tirana")),
        RawCountryEntry("GR", "یونان", "Greece", listOf(0xFF0D5EAFL, 0xFFF8FAFCL, 0xFF0D5EAFL), 0xFF38BDF8L, 0xFFF8FAFCL, listOf("greece", "athens")),
        RawCountryEntry("PT", "پرتغال", "Portugal", listOf(0xFF006600L, 0xFFFF0000L, 0xFFFFCC00L), 0xFF10B981L, 0xFFEF4444L, listOf("portugal", "lisbon")),
        RawCountryEntry("HR", "کرواسی", "Croatia", listOf(0xFFFF0000L, 0xFFF8FAFCL, 0xFF171796L), 0xFFEF4444L, 0xFF3B82F6L, listOf("croatia", "zagreb")),
        RawCountryEntry("MD", "مولداوی", "Moldova", listOf(0xFF003DA5L, 0xFFFFD100L, 0xFFC8102EL), 0xFFFACC15L, 0xFF3B82F6L, listOf("moldova")),
        RawCountryEntry("KZ", "قزاقستان", "Kazakhstan", listOf(0xFF00AFCAL, 0xFFFEC50CL, 0xFF00AFCAL), 0xFF38BDF8L, 0xFFFACC15L, listOf("kazakhstan", "almaty")),
        RawCountryEntry("AZ", "آذربایجان", "Azerbaijan", listOf(0xFF0092BCL, 0xFFE4002BL, 0xFF00AF66L), 0xFF38BDF8L, 0xFF10B981L, listOf("azerbaijan", "baku")),
        RawCountryEntry("GE", "گرجستان", "Georgia", listOf(0xFFF8FAFCL, 0xFFFF0000L, 0xFFF8FAFCL), 0xFFEF4444L, 0xFFF8FAFCL, listOf("georgia", "tbilisi")),
        RawCountryEntry("IN", "هند", "India", listOf(0xFFFF9933L, 0xFFF8FAFCL, 0xFF138808L), 0xFFFB923CL, 0xFF10B981L, listOf("india", "mumbai")),
        RawCountryEntry("KR", "کره جنوبی", "South Korea", listOf(0xFFF8FAFCL, 0xFFCD2E3AL, 0xFF0047A0L), 0xFF38BDF8L, 0xFFEF4444L, listOf("korea", "seoul")),
        RawCountryEntry("BR", "برزیل", "Brazil", listOf(0xFF009C3BL, 0xFFFFDF00L, 0xFF002776L), 0xFF10B981L, 0xFFFACC15L, listOf("brazil", "sao paulo")),
        RawCountryEntry("SC", "سیشل", "Seychelles", listOf(0xFF003F87L, 0xFFFCD856L, 0xFF007A3DL), 0xFFFACC15L, 0xFF10B981L, listOf("seychelles")),
        RawCountryEntry("CW", "کوراسائو", "Curacao", listOf(0xFF002B7FL, 0xFFF9E814L, 0xFF002B7FL), 0xFF38BDF8L, 0xFFFACC15L, listOf("curacao")),
        RawCountryEntry("BZ", "بلیز", "Belize", listOf(0xFF003F87L, 0xFFCE1126L, 0xFF003F87L), 0xFF38BDF8L, 0xFFEF4444L, listOf("belize")),
        RawCountryEntry("UA", "اوکراین", "Ukraine", listOf(0xFF0057B7L, 0xFFFFDD00L, 0xFF0057B7L), 0xFF38BDF8L, 0xFFFACC15L, listOf("ukraine", "kyiv")),
        RawCountryEntry("IQ", "عراق", "Iraq", listOf(0xFFCE1126L, 0xFFF8FAFCL, 0xFF141414L), 0xFFEF4444L, 0xFF10B981L, listOf("iraq", "baghdad")),
        RawCountryEntry("SA", "عربستان", "Saudi Arabia", listOf(0xFF006C35L, 0xFFF8FAFCL, 0xFF006C35L), 0xFF10B981L, 0xFFFACC15L, listOf("saudi", "riyadh")),
        RawCountryEntry("ID", "اندونزی", "Indonesia", listOf(0xFFCE1126L, 0xFFF8FAFCL, 0xFFCE1126L), 0xFFEF4444L, 0xFFF8FAFCL, listOf("indonesia", "jakarta")),
        RawCountryEntry("RS", "صربستان", "Serbia", listOf(0xFFC6363CL, 0xFF0C4076L, 0xFFF8FAFCL), 0xFF3B82F6L, 0xFFEF4444L, listOf("serbia", "belgrade")),
        RawCountryEntry("CY", "قبرس", "Cyprus", listOf(0xFFF8FAFCL, 0xFFD57800L, 0xFF4E5B31L), 0xFFF59E0BL, 0xFF10B981L, listOf("cyprus", "limassol")),
        RawCountryEntry("SI", "اسلوونی", "Slovenia", listOf(0xFFF8FAFCL, 0xFF005DA4L, 0xFFED1C24L), 0xFF38BDF8L, 0xFFEF4444L, listOf("slovenia", "ljubljana")),
        RawCountryEntry("SK", "اسلواکی", "Slovakia", listOf(0xFFF8FAFCL, 0xFF0B4EA2L, 0xFFEE1C25L), 0xFF38BDF8L, 0xFFEF4444L, listOf("slovakia", "bratislava")),
        RawCountryEntry("AT", "اتریش", "Austria", listOf(0xFFED2939L, 0xFFF8FAFCL, 0xFFED2939L), 0xFFEF4444L, 0xFFF8FAFCL, listOf("austria", "vienna")),
        RawCountryEntry("PL", "لهستان", "Poland", listOf(0xFFF8FAFCL, 0xFFDC143CL, 0xFFDC143CL), 0xFFEF4444L, 0xFFF8FAFCL, listOf("poland", "warsaw")),
        RawCountryEntry("AL", "آلبانی", "Albania", listOf(0xFFE41E20L, 0xFF181818L, 0xFFE41E20L), 0xFFEF4444L, 0xFFF5C451L, listOf("albania", "tirana")),
        RawCountryEntry("GR", "یونان", "Greece", listOf(0xFF0D5EAFL, 0xFFF8FAFCL, 0xFF0D5EAFL), 0xFF38BDF8L, 0xFFF8FAFCL, listOf("greece", "athens")),
        RawCountryEntry("PT", "پرتغال", "Portugal", listOf(0xFF006600L, 0xFFFF0000L, 0xFFFFCC00L), 0xFF10B981L, 0xFFEF4444L, listOf("portugal", "lisbon")),
        RawCountryEntry("HR", "کرواسی", "Croatia", listOf(0xFFFF0000L, 0xFFF8FAFCL, 0xFF171796L), 0xFFEF4444L, 0xFF3B82F6L, listOf("croatia", "zagreb")),
        RawCountryEntry("MD", "مولداوی", "Moldova", listOf(0xFF003DA5L, 0xFFFFD100L, 0xFFC8102EL), 0xFFFACC15L, 0xFF3B82F6L, listOf("moldova")),
        RawCountryEntry("KZ", "قزاقستان", "Kazakhstan", listOf(0xFF00AFCAL, 0xFFFEC50CL, 0xFF00AFCAL), 0xFF38BDF8L, 0xFFFACC15L, listOf("kazakhstan", "almaty")),
        RawCountryEntry("AZ", "آذربایجان", "Azerbaijan", listOf(0xFF0092BCL, 0xFFE4002BL, 0xFF00AF66L), 0xFF38BDF8L, 0xFF10B981L, listOf("azerbaijan", "baku")),
        RawCountryEntry("GE", "گرجستان", "Georgia", listOf(0xFFF8FAFCL, 0xFFFF0000L, 0xFFF8FAFCL), 0xFFEF4444L, 0xFFF8FAFCL, listOf("georgia", "tbilisi")),
        RawCountryEntry("IN", "هند", "India", listOf(0xFFFF9933L, 0xFFF8FAFCL, 0xFF138808L), 0xFFFB923CL, 0xFF10B981L, listOf("india", "mumbai")),
        RawCountryEntry("KR", "کره جنوبی", "South Korea", listOf(0xFFF8FAFCL, 0xFFCD2E3AL, 0xFF0047A0L), 0xFF38BDF8L, 0xFFEF4444L, listOf("korea", "seoul")),
        RawCountryEntry("BR", "برزیل", "Brazil", listOf(0xFF009C3BL, 0xFFFFDF00L, 0xFF002776L), 0xFF10B981L, 0xFFFACC15L, listOf("brazil", "sao paulo")),
        RawCountryEntry("SC", "سیشل", "Seychelles", listOf(0xFF003F87L, 0xFFFCD856L, 0xFF007A3DL), 0xFFFACC15L, 0xFF10B981L, listOf("seychelles")),
        RawCountryEntry("CW", "کوراسائو", "Curacao", listOf(0xFF002B7FL, 0xFFF9E814L, 0xFF002B7FL), 0xFF38BDF8L, 0xFFFACC15L, listOf("curacao")),
        RawCountryEntry("BZ", "بلیز", "Belize", listOf(0xFF003F87L, 0xFFCE1126L, 0xFF003F87L), 0xFF38BDF8L, 0xFFEF4444L, listOf("belize")),
        RawCountryEntry(
            code = "CZ",
            nameFa = "جمهوری چک",
            nameEn = "Czechia",
            stripes = listOf(0xFFF8FAFCL, 0xFFD7141AL, 0xFF11457EL),
            accentPrimary = 0xFF38BDF8L,
            accentSecondary = 0xFFEF4444L,
            keywords = listOf("czech", "prague")
        ),
        RawCountryEntry(
            code = "RO",
            nameFa = "رومانی",
            nameEn = "Romania",
            stripes = listOf(0xFF002B7FL, 0xFFFCD116L, 0xFFCE1126L),
            accentPrimary = 0xFFFACC15L,
            accentSecondary = 0xFF3B82F6L,
            keywords = listOf("romania", "bucharest")
        ),
        RawCountryEntry(
            code = "BG",
            nameFa = "بلغارستان",
            nameEn = "Bulgaria",
            stripes = listOf(0xFFF8FAFCL, 0xFF00966EL, 0xFFD62612L),
            accentPrimary = 0xFF10B981L,
            accentSecondary = 0xFFEF4444L,
            keywords = listOf("bulgaria", "sofia")
        ),
        RawCountryEntry(
            code = "HU",
            nameFa = "مجارستان",
            nameEn = "Hungary",
            stripes = listOf(0xFFCE2939L, 0xFFF8FAFCL, 0xFF477050L),
            accentPrimary = 0xFF10B981L,
            accentSecondary = 0xFFEF4444L,
            keywords = listOf("hungary", "budapest")
        ),
        RawCountryEntry(
            code = "IE",
            nameFa = "ایرلند",
            nameEn = "Ireland",
            stripes = listOf(0xFF169B62L, 0xFFF8FAFCL, 0xFFFF883EL),
            accentPrimary = 0xFF10B981L,
            accentSecondary = 0xFFFB923CL,
            keywords = listOf("ireland", "dublin")
        ),
        RawCountryEntry(
            code = "BE",
            nameFa = "بلژیک",
            nameEn = "Belgium",
            stripes = listOf(0xFF1C1C1CL, 0xFFFDDA24L, 0xFFEF3340L),
            accentPrimary = 0xFFFACC15L,
            accentSecondary = 0xFFEF4444L,
            keywords = listOf("belgium", "brussels")
        ),
        RawCountryEntry(
            code = "NO",
            nameFa = "نروژ",
            nameEn = "Norway",
            stripes = listOf(0xFFBA0C2FL, 0xFFF8FAFCL, 0xFF00205BL),
            accentPrimary = 0xFFEF4444L,
            accentSecondary = 0xFF3B82F6L,
            keywords = listOf("norway", "oslo")
        ),
        RawCountryEntry(
            code = "DK",
            nameFa = "دانمارک",
            nameEn = "Denmark",
            stripes = listOf(0xFFC8102EL, 0xFFF8FAFCL, 0xFFC8102EL),
            accentPrimary = 0xFFEF4444L,
            accentSecondary = 0xFFF8FAFCL,
            keywords = listOf("denmark", "copenhagen")
        ),
        RawCountryEntry(
            code = "EE",
            nameFa = "استونی",
            nameEn = "Estonia",
            stripes = listOf(0xFF0072CEL, 0xFF1C1C1CL, 0xFFF8FAFCL),
            accentPrimary = 0xFF38BDF8L,
            accentSecondary = 0xFFE2E8F0L,
            keywords = listOf("estonia", "tallinn")
        ),
        RawCountryEntry(
            code = "LV",
            nameFa = "لتونی",
            nameEn = "Latvia",
            stripes = listOf(0xFF9E3039L, 0xFFF8FAFCL, 0xFF9E3039L),
            accentPrimary = 0xFFF43F5EL,
            accentSecondary = 0xFFF8FAFCL,
            keywords = listOf("latvia", "riga")
        ),
        RawCountryEntry(
            code = "LT",
            nameFa = "لیتوانی",
            nameEn = "Lithuania",
            stripes = listOf(0xFFFDB913L, 0xFF006A44L, 0xFFC1272DL),
            accentPrimary = 0xFFFACC15L,
            accentSecondary = 0xFF10B981L,
            keywords = listOf("lithuania", "vilnius")
        ),
        RawCountryEntry(
            code = "HK",
            nameFa = "هنگ‌کنگ",
            nameEn = "Hong Kong",
            stripes = listOf(0xFFDE2910L, 0xFFF8FAFCL, 0xFFDE2910L),
            accentPrimary = 0xFFF43F5EL,
            accentSecondary = 0xFFF8FAFCL,
            keywords = listOf("hong kong", "hk")
        ),
        RawCountryEntry(
            code = "AU",
            nameFa = "استرالیا",
            nameEn = "Australia",
            stripes = listOf(0xFF00008BL, 0xFFF8FAFCL, 0xFFFF0000L),
            accentPrimary = 0xFF38BDF8L,
            accentSecondary = 0xFFEF4444L,
            keywords = listOf("australia", "sydney")
        ),
        RawCountryEntry(
            code = "AM",
            nameFa = "ارمنستان",
            nameEn = "Armenia",
            stripes = listOf(0xFFD90012L, 0xFF0033A0L, 0xFFF2A800L),
            accentPrimary = 0xFFFACC15L,
            accentSecondary = 0xFFEF4444L,
            keywords = listOf("armenia", "yerevan")
        ),
        RawCountryEntry(
            code = "DE",
            nameFa = "آلمان",
            nameEn = "Germany",
            stripes = listOf(0xFF1C1C1CL, 0xFFDD1C1CL, 0xFFFFCE00L),
            accentPrimary = 0xFFFFCE00L,
            accentSecondary = 0xFFEF4444L,
            keywords = listOf("germany", "frankfurt", "falkenstein", "nuremberg", "hetzner", "munich", "berlin", "آلمان")
        ),
        RawCountryEntry(
            code = "NL",
            nameFa = "هلند",
            nameEn = "Netherlands",
            stripes = listOf(0xFFAE1C28L, 0xFFF8FAFCL, 0xFF21468BL),
            accentPrimary = 0xFF38BDF8L,
            accentSecondary = 0xFFF43F5EL,
            keywords = listOf("netherlands", "holland", "amsterdam", "rotterdam", "meppel", "هلند")
        ),
        RawCountryEntry(
            code = "FI",
            nameFa = "فنلاند",
            nameEn = "Finland",
            stripes = listOf(0xFFF8FAFCL, 0xFF003580L, 0xFFF8FAFCL),
            accentPrimary = 0xFF60A5FAL,
            accentSecondary = 0xFFE2E8F0L,
            keywords = listOf("finland", "helsinki", "tuusula", "فنلاند")
        ),
        RawCountryEntry(
            code = "US",
            nameFa = "آمریکا",
            nameEn = "United States",
            stripes = listOf(0xFF1E3A8AL, 0xFFDC2626L, 0xFFF8FAFCL),
            accentPrimary = 0xFF3B82F6L,
            accentSecondary = 0xFFEF4444L,
            keywords = listOf("united states", "usa", "america", "dallas", "ashburn", "new york", "los angeles", "seattle", "chicago", "miami", "san jose", "آمریکا")
        ),
        RawCountryEntry(
            code = "GB",
            nameFa = "انگلیس",
            nameEn = "United Kingdom",
            stripes = listOf(0xFF012169L, 0xFFC8102EL, 0xFFF8FAFCL),
            accentPrimary = 0xFF60A5FAL,
            accentSecondary = 0xFFF43F5EL,
            keywords = listOf("united kingdom", "britain", "england", "london", "manchester", "انگلیس", "بریتانیا")
        ),
        RawCountryEntry(
            code = "FR",
            nameFa = "فرانسه",
            nameEn = "France",
            stripes = listOf(0xFF0055A4L, 0xFFF8FAFCL, 0xFFEF4135L),
            accentPrimary = 0xFF3B82F6L,
            accentSecondary = 0xFFEF4444L,
            keywords = listOf("france", "paris", "marseille", "strasbourg", "gravelines", "roubaix", "ovh", "فرانسه")
        ),
        RawCountryEntry(
            code = "TR",
            nameFa = "ترکیه",
            nameEn = "Turkey",
            stripes = listOf(0xFFE30A17L, 0xFFF8FAFCL, 0xFFE30A17L),
            accentPrimary = 0xFFEF4444L,
            accentSecondary = 0xFFF8FAFCL,
            keywords = listOf("turkey", "turkiye", "istanbul", "ankara", "izmir", "bursa", "ترکیه", "استانبول")
        ),
        RawCountryEntry(
            code = "AE",
            nameFa = "امارات",
            nameEn = "UAE",
            stripes = listOf(0xFF00732FL, 0xFFF8FAFCL, 0xFFFF0000L),
            accentPrimary = 0xFF10B981L,
            accentSecondary = 0xFFEF4444L,
            keywords = listOf("uae", "emirates", "dubai", "abu dhabi", "fujairah", "امارات", "دبی")
        ),
        RawCountryEntry(
            code = "SG",
            nameFa = "سنگاپور",
            nameEn = "Singapore",
            stripes = listOf(0xFFEF3340L, 0xFFF8FAFCL, 0xFFEF3340L),
            accentPrimary = 0xFFF43F5EL,
            accentSecondary = 0xFFF8FAFCL,
            keywords = listOf("singapore", "سنگاپور")
        ),
        RawCountryEntry(
            code = "SE",
            nameFa = "سوئد",
            nameEn = "Sweden",
            stripes = listOf(0xFF006AA7L, 0xFFFECC02L, 0xFF006AA7L),
            accentPrimary = 0xFFFACC15L,
            accentSecondary = 0xFF38BDF8L,
            keywords = listOf("sweden", "stockholm", "سوئد")
        ),
        RawCountryEntry(
            code = "CH",
            nameFa = "سوئیس",
            nameEn = "Switzerland",
            stripes = listOf(0xFFDA291CL, 0xFFF8FAFCL, 0xFFDA291CL),
            accentPrimary = 0xFFEF4444L,
            accentSecondary = 0xFFF8FAFCL,
            keywords = listOf("switzerland", "zurich", "geneva", "سوئیس")
        ),
        RawCountryEntry(
            code = "CA",
            nameFa = "کانادا",
            nameEn = "Canada",
            stripes = listOf(0xFFFF0000L, 0xFFF8FAFCL, 0xFFFF0000L),
            accentPrimary = 0xFFEF4444L,
            accentSecondary = 0xFFF8FAFCL,
            keywords = listOf("canada", "toronto", "montreal", "vancouver", "کانادا")
        ),
        RawCountryEntry(
            code = "PL",
            nameFa = "لهستان",
            nameEn = "Poland",
            stripes = listOf(0xFFF8FAFCL, 0xFFDC143CL, 0xFFDC143CL),
            accentPrimary = 0xFFF43F5EL,
            accentSecondary = 0xFFF8FAFCL,
            keywords = listOf("poland", "warsaw", "لهستان")
        ),
        RawCountryEntry(
            code = "AT",
            nameFa = "اتریش",
            nameEn = "Austria",
            stripes = listOf(0xFFED2939L, 0xFFF8FAFCL, 0xFFED2939L),
            accentPrimary = 0xFFF43F5EL,
            accentSecondary = 0xFFF8FAFCL,
            keywords = listOf("austria", "vienna", "اتریش")
        ),
        RawCountryEntry(
            code = "JP",
            nameFa = "ژاپن",
            nameEn = "Japan",
            stripes = listOf(0xFFF8FAFCL, 0xFFBC002DL, 0xFFF8FAFCL),
            accentPrimary = 0xFFF43F5EL,
            accentSecondary = 0xFFE2E8F0L,
            keywords = listOf("japan", "tokyo", "osaka", "ژاپن")
        ),
        RawCountryEntry(
            code = "KR",
            nameFa = "کره جنوبی",
            nameEn = "South Korea",
            stripes = listOf(0xFFF8FAFCL, 0xFFCD2E3AL, 0xFF0047A0L),
            accentPrimary = 0xFF3B82F6L,
            accentSecondary = 0xFFF43F5EL,
            keywords = listOf("korea", "seoul", "کره")
        ),
        RawCountryEntry(
            code = "IT",
            nameFa = "ایتالیا",
            nameEn = "Italy",
            stripes = listOf(0xFF009246L, 0xFFF8FAFCL, 0xFFCE2B37L),
            accentPrimary = 0xFF10B981L,
            accentSecondary = 0xFFEF4444L,
            keywords = listOf("italy", "milan", "rome", "ایتالیا")
        ),
        RawCountryEntry(
            code = "ES",
            nameFa = "اسپانیا",
            nameEn = "Spain",
            stripes = listOf(0xFFAA151BL, 0xFFF1BF00L, 0xFFAA151BL),
            accentPrimary = 0xFFFACC15L,
            accentSecondary = 0xFFEF4444L,
            keywords = listOf("spain", "madrid", "barcelona", "اسپانیا")
        ),
        RawCountryEntry(
            code = "RU",
            nameFa = "روسیه",
            nameEn = "Russia",
            stripes = listOf(0xFFF8FAFCL, 0xFF0039A6L, 0xFFD52B1EL),
            accentPrimary = 0xFF3B82F6L,
            accentSecondary = 0xFFEF4444L,
            keywords = listOf("russia", "moscow", "petersburg", "روسیه")
        ),
        RawCountryEntry(
            code = "IN",
            nameFa = "هند",
            nameEn = "India",
            stripes = listOf(0xFFFF9933L, 0xFFF8FAFCL, 0xFF138808L),
            accentPrimary = 0xFFFB923CL,
            accentSecondary = 0xFF22C55EL,
            keywords = listOf("india", "mumbai", "bangalore", "هند")
        ),
        RawCountryEntry(
            code = "IR",
            nameFa = "ایران",
            nameEn = "Iran",
            stripes = listOf(0xFF239F40L, 0xFFF8FAFCL, 0xFFDA0000L),
            accentPrimary = 0xFF10B981L,
            accentSecondary = 0xFFEF4444L,
            keywords = listOf("iran", "tehran", "shiraz", "tabriz", "isfahan", "irancell", "mci", "ایران", "تهران", "تانل")
        )
    )

    private val BY_CODE: Map<String, RawCountryEntry> = ENTRIES.associateBy { it.code }

    val IRAN_SPEC: CountryThemeSpec = toSpec(BY_CODE.getValue("IR"))

    val DEFAULT_SPEC: CountryThemeSpec = CountryThemeSpec(
        code = "UN",
        nameFa = "بین‌الملل",
        nameEn = "Global",
        flagEmoji = isoToFlagEmoji("UN"),
        stripeColors = listOf(0xFF1E293BL, 0xFFF5C451L, 0xFF10B981L),
        accentPrimary = 0xFFF5C451L,
        accentSecondary = 0xFF10B981L
    )

    fun isoToFlagEmoji(isoCode: String): String {
        val clean = isoCode.trim().uppercase(Locale.US)
        if (clean.length != 2 || clean == "UN" || clean == "XX") {
            return String(Character.toChars(0x1F310))
        }
        val c0 = clean[0]
        val c1 = clean[1]
        if (c0 !in 'A'..'Z' || c1 !in 'A'..'Z') {
            return String(Character.toChars(0x1F310))
        }
        val cp0 = 0x1F1E6 + (c0.code - 'A'.code)
        val cp1 = 0x1F1E6 + (c1.code - 'A'.code)
        return String(Character.toChars(cp0)) + String(Character.toChars(cp1))
    }

    fun extractIsoFromFlagEmoji(text: String): String? {
        if (text.isEmpty()) return null
        val cps = ArrayList<Int>(text.length)
        var idx = 0
        while (idx < text.length) {
            val cp = Character.codePointAt(text, idx)
            cps.add(cp)
            idx += Character.charCount(cp)
        }
        var i = 0
        while (i + 1 < cps.size) {
            val first = cps[i]
            val second = cps[i + 1]
            if (first in 0x1F1E6..0x1F1FF && second in 0x1F1E6..0x1F1FF) {
                val ch0 = ('A'.code + (first - 0x1F1E6)).toChar()
                val ch1 = ('A'.code + (second - 0x1F1E6)).toChar()
                return "$ch0$ch1"
            }
            i++
        }
        return null
    }

    fun resolveByCode(code: String): CountryThemeSpec {
        val upper = code.trim().uppercase(Locale.US).let { if (it == "UK") "GB" else it }
        if (upper.length != 2 || upper == "UN" || upper == "XX") {
            return DEFAULT_SPEC
        }
        val known = BY_CODE[upper]
        if (known != null) {
            return toSpec(known)
        }
        val hash = upper.hashCode()
        val huePalette = listOf(
            0xFF38BDF8L to 0xFF818CF8L,
            0xFF34D399L to 0xFFFBBF24L,
            0xFFF472B6L to 0xFF60A5FAL,
            0xFFFB923CL to 0xFFFACC15L
        )
        val pair = huePalette[(hash and 0x7FFFFFFF) % huePalette.size]
        return CountryThemeSpec(
            code = upper,
            nameFa = upper,
            nameEn = upper,
            flagEmoji = isoToFlagEmoji(upper),
            stripeColors = listOf(pair.first, 0xFFF8FAFCL, pair.second),
            accentPrimary = pair.first,
            accentSecondary = pair.second
        )
    }

    fun detectCountry(
        name: String,
        host: String,
        sni: String = "",
        explicitCode: String = ""
    ): CountryThemeSpec {
        if (explicitCode.isNotBlank() && !explicitCode.equals("UN", ignoreCase = true)) {
            return resolveByCode(explicitCode)
        }

        val emojiIso = extractIsoFromFlagEmoji(name)
        if (emojiIso != null) {
            return resolveByCode(emojiIso)
        }

        val hostLower = host.trim().lowercase(Locale.US)
        val sniLower = sni.trim().lowercase(Locale.US)
        val tldCode = extractCountryTld(hostLower) ?: extractCountryTld(sniLower)
        if (tldCode != null) {
            return resolveByCode(tldCode)
        }

        val combined = "$name $hostLower $sniLower".lowercase(Locale.US)
        for (entry in ENTRIES) {
            for (kw in entry.keywords) {
                if (combined.contains(kw)) {
                    return toSpec(entry)
                }
            }
        }

        val tokens = name.uppercase(Locale.US)
            .split(Regex("[^A-Z0-9]+"))
            .filter { it.length == 2 }
        for (tok in tokens) {
            val normalized = if (tok == "UK") "GB" else tok
            if (BY_CODE.containsKey(normalized)) {
                return resolveByCode(normalized)
            }
        }

        return DEFAULT_SPEC
    }

    private fun extractCountryTld(domain: String): String? {
        if (domain.isEmpty() || domain.all { it.isDigit() || it == '.' || it == ':' }) return null
        val tldMap = mapOf(
            ".ir" to "IR",
            ".de" to "DE",
            ".nl" to "NL",
            ".fi" to "FI",
            ".uk" to "GB",
            ".fr" to "FR",
            ".tr" to "TR",
            ".ae" to "AE",
            ".sg" to "SG",
            ".se" to "SE",
            ".ch" to "CH",
            ".ca" to "CA",
            ".pl" to "PL",
            ".at" to "AT",
            ".jp" to "JP",
            ".kr" to "KR",
            ".it" to "IT",
            ".es" to "ES",
            ".ru" to "RU",
            ".in" to "IN"
        )
        for ((suffix, iso) in tldMap) {
            if (domain.endsWith(suffix)) return iso
        }
        return null
    }

    private fun toSpec(entry: RawCountryEntry): CountryThemeSpec {
        return CountryThemeSpec(
            code = entry.code,
            nameFa = entry.nameFa,
            nameEn = entry.nameEn,
            flagEmoji = isoToFlagEmoji(entry.code),
            stripeColors = entry.stripes,
            accentPrimary = entry.accentPrimary,
            accentSecondary = entry.accentSecondary
        )
    }
}
