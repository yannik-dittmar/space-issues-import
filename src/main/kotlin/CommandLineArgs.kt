package com.jetbrains.space.import

import com.jetbrains.space.import.common.ExternalProjectProperty
import com.jetbrains.space.import.common.ImportSource
import com.jetbrains.space.import.common.ProjectPropertyType
import com.jetbrains.space.import.common.defaultProjectPropertyType
import com.jetbrains.space.import.github.GitHubAuthorization
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.SystemExitException
import com.xenomachina.argparser.default
import space.jetbrains.api.runtime.types.ImportExistsPolicy
import space.jetbrains.api.runtime.types.ImportMissingPolicy
import space.jetbrains.api.runtime.types.ProjectIdentifier
import java.util.*

data class CommandLineArgs(private val parser: ArgParser) {

    // Jira

    val jiraServer by parser.storing(
        "--jiraServer",
        help = "The URL of the Jira server that you want to import issues from."
    ).default(null)

    val jiraQuery by parser.storing(
        "--jiraQuery",
        help = "An optional JQL query that selects the Jira issues you want to import."
    ).default(null)

    val jiraUser by parser.storing(
        "--jiraUser",
        help = "An optional user name to use to login to Jira."
    ).default(null)

    val jiraPassword by parser.storing(
        "--jiraApiToken", "--jiraPassword",
        help = "An optional API token to use to login to Jira."
    ).default(null)

    // YouTrack

    val youtrackServer by parser.storing(
        "--youtrackServer",
        help = "The URL of the YouTrack server that you want to import issues from."
    ).default(null)

    val youtrackQuery by parser.storing(
        "--youtrackQuery",
        help = "A query that selects the YouTrack issues that you want to import."
    ).default(null)

    val youtrackToken by parser.storing(
        "--youtrackToken",
        help = "An optional permanent token that grants access to the YouTrack server for a specific user account. " +
            "If not specified, issue data is retrieved according to the access rights that are available to the guest account."
    ).default(null)

    // Notion

    val notionDatabaseId by parser.storing(
        "--notionDatabaseId",
        help = "The ID of the Notion database that you want to import issues from."
    ).default(null)

    val notionToken by parser.storing(
        "--notionToken",
        help = "A token to access the Notion API."
    ).default(null)

    val notionAssigneeProperty by parser.storing(
        "--notionAssigneeProperty",
        help = "The name or ID of a property in Notion which will be mapped to the Space issue assignee. For example, name${mappingSeparator}Title or id${mappingSeparator}uuid-uuid-uuid.",
        transform = { parseNotionProperty(this, "notionAssigneeProperty") }
    ).default(null)

    val notionStatusProperty by parser.storing(
        "--notionStatusProperty",
        help = "The name or ID of a property in Notion which will be mapped to the Space issue status. For example, name${mappingSeparator}Title or id${mappingSeparator}uuid-uuid-uuid.",
        transform = { parseNotionProperty(this, "notionStatusProperty") }
    ).default(null)

    val notionTagProperty by parser.storing(
        "--notionTagProperty",
        help = "The name or ID of a property in Notion which will be mapped to the Space issue tag. For example, name${mappingSeparator}Title or id${mappingSeparator}uuid-uuid-uuid.",
        transform = { parseNotionProperty(this, "notionTagProperty") }
    ).default(null)

    val notionAssigneePropertyMappingType by parser.storing(
        "--notionAssigneePropertyMappingType",
        help = "id, name, or email. Default: name. For --assignee command, what to map on the Notion side, " +
            "e.g. '--assignee uuid-uuid${mappingSeparator}john.doe' for 'id' vs '--assignee John Doe${mappingSeparator}john.doe' for 'name'. " +
            "This argument will be used in case the property corresponds to a person, select or multiselect. " +
            "Plain value (name) will be used otherwise (for email, phone number, text, title, etc.). " +
            "Note that email will only be used in case the property corresponds to a person (including created & edited by). ",
        transform = { parseNotionPropertyMappingType(this) }
    ).default(defaultProjectPropertyType)

