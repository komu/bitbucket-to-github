package bb2gh

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity

operator fun <T> Array<T>.component6(): T = this[5]

fun HttpResponse.readBodyAsText() = entity.content.use { it.reader().readText() }

inline fun <reified T : Any> ObjectMapper.readEntity(entity: HttpEntity) = entity.content.use { readValue<T>(it) }

fun HttpEntityEnclosingRequestBase.setJsonEntity(json: String) {
    entity = StringEntity(json, ContentType.APPLICATION_JSON)
}
