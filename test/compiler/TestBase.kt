package compiler

import org.junit.Assert
import org.junit.Before

/**
 * @author Moklev Vyacheslav
 */
open class TestBase {
    fun fail(message: String): Nothing {
        @Suppress("CAST_NEVER_SUCCEEDS")
        Assert.fail(message) as Nothing
    }
}