package moklev.asm.utils

/**
 * @author Moklev Vyacheslav
 */
object StaticUtils {
    private var labelCount = 0
    
    fun nextLabel(): String {
        val label = "tempL$labelCount"
        labelCount += 1
        return label
    }
}