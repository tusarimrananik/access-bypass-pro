package com.example.accessbypasspro

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException

/**
 * Uploads images (Uri) to a backend endpoint using multipart/form-data.
 *
 * Notes:
 * - Requires <uses-permission android:name="android.permission.INTERNET" /> in AndroidManifest.xml
 * - Requires OkHttp dependency in app/build.gradle:
 *   implementation("com.squareup.okhttp3:okhttp:4.12.0")
 *
 * IMPORTANT:
 * - Call suspend functions from a coroutine (e.g., with LaunchedEffect, viewModelScope, etc.)
 */
class UploadRepository(
    private val client: OkHttpClient = OkHttpClient()
) {

    /**
     * Upload a single image Uri.
     * @return Response body as String (or throws IOException on failure)
     */
    @Throws(IOException::class)
    suspend fun uploadImageUri(
        context: Context,
        uri: Uri,
        uploadUrl: String,              // e.g. "https://example.com/upload"
        formFieldName: String = "image" // e.g. "image" or "file" depending on backend
    ): String {
        val mime = context.contentResolver.getType(uri) ?: "image/*"
        val filename = guessFileName(context, uri)

        val fileBody = uriAsRequestBody(context, uri, mime)

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(formFieldName, filename, fileBody)
            .build()

        val request = Request.Builder()
            .url(uploadUrl)
            .post(multipart)
            .build()

        // OkHttp's execute() is blocking, but we're in a suspend function so you must call this
        // from Dispatchers.IO (or a ViewModel scope configured for IO).
        client.newCall(request).execute().use { resp ->
            val body = resp.body.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Upload failed: HTTP ${resp.code} $body")
            return body
        }
    }

    /**
     * Upload multiple image Uris (e.g., your first 5).
     * @return List of responses (one per upload). Throws if any upload fails.
     */
    @Throws(IOException::class)
    suspend fun uploadImageUris(
        context: Context,
        uris: List<Uri>,
        uploadUrl: String,
        formFieldName: String = "image"
    ): List<String> {
        val results = ArrayList<String>(uris.size)
        for (uri in uris) {
            val resp = uploadImageUri(
                context = context,
                uri = uri,
                uploadUrl = uploadUrl,
                formFieldName = formFieldName
            )
            results.add(resp)
        }
        return results
    }

    private fun guessFileName(context: Context, uri: Uri): String {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1 && c.moveToFirst()) {
                    val name = c.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        return "image.jpg"
    }

    private fun uriAsRequestBody(
        context: Context,
        uri: Uri,
        contentType: String?
    ): RequestBody {
        return object : RequestBody() {
            override fun contentType() = contentType?.toMediaTypeOrNull()

            override fun writeTo(sink: BufferedSink) {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Cannot open InputStream for $uri" }
                    sink.writeAll(input.source())
                }
            }
        }
    }
}