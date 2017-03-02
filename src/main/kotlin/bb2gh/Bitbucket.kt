package bb2gh

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.HttpClients
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
import java.util.*

class BitbucketConnector(val credentials: Credentials) {

    private val apiHost = HttpHost("api.bitbucket.org", 443, "https")

    private val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule())
        registerModule(BitbucketModule())
    }

    fun findRepositories(owner: String): List<RepositoryInfo> {
        val startUrl = "https://api.bitbucket.org/2.0/repositories/$owner"

        val clientBuilder = HttpClients.custom()
        val httpContext = HttpClientContext()
        clientBuilder.setDefaultCredentialsProvider(BasicCredentialsProvider().apply {
            setCredentials(AuthScope.ANY, UsernamePasswordCredentials(credentials.login, credentials.password))
        })

        // Create a local context that forces usage of preemptive basic authentication
        httpContext.authCache = BasicAuthCache().apply {
            put(apiHost, BasicScheme())
        }

        clientBuilder.build().use { client ->
            val repositories = ArrayList<RepositoryInfo>()
            var url: String? = startUrl
            while (url != null) {
                client.execute(HttpGet(url), httpContext).use { response ->
                    val (repos, next) = objectMapper.readEntity<RepositoriesResponse>(response.entity)
                    repositories += repos
                    url = next
                }
            }
            return repositories
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class RepositoriesResponse(val values: List<RepositoryInfo>, val next: String?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Link(val href: String, val name: String?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RepositoryInfo(
        val name: String,
        @JsonFormat(with = arrayOf(ACCEPT_SINGLE_VALUE_AS_ARRAY)) val links: Map<String, List<Link>>,
        @JsonProperty("is_private") val isPrivate: Boolean,
        val description: String) {

    val cloneLinks: List<Link>
        get() = links["clone"] ?: emptyList()

    val httpsCloneUrl: String?
        get() = cloneUrlForProtocol("https")

    fun cloneUrlForProtocol(protocol: String): String? =
            cloneLinks.map(Link::href).find { it.startsWith("$protocol:") }
}

/**
 * Jackson module that provides deserializers for types in Bitbucket's API.
 */
private class BitbucketModule : SimpleModule() {
    init {
        addDeserializer(Instant::class.java, object : JsonDeserializer<Instant>() {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Instant? {
                val str = p.text.trim()
                return if (str.any())
                    ZonedDateTime.parse(str, ISO_OFFSET_DATE_TIME).toInstant()
                else
                    null
            }
        })
    }
}
