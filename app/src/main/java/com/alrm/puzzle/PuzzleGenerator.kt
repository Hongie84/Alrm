package com.alrm.puzzle

import com.alrm.alarm.PuzzleType
import kotlin.random.Random

data class MathPuzzle(
    val question: String,
    val answer: Int
)

data class SequencePuzzle(
    val visibleNumbers: List<Int>,
    val answer: Int,
    val hint: String
)

data class MemoryPuzzle(
    val sequence: List<Int>,   // digits 1..9 shown briefly, then hidden
    val answer: String         // user must re-enter the sequence as a string
)

object PuzzleGenerator {

    fun generateMathPuzzle(difficulty: Int = 1): MathPuzzle {
        return when (difficulty) {
            1 -> {
                val a = Random.nextInt(1, 20)
                val b = Random.nextInt(1, 20)
                val op = Random.nextInt(0, 2)
                if (op == 0) MathPuzzle("$a + $b = ?", a + b)
                else MathPuzzle("$a × $b = ?", a * b)
            }
            2 -> {
                val a = Random.nextInt(10, 100)
                val b = Random.nextInt(1, 20)
                val ops = listOf(
                    Triple("$a + $b", a + b, "+"),
                    Triple("$a − $b", a - b, "−"),
                    Triple("$a × $b", a * b, "×")
                )
                val (q, ans, _) = ops.random()
                MathPuzzle("$q = ?", ans)
            }
            else -> {
                // Three-operand expression
                val a = Random.nextInt(2, 20)
                val b = Random.nextInt(2, 20)
                val c = Random.nextInt(2, 10)
                MathPuzzle("($a + $b) × $c = ?", (a + b) * c)
            }
        }
    }

    fun generateSequencePuzzle(): SequencePuzzle {
        // Linear sequences: a, a+d, a+2d, a+3d, _?
        val start = Random.nextInt(1, 20)
        val diff = Random.nextInt(1, 15) * (if (Random.nextBoolean()) 1 else -1)
        val seq = (0..3).map { start + it * diff }
        val next = start + 4 * diff
        return SequencePuzzle(
            visibleNumbers = seq,
            answer = next,
            hint = "What comes next?"
        )
    }

    fun generateMemoryPuzzle(length: Int = 4): MemoryPuzzle {
        val seq = (1..length).map { Random.nextInt(1, 10) }
        return MemoryPuzzle(
            sequence = seq,
            answer = seq.joinToString("")
        )
    }

    fun isPuzzleTypeFor(type: PuzzleType): Boolean = true
}
