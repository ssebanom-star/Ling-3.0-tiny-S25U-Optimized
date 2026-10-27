package net.classcardauto.mobile

/**
 * kotlin.Result is an inline class, so its members are name-mangled and cannot be called from Java.
 * The decompiled sources use these helpers instead; every argument is a boxed kotlin.Result.
 */
object ResultCompat {
    @JvmStatic
    fun success(value: Any?): Any = Result.success(value)

    @JvmStatic
    fun failure(error: Throwable): Any = Result.failure<Any?>(error)

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun isSuccess(result: Any?): Boolean = (result as Result<Any?>).isSuccess

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun valueOrThrow(result: Any?): Any? = (result as Result<Any?>).getOrThrow()

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun valueOrNull(result: Any?): Any? = (result as Result<Any?>).getOrNull()

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun errorOrNull(result: Any?): Throwable? = (result as Result<Any?>).exceptionOrNull()
}
