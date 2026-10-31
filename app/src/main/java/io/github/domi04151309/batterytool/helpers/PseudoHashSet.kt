package io.github.domi04151309.batterytool.helpers

/**
 * Empty stand-in for the focused-app lookup when the check is disabled; `contains` must stay
 * `false`, otherwise every guard with `!focused.contains(...)` becomes dead code.
 */
class PseudoHashSet : Set<String> {
    override val size: Int
        get() = 0

    override fun isEmpty(): Boolean = true

    override fun iterator(): Iterator<String> = setOf<String>().iterator()

    override fun containsAll(elements: Collection<String>): Boolean = elements.isEmpty()

    override fun contains(element: String): Boolean = false
}