    val notionStatusPropertyMappingType by parser.storing(
        "--notionStatusPropertyMappingType",
        help = "id or name. Default: name. For --status command, what to map on the Notion side, " +
            "e.g. '--tag uuid-uuid${mappingSeparator}To Do' for 'id' vs '--tag To Do${mappingSeparator}To Do' for 'name'.",
        transform = { parseNotionPropertyMappingType(this) }
    ).default(defaultProjectPropertyType)

    val notionTagPropertyMappingType by parser.storing(
        "--notionTagPropertyMappingType",
        help = "id or name. Default: name. For --tag command, what to map on the Notion side, " +
            "e.g. '--tag uuid-uuid${mappingSeparator}Android' for 'id' vs '--tag Android${mappingSeparator}Android' for 'name'.",
        transform = { parseNotionPropertyMappingType(this) }
    ).default(defaultProjectPropertyType)

    val tagPropertyMappingType by parser.storing(
        "--tagPropertyMappingType",
        help = "[supported only for Notion] id or name. Default: name. Please add 'View project data' permission to your Space integration if you use 'name'. " +
                "For --tag command, what to map on the Space side, " +
                "e.g. '--tag Android${mappingSeparator}space-tag-id' for 'id' vs '--tag Android${mappingSeparator}Android' for 'name'.",
        transform = { parseNotionPropertyMappingType(this) }
    ).default(defaultProjectPropertyType)

    val notionQuery by parser.storing(
        "--notionQuery",
        help = "JSON string which will be used as the request body to databases/:id/query. By default, all the cards from the board are exported."
    ).default(null)

    // GitHub

    val gitHubAuthorization by parser.storing(
        "--gitHubAuthorization",
        help = "An optional API authorization string to use to login to GitHub. Mandatory for private repositories. Can be $authorizationTypeMessage",
        transform = { parseGitHubAuthorization(this) }
    ).default(GitHubAuthorization.None)

    val gitHubRepositoryOwner by parser.storing(
        "--gitHubRepositoryOwner",
        help = "The owner of the repository that you want to import issues from."
    ).default(null)

    val gitHubRepository by parser.storing(
        "--gitHubRepository",
        help = "The repository that you want to import issues from."
    ).default(null)

    // Space

    val spaceServer by parser.storing(
        "--spaceServer",
        help = "The URL of the Space instance that you want to import into."
    )

    val spaceToken by parser.storing(
        "--spaceToken",
        help = "A personal token for a Space account that has the Import Issues permission."
    )

    val spaceProject by parser.storing(
        "--spaceProject",
        help = "The key or ID of a project in Space into which you want to import issues. For example, key${mappingSeparator}ABC or id${mappingSeparator}42.",
        transform = {
            val (identifierType, identifier) = parseMapping(this)
            when (identifierType.lowercase(Locale.getDefault())) {
                "key" -> ProjectIdentifier.Key(identifier)
                "id" -> ProjectIdentifier.Id(identifier)
                else -> throw SystemExitException("only key${mappingSeparator}value or id${mappingSeparator}value are allowed for --spaceProject as identifier", 2)
            }
        }
    )

    // Space /import API arguments

    val importSource by parser.storing(
        "--importSource",
        help = "The name of the import source. Default: External.",
        transform = { ImportSource.values().find { it.name.equals(this, ignoreCase = true) } ?: ImportSource.External }
    ).default(ImportSource.External)

    val dryRun by parser.flagging(
        "--dryRun",
        help = "Runs the import script without actually creating issues."
    )

    val onExistsPolicy by parser.mapping(
        "--updateExistingIssues" to ImportExistsPolicy.Update,
        "--skipExistingIssues" to ImportExistsPolicy.Skip,
        help = "Tells Space how to process issues when their external IDs matches previously imported issues in Space. Default: skipExistingIssues."
    ).default(ImportExistsPolicy.Skip)

    val statusMissingPolicy by parser.mapping(
        "--replaceMissingStatus" to ImportMissingPolicy.ReplaceWithDefault,
        "--skipMissingStatus" to ImportMissingPolicy.Skip,
        help = "Tells Space how to handle issues when the value for the status field does not exist. `replaceMissingStatus` sets it to the first unresolved status. Default: `skipMissingStatus`."
    ).default(ImportMissingPolicy.Skip)

