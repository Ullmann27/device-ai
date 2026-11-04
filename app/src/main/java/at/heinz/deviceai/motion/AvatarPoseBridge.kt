package at.heinz.deviceai.motion

import at.heinz.deviceai.avatar.AvatarPose
import at.heinz.deviceai.avatar.Viseme

/**
 * Thin bridge for renderers that consume [AvatarPose] (AvatarMorphMapper composes JawOpen/visemes itself):
 * copies the mouth fields of the last [LipSyncFaceSource.weightsAt] frame into [pose]. Allocates one pose,
 * so call it once per frame at most; the allocation-free path is [FaceWeightsSource.weightsAt].
 */
fun LipSyncFaceSource.applyTo(pose: AvatarPose): AvatarPose = pose.copy(
    jawOpen = jawOpen,
    viseme = Viseme.entries[dominantViseme],
    visemeWeight = dominantVisemeWeight,
    speakEnergy = speakEnergy,
)
