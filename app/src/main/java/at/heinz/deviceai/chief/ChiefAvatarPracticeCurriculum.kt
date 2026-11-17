package at.heinz.deviceai.chief

import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.AvatarEvent
import at.heinz.deviceai.avatar.AvatarPose
import at.heinz.deviceai.avatar.AvatarPoseReducer
import at.heinz.deviceai.avatar.AvatarRuntimeSignals
import at.heinz.deviceai.avatar.AvatarSink
import at.heinz.deviceai.avatar.GazeTarget
import at.heinz.deviceai.cognitive.AvatarCameraPolicy
import at.heinz.deviceai.cognitive.CameraFaceBox
import at.heinz.deviceai.cognitive.CameraFrameGeometry

/**
 * EXP-07/EXP-11: bounded prediction exercises for Iris, using the existing production logic as oracle.
 * Constructing this catalog performs no model call, persistence, camera capture or rendering. It is
 * not a completed bot attempt. The caller sends only [ChiefBotPracticeExercise.prompt] to the model;
 * expected answers and corrective explanations remain with the independent practice evaluator.
 */
object ChiefAvatarPracticeCurriculum {
    fun exercises(): List<ChiefBotPracticeExercise> = listOf(
        statePriority(), interruptedSpeech(), gazeExpiry(), cameraRevocation(),
        replacementSpeech(), taskGazePriority(),
    )

    private fun statePriority(): ChiefBotPracticeExercise {
        val trace = Trace(1_000)
        trace.director.onRuntime(AvatarRuntimeSignals(busy = true))
        val work = trace.pose()
        trace.now = 1_020
        trace.director.onRuntime(AvatarRuntimeSignals(busy = true, speaking = true, awaitingApproval = true))
        val approval = trace.pose()
        trace.now = 1_040
        trace.director.onRuntime(AvatarRuntimeSignals())
        val finished = trace.pose()
        return exercise(
            id = "iris-state-priority-v1", title = "Arbeit, Freigabe und Mimik",
            rules = "Runtime wählt die erste zutreffende Regel: awaitingApproval→WAITING_FOR_APPROVAL, " +
                "listening→LISTENING, speaking→SPEAKING, blocked→BLOCKED, researching→RESEARCHING, " +
                "busy&&learning→LEARNING, busy→THINKING, sonst IDLE. Ausdruck: LISTENING/WAITING_FOR_APPROVAL→ATTENTIVE; " +
                "THINKING/LEARNING→FOCUSED; RESEARCHING→CURIOUS; BLOCKED→CONCERNED; sonst NEUTRAL.",
            scenario = """
                Neue AvatarDirector-/AvatarPoseReducer-Instanz; alle nicht genannten Runtime-Flags sind false.
                t=1000 ms: onRuntime(busy=true). Lies work_state und work_expression.
                t=1020: onRuntime(busy=true, speaking=true, awaitingApproval=true). Lies approval_state und approval_expression.
                t=1040: onRuntime() ohne aktive Flags. Lies final_state.
            """.trimIndent(),
            expected = linkedMapOf(
                "work_state" to work.state.name, "work_expression" to work.expression.name,
                "approval_state" to approval.state.name, "approval_expression" to approval.expression.name,
                "final_state" to finished.state.name,
            ),
            explanation = "Die Runtime-Flags haben eine feste Priorität. Eine ausstehende Freigabe hat Vorrang " +
                "vor Sprechen und Arbeit. Zustand und zugehöriger Ausdruck stammen gemeinsam aus dem Director.",
        )
    }

