package com.recall.app.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume

/**
 * Google authorisation for Drive, using Google Identity Services on the device — no backend.
 *
 * Only the `drive.appdata` scope is requested: the app can see its own hidden backup folder
 * and nothing else in the user's Drive.
 */
object DriveAuth {
    private const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    sealed interface Outcome {
        data class Token(val value: String) : Outcome
        /** The user has to pick an account or grant access; launch [intent] from an Activity. */
        data class NeedsConsent(val intent: PendingIntent) : Outcome
        data class Failed(val message: String) : Outcome
    }

    suspend fun authorize(context: Context): Outcome = suspendCancellableCoroutine { cont ->
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()
        Identity.getAuthorizationClient(context).authorize(request)
            .addOnSuccessListener { result ->
                val token = result.accessToken
                val pending = result.pendingIntent
                cont.resume(
                    when {
                        result.hasResolution() && pending != null -> Outcome.NeedsConsent(pending)
                        token != null -> Outcome.Token(token)
                        else -> Outcome.Failed("Google didn't return access to Drive.")
                    }
                )
            }
            .addOnFailureListener { cont.resume(Outcome.Failed(explain(it))) }
    }

    /** Reads the result of the consent screen launched from [Outcome.NeedsConsent]. */
    fun fromConsent(context: Context, data: Intent?): Outcome = try {
        val token = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken
        if (token != null) Outcome.Token(token) else Outcome.Failed("Google Drive wasn't connected.")
    } catch (e: ApiException) {
        Outcome.Failed(explain(e))
    }

    private fun explain(e: Exception): String {
        val code = (e as? ApiException)?.statusCode
        return when (code) {
            CommonStatusCodes.DEVELOPER_ERROR ->
                "Drive backup isn't configured for this build yet (OAuth client missing). See the README."
            CommonStatusCodes.NETWORK_ERROR -> "No connection. Try again when you're online."
            CommonStatusCodes.CANCELED -> "Google Drive wasn't connected."
            else -> e.message?.takeIf { it.isNotBlank() } ?: "Couldn't reach Google Drive."
        }
    }
}

data class DriveFile(val id: String, val name: String, val createdAt: Long, val size: Long)

/** The few Drive v3 REST calls backup needs, over plain HTTPS. */
class DriveClient(private val token: String) {

    suspend fun list(prefix: String): List<DriveFile> = withContext(Dispatchers.IO) {
        val query = URLEncoder.encode("name contains '$prefix' and trashed = false", "UTF-8")
        val fields = URLEncoder.encode("files(id,name,createdTime,size)", "UTF-8")
        val body = request(
            "GET",
            "$API/files?spaces=appDataFolder&orderBy=createdTime%20desc&pageSize=50&q=$query&fields=$fields",
        )
        val files = JSONObject(body).optJSONArray("files") ?: return@withContext emptyList()
        List(files.length()) {
            val f = files.getJSONObject(it)
            DriveFile(
                id = f.getString("id"),
                name = f.optString("name"),
                createdAt = runCatching { java.time.Instant.parse(f.optString("createdTime")).toEpochMilli() }.getOrDefault(0),
                size = f.optString("size").toLongOrNull() ?: 0,
            )
        }
    }

    suspend fun upload(name: String, content: String): Unit = withContext(Dispatchers.IO) {
        val boundary = "recall-${System.nanoTime()}"
        val metadata = JSONObject().put("name", name).put("parents", org.json.JSONArray().put("appDataFolder"))
        val body = buildString {
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata).append("\r\n")
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(content).append("\r\n")
            append("--$boundary--")
        }
        request("POST", "$UPLOAD/files?uploadType=multipart", body, "multipart/related; boundary=$boundary")
    }

    suspend fun download(id: String): String = withContext(Dispatchers.IO) {
        request("GET", "$API/files/$id?alt=media")
    }

    suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
        request("DELETE", "$API/files/$id")
    }

    private fun request(method: String, url: String, body: String? = null, contentType: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            if (code in 200..299) {
                return conn.inputStream?.use { it.readBytes().decodeToString() }.orEmpty()
            }
            val detail = conn.errorStream?.use { it.readBytes().decodeToString() }.orEmpty()
            val message = runCatching { JSONObject(detail).getJSONObject("error").getString("message") }.getOrNull()
            throw BackupException(
                when (code) {
                    401 -> "Google Drive needs you to sign in again."
                    403 -> message ?: "Google Drive refused the request. Is the Drive API enabled for this app?"
                    else -> message ?: "Google Drive error ($code)."
                }
            )
        } catch (e: java.io.IOException) {
            throw BackupException("No connection. Try again when you're online.")
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    }
}
