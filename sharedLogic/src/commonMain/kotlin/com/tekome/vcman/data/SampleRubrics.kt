package com.tekome.vcman.data

data class SampleRubric(
    val title: String,
    val text: String,
) {
    init {
        require(title.isNotBlank()) { "SampleRubric.title must not be blank" }
        require(text.isNotBlank()) { "SampleRubric.text must not be blank" }
    }
}

object SampleRubrics {
    val cryptoBenchScore =
        SampleRubric(
            title = "Crypto Bench Score",
            text =
                """
                # Crypto Benchmark Score

                Score the subject project against 5 sections (0-10 per question, weighted).

                | Section | Max | Weight formula |
                |---|---|---|
                | V - Value | 30 | Working product, mainnet activity, exchange listings, real utility |
                | C - Community | 29 | Team, validators/miners, investors, KOLs, governance, mission |
                | M - Money | 10 | Funding raised relative to product stage (PoC / mainnet / production) |
                | A - Attention & Adoption | 10 | Comms, BD, community management staffing |
                | N - Need | 25 | Problem legitimacy, market urgency, niche difficulty, first-mover edge |

                ## V - Value (max 30)
                - Public whitepaper or technical document exists.
                - Whitepaper has clear technical content.
                - Working testnet or prototype exists, with real and maintained usage.
                - Mainnet is active, decentralized, and secure.
                - Token is listed on a tier-1 exchange and has real utility in its ecosystem.

                ## C - Community (max 29)
                - Founders/lead devs, validators/miners, investors, and holders are identifiable.
                - Journalists/KOLs cover the project; it has organic cultural momentum and resilience
                  through bear markets.
                - Governance process is clear, documented, and it is known who makes final decisions.
                - Technical and cultural mission are distinct from competitors.
                - Exchange listings: more/larger exchanges score higher (Binance/Coinbase highest,
                  DEX-only listings lowest).

                ## M - Money (max 10)
                - ~$1-2M funding is enough for a prototype/PoC stage.
                - ~$5M is enough for a working mainnet/testnet stage.
                - $10M+ is enough for a production-ready stage.

                ## A - Attention & Adoption (max 10)
                - Dedicated comms/media, business development, and community management staff exist.

                ## N - Need (max 25)
                - The problem being solved is legitimate and the market is genuinely underserved.
                - The niche is difficult to replicate (scale, technical difficulty, timing).
                - The project has first-mover advantage and dominates its niche.

                ## Scoring scale (per question, 0-10)
                - 9-10 excellent, verifiable evidence beating the benchmark.
                - 7-8 good, meets the bar with verifiable evidence.
                - 5-6 average, some data but incomplete.
                - 3-4 weak, missing evidence or red flags.
                - 1-2 very weak, no data or the project looks abandoned.
                - 0 nothing found / not verifiable.

                ## Output format
                Report the total score out of 104, a breakdown per section (Value/Community/
                Money/Attention/Need), and a short overall assessment comparing the subject to
                well-known benchmarks (e.g. BTC, ETH). Do not guess: mark unavailable data as
                "not available" instead of scoring it.
                """.trimIndent(),
        )
}
