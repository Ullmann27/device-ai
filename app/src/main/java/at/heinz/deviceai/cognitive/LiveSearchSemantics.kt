package at.heinz.deviceai.cognitive

import java.util.Locale

/** Observed roles, never input approval. */
internal object LiveSearchSemantics {
    private val labels = setOf("suchen", "suche", "search", "suchen …", "search…", "search...")
    private val fieldIds = setOf("search", "search_src_text", "search_input", "search_text", "search_bar")
    private val buttonIds = fieldIds + setOf("menuitem_search", "menu_search")

    fun isSearchLabel(label: String) = label.lowercase(Locale.ROOT) in labels

    fun isControl(label: String, viewId: String?): Boolean =
        !SendActionGuard.isSendLike(label, viewId) && (isSearchLabel(label) || id(viewId) in buttonIds)

    fun isField(node: ActionNode) = isField(node.label, node.viewId, node.visible, node.enabled,
        node.editable, node.password)

    fun isField(node: ClickNode) = isField(node.label, node.viewId, node.visible, node.enabled,
        node.editable, node.sensitive)

    private fun isField(label: String, viewId: String?, visible: Boolean, enabled: Boolean,
                        editable: Boolean, sensitive: Boolean): Boolean =
        visible && enabled && editable && !sensitive && !SendActionGuard.isSendLike(label, viewId) &&
            (isSearchLabel(label) || id(viewId) in fieldIds)

    private fun id(value: String?) = value?.substringAfterLast('/')?.lowercase(Locale.ROOT)
}
