package com.comicink.data.source

sealed class JsSourceException(override val message: String) : Exception(message) {
    class SourceNotFound(val key: String) : JsSourceException("Source not found: $key")
    class ScriptError(override val message: String, val stack: String? = null) : JsSourceException("Script error: $message")
    class NetworkError(override val message: String) : JsSourceException("Network error: $message")
    class ParseError(override val message: String) : JsSourceException("Parse error: $message")
    class LoginRequired(override val message: String = "Login required") : JsSourceException(message)
}