    val assigneeMissingPolicy by parser.mapping(
        "--replaceMissingAssignee" to ImportMissingPolicy.ReplaceWithDefault,
        "--skipMissingAssignee" to ImportMissingPolicy.Skip,
        help = "Tells Space how to handle issues when the value for the assignee field does not exist. `replaceMissingAssignee` sets it to `unassigned`. Default: `skipMissingAssignee`."
    ).default(ImportMissingPolicy.Skip)

    // add-ons

    val assigneeMapping by parser.adding(
        "-a", "--assignee",
        help = "Maps the assignee in the external system to a member profile in Space. For example, leonid.tolstoy${mappingSeparator}leo.tolstoy.",
        transform = { parseMapping(this) }
    ).default(emptyList())

    val statusMapping by parser.adding(
        "-s", "--status",
        help = "Maps an issue status in the external system to an issue status in Space. For example, in-progress${mappingSeparator}In Progress.",
        transform = { parseMapping(this) }
    ).default(emptyList())

    val tagMapping by parser.adding(
        "-t", "--tag",
        help = "[supported only for Notion] Maps the tag in the external system to a tag in Space. " +
                "For example, external-tag${mappingSeparator}space-tag-id. " +
                "Please remember to specify the tag property for the external system.",
        transform = { parseMapping(this) }
    ).default(emptyList())

    val batchSize by parser.storing(
        "--batchSize",
        help = "The size of a batch with issues being sent to Space per request. Default: 50.",
        transform = { toInt() }
    ).default(50)

    val debug by parser.flagging(
        "--debug",
        help = "Runs the import script in debug mode."
    )

    companion object {
        private const val mappingSeparator = "::"
        private const val authorizationTypeMessage = "either 'oauth$mappingSeparator<oauth_token>', for OAuth (e.g. personal token); " +
                "'oauth_with_login$mappingSeparator<user_id_OR_org_name>$mappingSeparator<oauth_token>', for OAuth with user id or organization name; " +
                "'jwt$mappingSeparator<jwt_token>', for JSON Web Token; " +
                "or 'ait$mappingSeparator<ait_token>', for App Installation Token."

        fun parseMapping(
            arg: String,
            separator: String = mappingSeparator,
            canContainMultipleSeparators: Boolean = false
        ): Pair<String, String> {
            val mapping = arg.split(separator)
            if ((!canContainMultipleSeparators && mapping.size != 2) || mapping.size < 2)
                throw SystemExitException("mapping format is wrong for argument: $arg", 2)

            return mapping.first() to mapping.subList(1, mapping.size).joinToString(mappingSeparator)
        }

        fun parseGitHubAuthorization(arg: String): GitHubAuthorization {
            val (authType, token) = parseMapping(arg, canContainMultipleSeparators = true)
            return when (authType.lowercase(Locale.getDefault())) {
                "oauth" -> GitHubAuthorization.OAuth(token)
                "oauth_with_login" -> parseMapping(token, canContainMultipleSeparators = true).let { (login, token) ->
                    GitHubAuthorization.OAuth(token, login)
                }
                "jwt" -> GitHubAuthorization.Jwt(token)
                "ait" -> GitHubAuthorization.AppInstallationToken(token)
                else -> throw SystemExitException("--gitHubAuthorization can only be $authorizationTypeMessage", 2)
            }
        }

        fun parseNotionProperty(
            arg: String,
            propertyName: String
        ): ExternalProjectProperty {
            val (identifierType, identifier) = parseMapping(arg)
            return when (identifierType.lowercase(Locale.getDefault())) {
                "name" -> ExternalProjectProperty.Name(identifier)
                "id" -> ExternalProjectProperty.Id(identifier)
                else -> throw SystemExitException("only name${mappingSeparator}value or id${mappingSeparator}value are allowed for --$propertyName as identifier",
                    2)
            }
        }

        fun parseNotionPropertyMappingType(arg: String)
            = ProjectPropertyType.values().find { it.name.equals(arg, ignoreCase = true) }
                ?: defaultProjectPropertyType
    }
}
