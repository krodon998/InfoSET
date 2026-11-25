# InfoSET release signing

InfoSET release APKs must always use the same private signing key. Losing the key or its password
means future versions cannot update an installed official build.

## One-time setup on the owner Windows PC

Install JDK 21 and GitHub CLI, sign in with `gh auth login`, then run:

    Set-ExecutionPolicy -Scope Process Bypass
    .\scripts\setup-release-signing.ps1

The script:

1. creates `Documents\InfoSET-signing\infoset-release.p12`;
2. refuses to overwrite an existing key;
3. uploads the encrypted keystore and signing values to GitHub Actions secrets;
4. prints the signing certificate fingerprint for the release record.

If key creation succeeds but secret upload is interrupted, keep the key and rerun:

    .\scripts\setup-release-signing.ps1 -UseExisting

The keystore and password must be backed up separately. Neither belongs in Git, chat attachments,
release assets, or application resources.

## Local signed build

Add these values to untracked `local.properties`:

    storeFile=C:\\Users\\USER\\Documents\\InfoSET-signing\\infoset-release.p12
    storePassword=YOUR_PASSWORD
    keyAlias=infoset-release
    keyPassword=YOUR_PASSWORD

Then build:

    .\gradlew.bat assembleGithubRelease

## GitHub release

After the repository secrets exist, run the `Release InfoSET` workflow and provide a version tag
such as `v0.1.0`. The workflow builds the release APKs, verifies every signature, and publishes the
matching GitHub Release.
