package com.maru.expenserecorder

object ExpenseParser {

    data class ParsedExpense(val amount: Double, val description: String)

    private val numberWords = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30,
        "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70,
        "eighty" to 80, "ninety" to 90, "hundred" to 100, "thousand" to 1000,
        // Hebrew transliterations
        "efes" to 0, "ahat" to 1, "echad" to 1, "shtaim" to 2, "shnayim" to 2,
        "shalosh" to 3, "arba" to 4, "hamesh" to 5, "shesh" to 6,
        "sheva" to 7, "shmone" to 8, "tesha" to 9, "eser" to 10,
        "esrim" to 20, "shloshim" to 30, "arbaim" to 40, "hamishim" to 50,
        "shishim" to 60, "shivim" to 70, "shmonim" to 80, "tishim" to 90,
        "meah" to 100, "alef" to 1000
    )

    private val shekelWords = setOf(
        "shekel", "shekels", "nis", "ils", "shek", "שקל", "שקלים", "₪"
    )

    fun parse(input: String): ParsedExpense? {
        val text = input.trim().lowercase()
        val tokens = text.split(Regex("\\s+|[,]"))

        var amount: Double? = null
        val descriptionTokens = mutableListOf<String>()
        var i = 0

        while (i < tokens.size) {
            val token = tokens[i]

            // Skip shekel keywords
            if (token in shekelWords) { i++; continue }

            // Try digit parse (handles "50", "50.5", "50,5")
            val numericVal = token.replace(',', '.').toDoubleOrNull()
            if (numericVal != null) {
                amount = numericVal
                i++
                continue
            }

            // Try word-number parse
            val wordVal = resolveWordNumber(tokens, i)
            if (wordVal != null) {
                amount = wordVal.first.toDouble()
                i += wordVal.second
                continue
            }

            // Everything else is description
            if (token.isNotBlank() && token !in shekelWords) {
                descriptionTokens.add(token)
            }
            i++
        }

        val finalAmount = amount ?: return null
        val description = descriptionTokens
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replaceFirstChar { it.uppercase() }
            .ifBlank { "Cash expense" }

        return ParsedExpense(finalAmount, description)
    }

    // Returns (value, tokensConsumed) or null
    private fun resolveWordNumber(tokens: List<String>, start: Int): Pair<Int, Int>? {
        var value = 0
        var current = 0
        var consumed = 0
        var found = false

        var i = start
        while (i < tokens.size && tokens[i] !in shekelWords) {
            val word = tokens[i]
            val n = numberWords[word] ?: break

            when {
                n == 1000 -> { current = if (current == 0) 1000 else current * 1000 }
                n == 100  -> { current = if (current == 0) 100 else current * 100 }
                else      -> current += n
            }
            found = true
            consumed++
            i++
        }

        if (!found) return null
        value += current
        return Pair(value, consumed)
    }
}
