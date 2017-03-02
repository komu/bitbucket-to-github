# bitbucket-to-github

Imports all repositories of given Bitbucket user to GitHub.

Private repositories are imported as private, public repositories as public. The repository description
and version history are imported, all other information (wikis, issues, etc) are not.

Since GitHub's import supports Mercurial in addition to Git, the source repositories may contain Mercurial repositories.

## Usage

```
./gradlew build
java -jar build/libs/bitbucket-to-github-all.jar BB-OWNER BB-LOGIN BB-PASSWORD GH-OWNER GH-LOGIN GH-PASSWORD
```
