// "Create function 'contains' from usage" "true"

class A<T>(val n: T)

fun test() {
    when {
        2 <caret>in A(1) -> {}
    }
}