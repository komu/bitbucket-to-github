package bb2gh

class Importer(val bitbucketOwner: String,
               val bitbucketCredentials: Credentials,
               val githubOwner: String,
               githubCredentials: Credentials) {

    val bitbucket = BitbucketConnector(bitbucketCredentials)
    val github = GitHubConnector(githubCredentials)

    fun importAllRepositories() {
        val repositories = bitbucket.findRepositories(bitbucketOwner)

        println("importing ${repositories.size} Bitbucket repositories owned by '$bitbucketOwner'...")
        for (repo in repositories) {
            importBitbucketRepositoryToGithub(repo)
        }
    }

    fun importBitbucketRepositoryToGithub(repo: RepositoryInfo) {
        val target = GitHubRepository(githubOwner, repo.name.toLowerCase().replace(' ', '-'))

        if (github.repositoryExists(target)) {
            println("skip ${repo.name} (already exists)")
            return
        }

        println("import ${repo.name}")

        val cloneUrl = repo.httpsCloneUrl ?: error("no https clone url for repo ${repo.name}")

        println("  - creating empty repository $target")
        github.createRepository(target, description = repo.description, private = repo.isPrivate)

        println("  - importing repository from $cloneUrl")
        github.importRepositoryToGitHub(target, cloneUrl, bitbucketCredentials)
    }
}

fun main(args: Array<String>) {
    if (args.size != 6) {
        System.err.println("usage: bitbucket-to-github BB-OWNER BB-LOGIN BB-PASSWORD GH-OWNER GH-LOGIN GH-PASSWORD")
        System.exit(1)
    }

    val (bbOwner, bbLogin, bbPassword, ghOwner, ghLogin, ghPassword) = args

    val bbCredentials = Credentials(bbLogin, bbPassword)
    val ghCredentials = Credentials(ghLogin, ghPassword)

    val importer = Importer(bbOwner, bbCredentials, ghOwner, ghCredentials)
    importer.importAllRepositories()
}


