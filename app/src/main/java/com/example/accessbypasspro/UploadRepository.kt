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
 * Backend expectations (based on your updated Node/Express code):
 * - file field name: "file"  (upload.single("file"))
 * - optional text field: "folderPath" like "test/sub1/sub2"
 *
 * Notes:
 * - Requires <uses-permission android:name="android.permission.INTERNET" /> in AndroidManifest.xml
 * - OkHttp dependency:
 *   implementation("com.squareup.okhttp3:okhttp:4.12.0")
 */
class UploadRepository(
    private val client: OkHttpClient = OkHttpClient()
) {

    /**
     * Upload a single image Uri.
     * If folderPath is provided, server will create/find nested folders and upload there.
     * If a file with the same name already exists in that folder, server will update it.
     */
    @Throws(IOException::class)
    suspend fun uploadImageUri(
        context: Context,
        uri: Uri,
        uploadUrl: String,                    // e.g. "https://example.com/upload"
        folderPath: String? = null,           // e.g. "test/sub1/sub2" or null/"" for root
        fileFieldName: String = "file",       // MUST match backend: upload.single("file")
        folderFieldName: String = "folderPath"// MUST match backend: req.body.folderPath
    ): String {
        val mime = context.contentResolver.getType(uri) ?: "image/*"
        val filename = guessFileName(context, uri)

        val fileBody = uriAsRequestBody(context, uri, mime)

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
        // text part first (optional)
        val cleanFolder = folderPath?.trim().orEmpty()
        if (cleanFolder.isNotEmpty()) {
            multipartBuilder.addFormDataPart(folderFieldName, cleanFolder)
        }

        // file part
        val multipart = multipartBuilder
            .addFormDataPart(fileFieldName, filename, fileBody)
            .build()

        val request = Request.Builder()
            .url(uploadUrl)
            .post(multipart)
            .build()

        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Upload failed: HTTP ${resp.code} $body")
            return body
        }
    }

    /**
     * Upload multiple image Uris (e.g., your first 5) to the same folderPath.
     */
    @Throws(IOException::class)
    suspend fun uploadImageUris(
        context: Context,
        uris: List<Uri>,
        uploadUrl: String,
        folderPath: String? = null,
        fileFieldName: String = "file",
        folderFieldName: String = "folderPath"
    ): List<String> {
        val results = ArrayList<String>(uris.size)
        for (uri in uris) {
            val resp = uploadImageUri(
                context = context,
                uri = uri,
                uploadUrl = uploadUrl,
                folderPath = folderPath,
                fileFieldName = fileFieldName,
                folderFieldName = folderFieldName
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