// "Create property 'foo' from usage" "true"
// ERROR: Property must be initialized or be abstract

class A<T>(val n: T) {
    var foo: String

}

fun test() {
    A(1).foo = "1"
}
