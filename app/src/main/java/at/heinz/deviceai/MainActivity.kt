package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var bridge: ShizukuBridge
    private lateinit var interpreter: CommandInterpreter
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var runButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        interpreter = CommandInterpreter(this)
        bridge = ShizukuBridge(this) { message ->
            runOnUiThread {
                status.text = message
                runButton.isEnabled = true
            }
        }

        setContentView(buildUi())
        bridge.start()
    }

    override fun onDestroy() {
        bridge.stop()
        super.onDestroy()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(26), dp(22), dp(26))
            setBackgroundColor(Color.rgb(16, 18, 24))
        }

        root.addView(TextView(this).apply {
            text = "Device AI"
            textSize = 30f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        status = TextView(this).apply {
            text = "Prüfe Shizuku …"
            textSize = 15f
            setTextColor(Color.rgb(170, 185, 220))
            setPadding(0, dp(8), 0, dp(20))
        }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "Shizuku verbinden"
            setOnClickListener { bridge.requestAccess() }
        }, matchWidth())

        root.addView(TextView(this).apply {
            text = "Was soll ich auf dem Handy machen?"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, dp(22), 0, dp(8))
        })

        input = EditText(this).apply {
            hint = "z. B. „Öffne WhatsApp“"
            setHintTextColor(Color.rgb(135, 140, 155))
            setTextColor(Color.WHITE)
            textSize = 17f
            minLines = 2
            maxLines = 5
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(Color.rgb(35, 39, 50))
        }
        root.addView(input, matchWidth())

        runButton = Button(this).apply {
            text = "Ausführen"
            setOnClickListener { handleInput() }
        }
        root.addView(runButton, matchWidth(top = 10))

        val quickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        quickRow.addView(quickButton("Apps", "Apps"), weighted())
        quickRow.addView(quickButton("Downloads", "Downloads"), weighted())
        root.addView(quickRow, matchWidth(top = 10))

        val quickRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        quickRow2.addView(quickButton("Screenshot", "Screenshot"), weighted())
        quickRow2.addView(quickButton("Speicher", "Speicher"), weighted())
        root.addView(quickRow2, matchWidth(top = 6))

        root.addView(TextView(this).apply {
            text = "Ergebnis"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, dp(22), 0, dp(8))
        })

        output = TextView(this).apply {
            text = "Bereit."
            textSize = 14f
            setTextColor(Color.rgb(220, 225, 235))
            setTextIsSelectable(true)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.rgb(25, 28, 36))
        }

        val scroll = ScrollView(this).apply {
            addView(output, matchWidth())
        }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { topMargin = dp(4) }
        )

        return root
    }

    private fun quickButton(label: String, command: String): Button =
        Button(this).apply {
            text = label
            setOnClickListener {
                input.setText(command)
                handleInput()
            }
        }

    private fun handleInput() {
        val plan = interpreter.interpret(input.text.toString()) ?: return

        if (plan.needsConfirmation) {
            AlertDialog.Builder(this)
                .setTitle(plan.title)
                .setMessage("Diese Aktion verändert den Gerätezustand. Ausführen?")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Ausführen") { _, _ -> execute(plan) }
                .show()
        } else {
            execute(plan)
        }
    }

    private fun execute(plan: CommandPlan) {
        runButton.isEnabled = false
        output.text = "⏳ ${plan.title} …"

        bridge.execute(plan.shellCommand) { result ->
            runButton.isEnabled = true
            output.text = "✓ ${plan.title}\n\n$result"
        }
    }

    private fun matchWidth(top: Int = 0) =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(top) }

    private fun weighted() =
        LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            marginStart = dp(3)
            marginEnd = dp(3)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
