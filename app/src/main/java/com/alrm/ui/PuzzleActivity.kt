package com.alrm.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alrm.R
import com.alrm.alarm.PuzzleType
import com.alrm.databinding.ActivityPuzzleBinding
import com.alrm.puzzle.MemoryPuzzle
import com.alrm.puzzle.MathPuzzle
import com.alrm.puzzle.PuzzleGenerator
import com.alrm.puzzle.SequencePuzzle
import kotlin.math.abs
import kotlin.math.sqrt

class PuzzleActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        const val EXTRA_SNOOZE_DURATION = "snooze_duration"
        const val EXTRA_PUZZLE_TYPE = "puzzle_type"
        private const val MEMORY_SHOW_MS = 3000L
        private const val SHAKE_THRESHOLD = 15f
        private const val SHAKE_COUNT_REQUIRED = 5
    }

    private lateinit var binding: ActivityPuzzleBinding
    private var puzzleType: PuzzleType = PuzzleType.MATH

    // Puzzle state
    private var mathPuzzle: MathPuzzle? = null
    private var seqPuzzle: SequencePuzzle? = null
    private var memPuzzle: MemoryPuzzle? = null
    private var shakeCount = 0
    private var lastShakeTime = 0L

    // Sensors for shake
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastAccel = FloatArray(3) { 0f }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPuzzleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        puzzleType = PuzzleType.valueOf(
            intent.getStringExtra(EXTRA_PUZZLE_TYPE) ?: PuzzleType.MATH.name
        )

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        binding.btnSubmit.setOnClickListener { checkAnswer() }

        presentPuzzle()
    }

    private fun presentPuzzle() {
        when (puzzleType) {
            PuzzleType.MATH -> presentMath()
            PuzzleType.SEQUENCE -> presentSequence()
            PuzzleType.MEMORY -> presentMemory()
            PuzzleType.SHAKE -> presentShake()
        }
    }

    private fun presentMath() {
        mathPuzzle = PuzzleGenerator.generateMathPuzzle(difficulty = 1)
        binding.textPuzzleQuestion.text = mathPuzzle!!.question
        binding.layoutInput.visibility = View.VISIBLE
        binding.layoutShake.visibility = View.GONE
        binding.editAnswer.hint = getString(R.string.enter_answer)
    }

    private fun presentSequence() {
        seqPuzzle = PuzzleGenerator.generateSequencePuzzle()
        val seq = seqPuzzle!!.visibleNumbers.joinToString("  ,  ")
        binding.textPuzzleQuestion.text = "$seq  ,  ?"
        binding.layoutInput.visibility = View.VISIBLE
        binding.layoutShake.visibility = View.GONE
        binding.editAnswer.hint = getString(R.string.enter_next_number)
    }

    private fun presentMemory() {
        memPuzzle = PuzzleGenerator.generateMemoryPuzzle(length = 4)
        val seq = memPuzzle!!.sequence.joinToString("  ")

        // Show the sequence briefly then hide it
        binding.textPuzzleQuestion.text = seq
        binding.layoutInput.visibility = View.GONE
        binding.layoutShake.visibility = View.GONE
        binding.textPuzzleSubtitle.text = getString(R.string.memorise_sequence)
        binding.textPuzzleSubtitle.visibility = View.VISIBLE

        object : CountDownTimer(MEMORY_SHOW_MS, 1000) {
            override fun onTick(ms: Long) {
                binding.textPuzzleQuestion.text = "${seq}\n\n${ms / 1000 + 1}..."
            }
            override fun onFinish() {
                binding.textPuzzleQuestion.text = getString(R.string.what_was_the_sequence)
                binding.textPuzzleSubtitle.text = getString(R.string.enter_digits_no_spaces)
                binding.layoutInput.visibility = View.VISIBLE
                binding.editAnswer.hint = getString(R.string.enter_sequence)
            }
        }.start()
    }

    private fun presentShake() {
        binding.textPuzzleQuestion.text = getString(R.string.shake_phone_puzzle, SHAKE_COUNT_REQUIRED)
        binding.layoutInput.visibility = View.GONE
        binding.layoutShake.visibility = View.VISIBLE
        binding.progressShake.max = SHAKE_COUNT_REQUIRED
        accelerometer?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun checkAnswer() {
        val input = binding.editAnswer.text?.toString()?.trim() ?: ""
        val correct = when (puzzleType) {
            PuzzleType.MATH -> {
                val ans = input.toIntOrNull()
                ans == mathPuzzle?.answer
            }
            PuzzleType.SEQUENCE -> {
                val ans = input.toIntOrNull()
                ans == seqPuzzle?.answer
            }
            PuzzleType.MEMORY -> {
                input == memPuzzle?.answer
            }
            PuzzleType.SHAKE -> false // handled by sensor
        }

        if (correct) {
            puzzleSolved()
        } else {
            Toast.makeText(this, R.string.wrong_answer_try_again, Toast.LENGTH_SHORT).show()
            binding.editAnswer.text?.clear()
            // Regenerate to prevent brute-force
            presentPuzzle()
        }
    }

    private fun puzzleSolved() {
        setResult(RESULT_OK)
        finish()
    }

    // SensorEventListener for shake detection
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val delta = sqrt(
            (x - lastAccel[0]) * (x - lastAccel[0]) +
            (y - lastAccel[1]) * (y - lastAccel[1]) +
            (z - lastAccel[2]) * (z - lastAccel[2])
        )
        lastAccel = floatArrayOf(x, y, z)

        val now = System.currentTimeMillis()
        if (delta > SHAKE_THRESHOLD && now - lastShakeTime > 300) {
            lastShakeTime = now
            shakeCount++
            binding.progressShake.progress = shakeCount
            binding.textShakeCount.text = "$shakeCount / $SHAKE_COUNT_REQUIRED"
            if (shakeCount >= SHAKE_COUNT_REQUIRED) {
                sensorManager.unregisterListener(this)
                puzzleSolved()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        if (puzzleType == PuzzleType.SHAKE) {
            accelerometer?.also { sensor ->
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onBackPressed() {
        // Cannot dismiss puzzle by pressing back
    }
}
