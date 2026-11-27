# Daddy Home

Daddy Home is InfoSET's visible account, capability, controlled-workspace, and activity-audit boundary.
Authentication identifies an execution account. A capability grant decides what that actor may do
inside the home. The two are deliberately separate.

## GitHub account boundary

The dedicated GitHub device flow projects a stable numeric account ID, login, display name, avatar,
and granted OAuth scopes. The full credential remains a single Android Keystore AES-GCM ciphertext
under `noBackupFilesDir/github-account/credential.enc`.

The credential is never projected into Settings JSON, Room, whole-house backup, Android cloud
backup, Activity Journal, audit detail, request logs, a provider prompt, or a repository. Uninstall
and device migration require a new sign-in.

The account screen exposes loading, unavailable, disconnected, authorizing, device-code, connected,
and failed states. Connected accounts can log out or reconnect. The registered OAuth App is
`InfoSET Daddy Home`; its public client ID is compiled from `gradle.properties`. Device Flow is
enabled and no client secret is generated or embedded.

The account surface can install the official remote GitHub MCP endpoint into the mature MCP and
Context Composer path. Its Settings entry contains only the endpoint, enabled tools, and display
metadata. The transport resolves the dedicated device-flow credential in memory when it connects;
logout removes that runtime header and forces the GitHub MCP session to reconnect unauthenticated.
Every official GitHub MCP tool call is rejected unless its target is a repository binding for the
same stable account. The call guard checks tool execution, journal, repository, branch-write,
Build/PR, and direct-main grants before dispatch, then records the source, target, result, and wait.

## ChatGPT / Codex account boundary

Daddy Home also exposes an official Sign in with ChatGPT entry. It uses OpenAI dynamic agent client
registration with PKCE, a stable per-install host identity, the public `api.openai.com/v1/models`
and `api.openai.com/v1/responses` endpoints, `store=false`, streaming, and encrypted reasoning-item
replay. It does not call ChatGPT private backend endpoints.

The complete access and refresh credentials are stored only as a Keystore AES-GCM ciphertext under
`noBackupFilesDir/provider-accounts/credentials.enc`. Settings JSON retains a redacted signed-in
marker, account display metadata, the issued public OAuth client ID, and host ID. Existing plaintext
account credentials are migrated one way into the vault on startup. Logout removes the vault entry;
the public registration metadata remains available for reauthorization. Provider settings, Room,
whole-house backup, Android cloud backup, logs, prompts, and repositories never receive bearer or
refresh tokens.

## Actor capability grants

The stable first actor is `daddy`. Each grant records:

- capability;
- global or repository scope;
- grantor and execution source;
- creation, optional expiry, revocation time, and revoker.

Grant and revoke actions append an audit event. Repository read, branch write, Build/PR, and direct
main are separate grants. Direct main is never implied by ordinary repository write.

## Controlled GitHub workspace

A repository binding records the authenticated account ID, `owner/repository`, optional branch and
path prefix, optional InfoSET Workspace, and whether branch writes are enabled. Operations validate
both the binding and current capability grant before reading a file, writing a branch, copying a
file into a Workspace, or running a Workspace command.

The official GitHub MCP is Daddy's model-facing repository tool surface. The bounded home screen
uses a narrow bootstrap bridge for repository verification and explicit file transfer into an
InfoSET Workspace; it cannot bypass the same repository bindings and capability checks. Both paths
use the token only to build an in-memory Authorization header. Audit events record the actor,
capability, source account reference, target repository/path, time, and outcome. They do not record
headers, token values, file bodies, or command output.

The visible audit feed translates action, capability, tool, and result codes into Chinese labels;
the stored codes remain stable for queries, restore, and forward-compatible auditing.

## Activity Journal

Activity Journal is an inspectable derived layer, not Conversation Archive. Each row can record:

- actor, category, title, summary, start, and end;
- execution source, account, organization, and billing source;
- tool names, result, failure reason, outputs, and related object;
- active duration and waiting duration as separate values.

Day, week, and month summaries count completed active duration. Running rows, background waits, API
polling, and synchronous tool waits never become active time by subtraction or guesswork. Instant
actions remain valid events with zero active duration.

The whole-house Room snapshot includes grants, audit events, repository bindings, and journal rows.
It does not include the GitHub credential blob. Nullable event, output, organization, billing, and
failure fields remain null across restore.

## OpenAI API organization placeholder

Activity execution identity already has nullable account, organization, and billing-source fields so
future Provider calls can distinguish an OpenAI Personal API Organization, Project, and
`subscription-covered` / `api-billed` / `local` execution. This node does not add a second API key
store and does not change current Provider credential or backup behavior.