    private fun interruptedSpeech(): ChiefBotPracticeExercise {
        val trace = Trace(2_000)
        trace.director.onRuntime(AvatarRuntimeSignals(speaking = true))
        trace.director.speechStarted("satz-a", "Hallo")
        trace.now = 2_010
        trace.director.wordRange("satz-a", 0, 5)
        trace.now = 2_100
        trace.director.audioLevel("satz-a", .8f)
        val before = trace.pose()
        trace.now = 2_120
        trace.director.speechEnded("satz-a", interrupted = true)
        trace.now = 2_130
        trace.director.audioLevel("satz-a", 1f)
        trace.director.wordRange("satz-a", 0, 5)
        val after = trace.pose()
        return exercise(
            id = "iris-speech-interruption-v1", title = "Sprachabbruch und verspätete Signale",
            rules = SPEECH_RULES,
            scenario = """
                Neue Director-/Reducer-Instanz. t=2000 ms: onRuntime(speaking=true), speechStarted("satz-a","Hallo").
                t=2010: wordRange("satz-a",0,5). t=2100: audioLevel("satz-a",0.8), dann Pose: mouth_before = jawOpen>0.
                t=2120: speechEnded("satz-a",interrupted=true).
                t=2130: verspätet audioLevel("satz-a",1.0) und wordRange("satz-a",0,5), dann Pose.
                mouth_after = jawOpen>0; energy_after = speakEnergy>0. Nenne außerdem viseme_after und state_after.
                Es folgt kein weiterer onRuntime-Aufruf.
            """.trimIndent(),
            expected = linkedMapOf(
                "mouth_before" to (before.jawOpen > 0f).toString(),
                "mouth_after" to (after.jawOpen > 0f).toString(),
                "energy_after" to (after.speakEnergy > 0f).toString(),
                "viseme_after" to after.viseme.name, "state_after" to after.state.name,
            ),
            explanation = "Sprachende entfernt die aktive Utterance und leert den Mundzustand. Späte Signale " +
                "dieser ID werden verworfen. Der Runtime-Zustand ändert sich erst mit einem passenden onRuntime-Aufruf.",
        )
    }

    private fun gazeExpiry(): ChiefBotPracticeExercise {
        val trace = Trace(3_000)
        val accepted = trace.director.onObservedGaze(GazeTarget(.4f, -.3f), 60, 0)
        val atLimit = trace.pose(3_750).gazeObserved
        val afterLimit = trace.pose(3_751).gazeObserved
        trace.now = 3_800
        val weakAccepted = trace.director.onObservedGaze(GazeTarget(.4f, -.3f), 59, 0)
        return exercise(
            id = "iris-gaze-expiry-v1", title = "Frische und Konfidenz des Blicksignals",
            rules = "Eine neue Instanz beginnt in IDLE. Beobachteter Blick ist nur in IDLE/LISTENING/SPEAKING erlaubt. " +
                "Konfidenz muss einschließlich der Grenzen in 60..100 liegen. Das Alter muss in 0..750 ms liegen. " +
                "pose(t) berechnet das Alter erneut; abgelaufene Beobachtungen verschwinden auch ohne Callback.",
            scenario = """
                Neue Director-/Reducer-Instanz im Grundzustand. Grenze: OBSERVED_GAZE_TTL_MS=750, MIN_GAZE_CONFIDENCE=60.
                t=3000 ms: accepted = onObservedGaze(GazeTarget(0.4,-0.3),confidence=60,ageMillis=0).
                Ohne neue Beobachtung: pose(3750).gazeObserved ergibt observed_at_limit; pose(3751).gazeObserved ergibt observed_after_limit.
                t=3800: weak_accepted = onObservedGaze(GazeTarget(0.4,-0.3),confidence=59,ageMillis=0).
            """.trimIndent(),
            expected = linkedMapOf(
                "accepted" to accepted.toString(), "observed_at_limit" to atLimit.toString(),
                "observed_after_limit" to afterLimit.toString(), "weak_accepted" to weakAccepted.toString(),
            ),
            explanation = "Eine gültige Blickbeobachtung gilt einschließlich der Frischegrenze. Danach " +
                "läuft sie auch ohne neuen Callback ab. Konfidenz unter der Mindestgrenze wird abgewiesen.",
        )
    }

