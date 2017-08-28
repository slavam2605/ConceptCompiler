package moklev.asm.utils

/**
 * @author Moklev Vyacheslav
 */

/**
 * Superclass for optional preferences while coloring a conflict graph
 */
sealed class ColoringPreference

/**
 * Try to assign variables [node] and [other] to the same color
 */
class Coalesce(val node: String, val other: String) : ColoringPreference()

/**
 * Try to assign variable [node] to the color [register]
 */
class Target(val node: String, val register: InRegister) : ColoringPreference()

/**
 * Try to avoid assigning the color [register] to any variable
 */
class Avoid(val register: InRegister) : ColoringPreference()

/**
 * Must color [node] to the color [register] (precolored node)
 */
class Predefined(val node: String, val register: InRegister) : ColoringPreference()