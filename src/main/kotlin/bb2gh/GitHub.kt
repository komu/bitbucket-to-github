package bb2gh

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.methods.HttpHead
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients

class GitHubConnector(val credentials: Credentials) {

    private val apiHost = HttpHost("api.github.com", 443, "https")

    private val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule())
    }

    fun importRepositoryToGitHub(target: GitHubRepository, cloneUrl: String, bbCredentials: Credentials): GitHubImportResponse {
        val importRequest = GitHubImportRequest(cloneUrl, bbCredentials.login, bbCredentials.password)

        withAuthenticatedClient { client, httpContext ->
            val request = HttpPut("https://api.github.com/repos/${target.owner}/${target.name}/import").apply {
                addHeader("Accept", "application/vnd.github.barred-rock-preview")
                setJsonEntity(objectMapper.writeValueAsString(importRequest))
            }

            client.execute(request, httpContext).use { response ->
                if (response.statusLine.statusCode == 201)
                    return objectMapper.readEntity<GitHubImportResponse>(response.entity)
                else
                    error("import failed: ${response.statusLine}: ${response.readBodyAsText()}")
            }
        }
    }

    fun repositoryExists(target: GitHubRepository): Boolean {
        withAuthenticatedClient { client, httpContext ->
            val request = HttpHead("https://api.github.com/repos/${target.owner}/${target.name}").apply {
                addHeader("Accept", "application/vnd.github.v3+json")
            }

            client.execute(request, httpContext).use { response ->
                return response.statusLine.statusCode == 200
            }
        }
    }

    fun createRepository(target: GitHubRepository, description: String, private: Boolean) {
        val createRequest = GitHubCreateRequest(target.name, description = description, private = private)

        withAuthenticatedClient { client, httpContext ->
            val request = HttpPost("https://api.github.com/orgs/${target.owner}/repos").apply {
                addHeader("Accept", "application/vnd.github.v3+json")
                setJsonEntity(objectMapper.writeValueAsString(createRequest))
            }

            client.execute(request, httpContext).use { response ->
                if (response.statusLine.statusCode != 201)
                    error("import failed: ${response.statusLine}: ${response.readBodyAsText()}")
            }
        }
    }

    private inline fun <T> withAuthenticatedClient(callback: (CloseableHttpClient, HttpClientContext) -> T): T {
        val clientBuilder = HttpClients.custom()
        val httpContext = HttpClientContext()

        clientBuilder.setDefaultCredentialsProvider(BasicCredentialsProvider().apply {
            setCredentials(AuthScope.ANY, UsernamePasswordCredentials(credentials.login, credentials.password))
        })

        // Create a local context that forces usage of preemptive basic authentication
        httpContext.authCache = BasicAuthCache().apply {
            put(apiHost, BasicScheme())
        }

        return clientBuilder.build().use { client -> callback(client, httpContext) }
    }
}

data class GitHubRepository(val owner: String, val name: String) {
    override fun toString() = "$owner/$name"
}

data class GitHubImportRequest(
        @JsonProperty("vcs_url") val vcsUrl: String,
        @JsonProperty("vcs_username") val vcsUsername: String,
        @JsonProperty("vcs_password") val vcsPassword: String)

data class GitHubCreateRequest(
        val name: String,
        val description: String,
        val private: Boolean)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubImportResponse(val status: String)
