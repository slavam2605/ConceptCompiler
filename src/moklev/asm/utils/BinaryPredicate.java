package moklev.asm.utils;

/**
 * @author Moklev Vyacheslav
 */
public interface BinaryPredicate<A, B> {
    boolean test(A a, B b);
}
