# Hinweis zu den Godot-Aufnahmen

Die Aufnahmen entstehen mit einem Nachweis-Skript, nicht im laufenden Spiel. Darin ist `_process` der Figur abgeschaltet. `sample_combat()` startet für Kampftechniken eine Überblendung von 0,10 s, die im Spiel `_process` → `CharacterMotion.advance()` fortschreibt.

Ohne diesen Schritt zeigt das Bild die Ausgangspose der Überblendung. Betroffen sind:

- **Bild 11:** Die Kacheln „jab_right“ und „uppercut_right“ zeigen nicht die Technik. „jab_right“ zeigt die Ausgangspose, „uppercut_right“ die vorher aufgenommene Block-Pose.
- **Bild 12:** Die Kachel „jab_right“ zeigt nicht die Technik.
- **Bild 13:** Die Kachel „uppercut_right“ zeigt ebenfalls die Block-Pose.
- **Bild 14:** Die Kacheln „uppercut_left“ zeigen die Block-Pose. Die Kacheln „block“ in Bild 11 und 13 sind korrekt, weil Block über `_play()` ohne Überblendung läuft.
- **Bild 17** (`godot_strikes_capture.gd`) ist korrigiert: erst abtasten, dann `actor._motion.advance(10.0)`, dann erneut abtasten und den Player anhalten. Es zeigt die echten Posen von Jab, Haken, Uppercut, Front- und Seitkick, Block und Idle, jeweils vorher (#87) und nachher (#88).

Ein Spielfehler wurde dadurch nicht gefunden; im Spiel schreitet die Überblendung normal fort.
