package com.alrm.ui

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.*
import com.alrm.R
import com.alrm.alarm.PuzzleType
import com.alrm.puzzle.PuzzleGenerator
import kotlin.math.sqrt

class PuzzleActivity : Activity(), SensorEventListener {

    companion object {
        const val EXTRA_SNOOZE_DURATION = "snooze_duration"
        const val EXTRA_PUZZLE_TYPE = "puzzle_type"
        private const val MEMORY_SHOW_MS = 3000L
        private const val SHAKE_THRESHOLD = 15f
        private const val SHAKE_COUNT_REQUIRED = 5
    }

    private lateinit var puzzleType: PuzzleType
    private var mathAnswer: Int = 0
    private var seqAnswer: Int = 0
    private var memAnswer: String = ""
    private var shakeCount = 0
    private var lastShakeTime = 0L
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastAccel = FloatArray(3) { 0f }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_puzzle)
        actionBar?.setDisplayHomeAsUpEnabled(false)
        puzzleType = PuzzleType.valueOf(
            intent.getStringExtra(EXTRA_PUZZLE_TYPE) ?: PuzzleType.MATH.name
        )
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        (findViewById(R.id.btnSubmit) as Button).setOnClickListener { checkAnswer() }
        presentPuzzle()
    }

    private fun tv(id: Int): TextView = findViewById(id) as TextView
    private fun progress(): ProgressBar = findViewById(R.id.progressShake) as ProgressBar

    private fun presentPuzzle() = when (puzzleType) {
        PuzzleType.MATH -> {
            val p = PuzzleGenerator.generateMathPuzzle()
            mathAnswer = p.answer
            tv(R.id.textPuzzleQuestion).text = p.question
            showInputLayout(true)
        }
        PuzzleType.SEQUENCE -> {
            val p = PuzzleGenerator.generateSequencePuzzle()
            seqAnswer = p.answer
            tv(R.id.textPuzzleQuestion).text =
                p.visibleNumbers.joinToString(" , ") + " , ?"
            showInputLayout(true)
        }
        PuzzleType.MEMORY -> {
            val p = PuzzleGenerator.generateMemoryPuzzle(4)
            memAnswer = p.answer
            showInputLayout(false)
            tv(R.id.textPuzzleSubtitle).visibility = View.VISIBLE
            tv(R.id.textPuzzleSubtitle).text = "Memorise this sequence:"
            object : CountDownTimer(MEMORY_SHOW_MS, 1000) {
                override fun onTick(ms: Long) {
                    tv(R.id.textPuzzleQuestion).text =
                        p.sequence.joinToString("  ") + "\n\n${ms/1000+1}..."
                }
                override fun onFinish() {
                    tv(R.id.textPuzzleQuestion).text = "What was the sequence?"
                    tv(R.id.textPuzzleSubtitle).text = "Enter digits without spaces"
                    showInputLayout(true)
                    val et = findViewById(R.id.editAnswer) as EditText
                    et.inputType = android.text.InputType.TYPE_CLASS_NUMBER
                }
            }.start()
        }
        PuzzleType.SHAKE -> {
            tv(R.id.textPuzzleQuestion).text =
                "Shake your phone $SHAKE_COUNT_REQUIRED times!"
            showInputLayout(false)
            progress().visibility = View.VISIBLE
            tv(R.id.textShakeCount).visibility = View.VISIBLE
            progress().max = SHAKE_COUNT_REQUIRED
        }
    }

    private fun showInputLayout(show: Boolean) {
        val layout = findViewById(R.id.layoutInput) as View
        layout.visibility = if (show) View.VISIBLE else View.GONE
        progress().visibility = View.GONE
        tv(R.id.textShakeCount).visibility = View.GONE
    }

    private fun checkAnswer() {
        val input = (findViewById(R.id.editAnswer) as EditText).text.toString().trim()
        val correct = when (puzzleType) {
            PuzzleType.MATH -> input.toIntOrNull() == mathAnswer
            PuzzleType.SEQUENCE -> input.toIntOrNull() == seqAnswer
            PuzzleType.MEMORY -> input == memAnswer
            PuzzleType.SHAKE -> false
        }
        if (correct) {
            setResult(RESULT_OK); finish()
        } else {
            Toast.makeText(this, "Wrong! Try again.", Toast.LENGTH_SHORT).show()
            (findViewById(R.id.editAnswer) as EditText).text.clear()
            presentPuzzle()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (puzzleType != PuzzleType.SHAKE) return
        val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
        val delta = sqrt(
            (x-lastAccel[0])*(x-lastAccel[0]) +
            (y-lastAccel[1])*(y-lastAccel[1]) +
            (z-lastAccel[2])*(z-lastAccel[2])
        )
        lastAccel = floatArrayOf(x, y, z)
        val now = System.currentTimeMillis()
        if (delta > SHAKE_THRESHOLD && now - lastShakeTime > 300) {
            lastShakeTime = now
            shakeCount++
            progress().progress = shakeCount
            tv(R.id.textShakeCount).text = "$shakeCount / $SHAKE_COUNT_REQUIRED"
            if (shakeCount >= SHAKE_COUNT_REQUIRED) {
                sensorManager.unregisterListener(this)
                setResult(RESULT_OK); finish()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        if (puzzleType == PuzzleType.SHAKE) {
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun onPause() { sensorManager.unregisterListener(this); super.onPause() }
    override fun onBackPressed() {}
}
