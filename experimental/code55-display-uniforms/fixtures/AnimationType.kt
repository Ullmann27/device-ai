package at.heinz.deviceai.cockpit

abstract class CockpitAnimationEvent {
    abstract val turnId: String
    abstract val atMillis: Long
    abstract class Extension(val kind: String) : CockpitAnimationEvent()
}
