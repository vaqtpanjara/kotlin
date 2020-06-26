// IGNORE_BACKEND: JS_IR
// IGNORE_BACKEND: JS_IR_ES6
// TODO: muted automatically, investigate should it be ran for JS or not
// IGNORE_BACKEND: JS

// WITH_RUNTIME

fun box(): String {
    assert(Long.MAX_VALUE !in Int.MIN_VALUE..Int.MAX_VALUE)
    assert(Int.MAX_VALUE in Long.MIN_VALUE..Long.MAX_VALUE)
    assert(Double.MAX_VALUE !in Float.MIN_VALUE..Float.MAX_VALUE)
    assert(Float.MIN_VALUE in 0.0..1.0)
    assert(2.0 !in 1.0f..0.0f)
    assert(1L in 0..2)

    return "OK"
}
