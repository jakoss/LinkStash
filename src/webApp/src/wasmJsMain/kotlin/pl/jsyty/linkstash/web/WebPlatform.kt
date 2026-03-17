@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package pl.jsyty.linkstash.web

import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlin.JsFun
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsString
import kotlin.js.unsafeCast
import pl.jsyty.linkstash.contracts.client.ApiException
import pl.jsyty.linkstash.contracts.link.LinkDto

internal const val apiBaseUrlStorageKey = "linkstash.web.apiBaseUrl"
internal const val selectedSpaceStorageKey = "linkstash.web.selectedSpaceId"
internal const val sessionExpectedStorageKey = "linkstash.web.sessionExpected"
internal const val csrfHeaderName = "X-CSRF-Token"

internal suspend fun copyCurrentLinksToClipboard(links: List<LinkDto>) {
    val payload = links.joinToString(separator = "\n") { it.url }
    copyTextToClipboardJs(payload.toJsString()).await<JsAny?>()
}

internal suspend fun readClipboardTextOrNull(): String? {
    return runCatching {
        readTextFromClipboardJs().await<JsString>().toString().trim()
    }.getOrNull()?.takeIf { it.isNotEmpty() }
}

internal fun shouldUseManualPasteFlow(): Boolean {
    return shouldUseManualPasteFlowJs()
}

internal fun registerGlobalUrlPasteListener(onUrlPasted: (String) -> Unit): () -> Unit {
    val disposer = registerGlobalUrlPasteListenerJs { pastedUrl ->
        onUrlPasted(pastedUrl.toString())
    }
    return {
        disposeListenerJs(disposer)
    }
}

internal fun loadStoredApiBaseUrl(): String {
    return localStorage.getItem(apiBaseUrlStorageKey)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: suggestedApiBaseUrl()
}

internal fun storeApiBaseUrl(value: String) {
    localStorage.setItem(apiBaseUrlStorageKey, value)
}

internal fun loadStoredSelectedSpaceId(): String? {
    return localStorage.getItem(selectedSpaceStorageKey)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

internal fun loadStoredSessionExpected(): Boolean {
    return localStorage.getItem(sessionExpectedStorageKey) == "true"
}

internal fun storeSelectedSpaceId(spaceId: String?) {
    if (spaceId == null) {
        localStorage.removeItem(selectedSpaceStorageKey)
    } else {
        localStorage.setItem(selectedSpaceStorageKey, spaceId)
    }
}

internal fun storeSessionExpected(expected: Boolean) {
    if (expected) {
        localStorage.setItem(sessionExpectedStorageKey, "true")
    } else {
        localStorage.removeItem(sessionExpectedStorageKey)
    }
}

internal fun suggestedApiBaseUrl(): String {
    val protocol = window.location.protocol.ifBlank { "http:" }
    val host = window.location.hostname.ifBlank { "localhost" }
    val port = window.location.port

    if ((host == "localhost" || host == "127.0.0.1") && port == "8081") {
        return "$protocol//$host:8080"
    }

    return if (port.isBlank()) {
        "$protocol//$host"
    } else {
        "$protocol//$host:$port"
    }
}

internal fun String.requireNonBlank(message: String): String {
    return takeIf { it.isNotBlank() } ?: error(message)
}

internal fun String.isHttpUrl(): Boolean {
    val candidate = trim()
    return candidate.startsWith("http://") || candidate.startsWith("https://")
}

internal fun String.requireHttpUrl(): String {
    return trim().takeIf { it.isHttpUrl() }
        ?: error("URL must start with http:// or https://")
}

internal fun Throwable.humanMessage(): String {
    return when (this) {
        is ApiException -> error.message
        else -> message ?: this::class.simpleName ?: "Unexpected error"
    }
}

internal fun compactUrlLabel(url: String): String {
    val withoutScheme = url
        .removePrefix("https://")
        .removePrefix("http://")
        .trim()

    val host = withoutScheme.substringBefore('/')
    val path = withoutScheme.substringAfter('/', missingDelimiterValue = "")
        .trim('/')

    return if (path.isBlank()) {
        host
    } else {
        "$host/${path.take(32)}"
    }
}

internal fun compactCreatedAt(createdAt: String): String {
    return createdAt.substringBefore('T').ifBlank { createdAt }
}

@JsFun(
    """
    (text) => {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        return navigator.clipboard.writeText(text);
      }
      const textarea = document.createElement('textarea');
      textarea.value = text;
      textarea.setAttribute('readonly', 'readonly');
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.focus();
      textarea.select();
      const copied = document.execCommand('copy');
      document.body.removeChild(textarea);
      return copied ? Promise.resolve() : Promise.reject(new Error('Clipboard copy failed'));
    }
    """
)
private external fun copyTextToClipboardJs(text: JsString): Promise<JsAny?>

@JsFun(
    """
    () => {
      if (navigator.clipboard && navigator.clipboard.readText) {
        return navigator.clipboard.readText();
      }
      return Promise.resolve('');
    }
    """
)
private external fun readTextFromClipboardJs(): Promise<JsString>

@JsFun(
    """
    () => {
      const userAgent = navigator.userAgent || '';
      const platform = navigator.platform || '';
      const maxTouchPoints = navigator.maxTouchPoints || 0;
      const isIPhoneOrIPad = /iPhone|iPad|iPod/.test(userAgent);
      const isIPadOS = platform === 'MacIntel' && maxTouchPoints > 1;
      const isFirefoxFamily = /Firefox|FxiOS|Zen/.test(userAgent);
      return isIPhoneOrIPad || isIPadOS || isFirefoxFamily;
    }
    """
)
private external fun shouldUseManualPasteFlowJs(): Boolean

@JsFun(
    """
    (callback) => {
      const isEditableTarget = (target) => {
        if (!target || !(target instanceof HTMLElement)) {
          return false;
        }
        if (target.isContentEditable) {
          return true;
        }
        const tagName = target.tagName;
        return tagName === 'INPUT' || tagName === 'TEXTAREA' || tagName === 'SELECT';
      };

      const handler = (event) => {
        if (event.defaultPrevented || isEditableTarget(event.target) || isEditableTarget(document.activeElement)) {
          return;
        }
        const pastedText = event.clipboardData?.getData('text/plain')?.trim();
        if (!pastedText || (!pastedText.startsWith('http://') && !pastedText.startsWith('https://'))) {
          return;
        }
        event.preventDefault();
        callback(pastedText);
      };

      document.addEventListener('paste', handler, true);
      return () => document.removeEventListener('paste', handler, true);
    }
    """
)
private external fun registerGlobalUrlPasteListenerJs(callback: (JsString) -> Unit): JsAny

@JsFun("(dispose) => dispose()")
private external fun disposeListenerJs(dispose: JsAny)