    private fun cameraRevocation(): ChiefBotPracticeExercise {
        var now = 4_000L
        val policy = AvatarCameraPolicy { now }
        val token = checkNotNull(policy.start(true, true, true, true))
        val face = CameraFaceBox(650, 450, 750, 550, 90)
        val geometry = CameraFrameGeometry(0, 0, 1_000, 1_000, 0, 0)
        val before = policy.acceptFrame(token, listOf(face), geometry, now) != null
        now = 4_100
        val gateStillOpen = policy.updateGate(token, ownerEnabled = true, foreground = true, permission = false)
        now = 4_200
        val lateAccepted = policy.acceptFrame(token, listOf(face), geometry, now) != null
        return exercise(
            id = "iris-camera-revocation-v1", title = "Widerruf und alter Kameracallback",
            rules = "start benötigt alle vier Bedingungen und erzeugt ein neues Sitzungstoken. Ein gültiger, " +
                "frischer Frame mit aktuellem Token liefert eine Probe und setzt TRACKING. updateGate prüft der Reihe nach: " +
                "!ownerEnabled→OFF, !foreground→NOT_FOREGROUND, !permission→PERMISSION_REQUIRED. Trifft eine Regel zu, " +
                "werden Token und Probe gelöscht und false zurückgegeben. Ein ungültiges Token wird von acceptFrame abgewiesen.",
            scenario = """
                Nur die reine AvatarCameraPolicy, keine Kameraaufnahme. Neue Instanz mit kontrollierter Uhr.
                t=4000 ms: token = start(ownerEnabled=true,foreground=true,permission=true,hardwareSupported=true).
                acceptFrame(token,[CameraFaceBox(650,450,750,550,90)],CameraFrameGeometry(0,0,1000,1000,0,0),4000).
                before_accepted bedeutet: dieser Frame liefert eine Probe ungleich null.
                t=4100: gate_still_open = updateGate(token,true,true,false).
                t=4200: derselbe Frame mit demselben token und Aufnahmezeit 4200. late_accepted bedeutet: Probe ungleich null.
                Nenne danach state und sample_present = latest()!=null.
            """.trimIndent(),
            expected = linkedMapOf(
                "before_accepted" to before.toString(), "gate_still_open" to gateStillOpen.toString(),
                "late_accepted" to lateAccepted.toString(), "state" to policy.state.name,
                "sample_present" to (policy.latest() != null).toString(),
            ),
            explanation = "Der Widerruf beendet die aktuelle Policy-Generation und entfernt ihre Probe. " +
                "Ein späterer Callback mit dem alten Token kann sie nicht wiederherstellen.",
        )
    }

    private fun replacementSpeech(): ChiefBotPracticeExercise {
        val trace = Trace(5_000)
        trace.director.onRuntime(AvatarRuntimeSignals(speaking = true))
        trace.director.speechStarted("alt-7", "Alt")
        trace.now = 5_020
        trace.director.speechStarted("neu-9", "Neu")
        trace.now = 5_030
        trace.director.audioLevel("neu-9", .75f)
        val eventsBefore = trace.events.size
        trace.now = 5_040
        trace.director.speechEnded("alt-7", interrupted = true)
        trace.director.audioLevel("alt-7", 1f)
        trace.director.wordRange("alt-7", 0, 3)
        val oldEventsEmitted = trace.events.size != eventsBefore
        val pose = trace.pose()
        return exercise(
            id = "iris-replacement-transfer-v1", title = "Transfer: alte ID während neuer Sprache",
            rules = SPEECH_RULES,
            scenario = """
                Neue Director-/Reducer-Instanz. t=5000 ms: onRuntime(speaking=true), speechStarted("alt-7","Alt").
                t=5020: speechStarted("neu-9","Neu"). t=5030: audioLevel("neu-9",0.75).
                Zähle ab jetzt die am Sink eintreffenden Events.
                t=5040: speechEnded("alt-7",interrupted=true), audioLevel("alt-7",1.0), wordRange("alt-7",0,3).
                old_events_emitted bedeutet: mindestens einer dieser drei Aufrufe erzeugt ein Event am Sink.
                Lies danach die Pose: mouth_open = jawOpen>0; energy_active = speakEnergy>0; außerdem state.
            """.trimIndent(),
            expected = linkedMapOf(
                "old_events_emitted" to oldEventsEmitted.toString(), "mouth_open" to (pose.jawOpen > 0f).toString(),
                "energy_active" to (pose.speakEnergy > 0f).toString(), "state" to pose.state.name,
            ),
            explanation = "Nur die aktuelle Utterance darf Sprachereignisse erzeugen. Ein verspätetes Ende " +
                "einer ersetzten ID beendet den neuen Sprachvorgang nicht.", transfer = true,
        )
    }

