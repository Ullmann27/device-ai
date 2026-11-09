#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cat > "$work/ContractProbe.kt" <<'KOT'
package at.heinz.deviceai.cognitive

enum class InternetAccessMode { OFF, ASK, ALWAYS_ON }
fun main() {
    var passed = 0
    fun checkCase(value: Boolean, message: String) { check(value) { message }; passed++ }
    val battery = OwnerPreviousBatteryIntent
    val mode = TurnScopedInternetMode
    val fresh = "Miss den Akku nochmal und vergleiche mit der letzten Messung."
    checkCase(battery.isFreshComparisonRequest(fresh), "fresh battery request")
    checkCase(!battery.isPreviousReadingRequest(fresh), "fresh request is not historical only")
    checkCase(!battery.isFreshComparisonRequest("Prüfe nicht den Akku; vergleiche nur die letzte Messung."), "negated fresh check")
    checkCase(battery.isPreviousReadingRequest("Wie viel Prozent Akku laut deiner gerade ausgeführten Geräteprüfung? Keine neue Messung."), "historical answer")
    checkCase(battery.isPreviousReadingRequest("Akku bitte nicht erneut messen."), "forbid re-measurement")
    checkCase(!mode.discardAfterConsentChange(true, 0, false, false), "pure local inference")
    checkCase(mode.discardAfterConsentChange(true, 0, false, true), "revoke online research context")
    checkCase(mode.discardAfterConsentChange(true, 1, false, false), "revoke web tool call")
    checkCase(mode.discardAfterConsentChange(true, 0, true, false), "revoke web session")
    checkCase(!mode.discardAfterConsentChange(false, 1, true, true), "unchanged consent")
    checkCase(mode.effective(InternetAccessMode.ALWAYS_ON, InternetAccessMode.OFF) == InternetAccessMode.OFF, "offline veto")
    var called = false
    try { mode.withCurrentConsentRevision(5, { 6 }) { called = true } } catch (_: IllegalStateException) { }
    checkCase(!called, "stale Web execution veto")
    println("CODE52_EXACT_SOURCE_TARGETED_KOTLIN: $passed PASS, 0 FAIL")
}
KOT
kotlinc \
    "$root/app/src/main/java/at/heinz/deviceai/cognitive/OwnerPreviousBatteryIntent.kt" \
    "$root/app/src/main/java/at/heinz/deviceai/cognitive/TurnScopedInternetMode.kt" \
    "$work/ContractProbe.kt" -include-runtime -d "$work/contract.jar"
java -jar "$work/contract.jar"
