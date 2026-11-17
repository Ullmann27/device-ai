package at.heinz.deviceai.cognitive

import java.util.Locale

/** Observed composer, never send permission. */
internal object LiveComposeSemantics {
    private val labels = setOf("nachricht", "message")

    fun isLabel(label: String): Boolean = label.lowercase(Locale.ROOT) in labels

    fun isField(node: ActionNode): Boolean = isField(node.label, node.viewId, node.visible, node.enabled,
        node.editable, node.password)

    fun isField(node: ClickNode): Boolean = isField(node.label, node.viewId, node.visible, node.enabled,
        node.editable, node.sensitive)

    private fun isField(label: String, viewId: String?, visible: Boolean, enabled: Boolean,
                        editable: Boolean, sensitive: Boolean): Boolean =
        visible && enabled && editable && !sensitive && isLabel(label) &&
            !SendActionGuard.isSendLike(label, viewId) && !LiveSearchSemantics.isControl(label, viewId)
}