    private fun taskGazePriority(): ChiefBotPracticeExercise {
        val trace = Trace(6_000)
        val before = trace.director.onObservedGaze(GazeTarget(-.6f, .2f), 100, 0)
        trace.now = 6_020
        trace.director.onRuntime(AvatarRuntimeSignals(busy = true, learning = true))
        trace.now = 6_030
        val during = trace.director.onObservedGaze(GazeTarget(.8f, .6f), 100, 0)
        val pose = trace.pose()
        return exercise(
            id = "iris-task-gaze-transfer-v1", title = "Transfer: Aufgabe vor beobachtetem Blick",
            rules = "Eine neue Instanz beginnt in IDLE. Beobachteter Blick ist nur in IDLE/LISTENING/SPEAKING " +
                "bei Konfidenz 60..100 und Alter 0..750 ms erlaubt. Der Wechsel in andere Zustände löscht ihn. " +
                "Ohne höher priorisierte Flags ergibt busy&&learning den Zustand LEARNING, Ausdruck FOCUSED und " +
                "Aufgabenblick READING_DOWN. Ein in diesem Zustand abgewiesener Blick überschreibt den Aufgabenblick nicht.",
            scenario = """
                Neue Director-/Reducer-Instanz im Grundzustand.
                t=6000 ms: accepted_before = onObservedGaze(GazeTarget(-0.6,0.2),confidence=100,ageMillis=0).
                t=6020: onRuntime(busy=true,learning=true), alle anderen Flags false.
                t=6030: accepted_during_task = onObservedGaze(GazeTarget(0.8,0.6),confidence=100,ageMillis=0).
                Lies die Pose: gaze_observed, state, expression und task_gaze.
                task_gaze bedeutet: die Pose verwendet den zustandsabhängigen Aufgabenblick statt einer der Beobachtungen.
            """.trimIndent(),
            expected = linkedMapOf(
                "accepted_before" to before.toString(), "accepted_during_task" to during.toString(),
                "gaze_observed" to pose.gazeObserved.toString(), "state" to pose.state.name,
                "expression" to pose.expression.name,
                "task_gaze" to (pose.gazeX == GazeTarget.READING_DOWN.x && pose.gazeY == GazeTarget.READING_DOWN.y).toString(),
            ),
            explanation = "Ein Aufgabenwechsel hebt einen zuvor beobachteten Blick auf. Hohe Konfidenz " +
                "ersetzt nicht die Zustandsprüfung; der Aufgabenblick bleibt maßgeblich.", transfer = true,
        )
    }

    private fun exercise(id: String, title: String, rules: String, scenario: String, expected: Map<String, String>,
                         explanation: String, transfer: Boolean = false): ChiefBotPracticeExercise {
        val schema = expected.keys.joinToString(separator = ",", prefix = "{", postfix = "}") { "\"$it\":\"\"" }
        val prompt = "Avatar-Logikübung für Iris. Beurteile die genannten Aufrufe nach den vorhandenen Continuum-Verträgen.\n" +
            "VERTRAG\n" + rules + "\nEREIGNISSE\n" + scenario +
            "\nAntworte ausschließlich mit einem JSON-Objekt mit genau den folgenden Schlüsseln. " +
            "Ersetze die leeren Werte durch Strings: Enum-Namen in GROSSBUCHSTABEN; Wahrheitswerte als \"true\" oder \"false\". " +
            "Keine weiteren Felder, Erläuterungen oder Codeblöcke.\nANTWORTSCHEMA\n" + schema
        check(prompt.toByteArray(Charsets.UTF_8).size < 1_800)
        return ChiefBotPracticeExercise(id, "bot-iris", title, prompt, expected, explanation, transfer)
    }

    private const val SPEECH_RULES = "Runtime-Sprechen heißt SPEAKING, sofern kein höher priorisiertes Flag aktiv ist. " +
        "speechStarted ersetzt die aktive ID und leert den Mundzustand. Audio/Wortbereich/Ende wirken nur für diese ID. " +
        "Audio: energy+=(clamp(level,0,1)-energy)*0.4; jaw=clamp((energy-0.025)*1.5,0,1), solange Audio höchstens 250 ms alt ist. " +
        "speechEnded löscht aktive ID, Energie und Visemes; Ruhevisem ist SIL. Sprachcallbacks ändern den Runtime-Zustand nicht."

    private class Trace(var now: Long) {
        val reducer = AvatarPoseReducer()
        val events = ArrayList<AvatarEvent>()
        val director = AvatarDirector(AvatarSink { event -> events += event; reducer.apply(event) }) { now }
        fun pose(atMillis: Long = now): AvatarPose {
            now = atMillis
            return reducer.pose(atMillis)
        }
    }
}
