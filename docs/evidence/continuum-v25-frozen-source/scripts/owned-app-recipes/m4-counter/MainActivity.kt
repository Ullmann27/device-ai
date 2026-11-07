package at.heinz.continuum.m4counter

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Two local screens, bounded counter, no permissions. */
class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("continuum_m4_counter", MODE_PRIVATE) }
    private lateinit var body: LinearLayout
    private var count = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        count = CounterPolicy.restored(prefs.getInt("count", 0))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val nav = LinearLayout(this)
        nav.addView(Button(this).apply { text = "Zähler"; setOnClickListener { showCounter() } })
        nav.addView(Button(this).apply { text = "Über"; setOnClickListener { showAbout() } })
        root.addView(nav)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(body)
        setContentView(root)
        showCounter()
    }
    private fun showCounter() {
        body.removeAllViews()
        val label = TextView(this).apply { text = count.toString(); textSize = 32f }
        val status = TextView(this)
        fun save(next: Int) {
            if (prefs.edit().putInt("count", next).commit()) {
                count = next
                label.text = count.toString()
                status.text = "Lokal gespeichert."
            } else status.text = "Speichern fehlgeschlagen; Zähler unverändert."
        }
        body.addView(label)
        body.addView(Button(this).apply { text = "+1"; setOnClickListener { save(CounterPolicy.increment(count)) } })
        body.addView(Button(this).apply { text = "Auf null setzen"; setOnClickListener { save(0) } })
        body.addView(status)
    }
    private fun showAbout() {
        body.removeAllViews()
        body.addView(TextView(this).apply {
            text = "Continuum M4: Lokaler Zähler von 0 bis 9999. Keine Netzberechtigung."
            textSize = 18f
        })
    }
}
