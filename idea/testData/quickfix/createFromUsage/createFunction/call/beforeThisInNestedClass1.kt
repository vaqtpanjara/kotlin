// "Create function 'foo' from usage" "true"

class A<T>(val n: T) {
    inner class B<U>(val m: U) {
        fun test(): A<Int> {
            return this.<caret>foo(2, "2")
        }
    }
}