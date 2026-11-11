# Releasing to Maven Central

How a version of the mule-chunking-connector is released to Maven Central. The examples use 1.0.0-beta1.

Signing, deploying and publishing need the maintainer's signing key and Maven Central account. Everything else
here, including the full test run, can be done by any contributor.

## The build baseline

These are fixed for the connector and are not changed as part of other work:

| | Version |
|---|---|
| Java, to build and as the compiler target | 17 |
| Parent POM, `org.mule.extensions:mule-modules-parent` | 1.9.6 |
| Mule SDK API, `org.mule.sdk:mule-sdk-api` | 0.11.4 |
| MUnit | 3.4.0 |
| MUnit extensions Maven plugin | 1.5.0 |

The minimum Mule runtime, 4.9.6, follows from the parent POM.

To propose a change to any of them, open an issue on this repository that says which value should change, why,
and what you built and tested it with. A change to the baseline is made in a commit of its own, never alongside
other work, and is released only with the full test run below passing.

## Versions

- **Between releases** the build file carries the next version to be released with `-SNAPSHOT`, such as
  `1.0.0-SNAPSHOT`. A snapshot is never published.
- **A release** carries a plain version such as `1.0.0` or `1.0.1`.
- **A preview** carries a qualifier, such as `1.0.0-beta1`, and is released to Maven Central like any other
  version so that it can be tried as a user would. Maven orders `1.0.0-beta1` before `1.0.0`. The README and the
  changelog say plainly that it is a preview, which version it previews, and that the final version may differ;
  the changelog gives it a dated entry of its own.
- **The tag** for a version is `v` followed by the version: `v0.2.0`, `v1.0.0-beta1`.

## Prerequisites

### Java 17

Always build with Java 17. Other versions, Java 24 in particular, make the MUnit tests fail to start.

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home   # or wherever your JDK 17 is
$JAVA_HOME/bin/java -version   # openjdk version "17.x.x"
```

### For signing and publishing only

- A GPG key registered with Maven Central for signing.
- In your `~/.m2/settings.xml`: a server with the id `central` holding your Central Portal token
  (https://central.sonatype.com), and a profile with the id `release` that sets `gpg.keyname` to your key:

  ```xml
  <profile>
      <id>release</id>
      <properties>
          <gpg.keyname>YOUR_KEY_ID</gpg.keyname>
      </properties>
  </profile>
  ```

- Publishing rights to the `com.mulesoftforge` namespace on Maven Central.

## Release process

The order matters: the version is published on Maven Central first, and only then is anything pushed or tagged,
so that the repository never names a release that does not exist.

### Step 1: Prepare the release on a local `main`

1. Set the version in `pom.xml`, from `1.0.0-SNAPSHOT` to `1.0.0-beta1`.
2. Set the same version in the two places that tell a user which version to use: the dependency snippet under
   Installation in `README.md`, and `chunking.connector.version` in `examples/chunking-sample-app/pom.xml`.
3. Make sure the documentation is complete. A version is not released until:
   - `README.md` describes every feature of the release;
   - `CHANGELOG.md` has an entry for the version, with the release date, recording every change and every change
     an application must make;
   - the public API documentation is complete: every public type and member in the `api` packages has a
     documentation comment with `@since`, and every element and setting the tools show has a description. See
     [Checking the API documentation](#checking-the-api-documentation).
4. Commit on your local `main`. Do not push yet.

### Step 2: Run the full test suite and the sample application

```bash
mvn clean install
```

**Expected:** 56 unit tests, then 82 MUnit tests on Mule 4.9.6 in nine suites, all passing, and the connector
installed in your local Maven repository under the release version:

```
Tests run: 56, Failures: 0, Errors: 0, Skipped: 0
...
MUnit Run Summary - Product: MULE_EE, Version: 4.9.6
```

The suites are `chunking-strategy` 11, `errors` 6, `invalid-values` 17, `read-chunked` 1, `readme-examples` 12,
`release` 6, `sliding-window` 9, `streaming-strategies` 15 and `streaming-strategy-element` 5.

Then build the sample application against the version just installed:

```bash
cd examples/chunking-sample-app
mvn clean verify
```

**Expected:** 16 MUnit tests passing, and `BUILD SUCCESS`.

### Step 3: Build and sign the artifacts

```bash
mvn clean verify -Prelease
```

This runs every test again, builds the sources and test-sources jars, checks that neither the version nor any
dependency is a snapshot, and signs every artifact with GPG (you are asked for the key's passphrase).

**Expected:** `BUILD SUCCESS`, with a `.asc` signature made for each of the seven artifacts:

- `mule-chunking-connector-1.0.0-beta1.pom`
- `mule-chunking-connector-1.0.0-beta1.jar`
- `mule-chunking-connector-1.0.0-beta1-mule-plugin.jar`
- `mule-chunking-connector-1.0.0-beta1-javadoc.jar`
- `mule-chunking-connector-1.0.0-beta1-sources.jar`
- `mule-chunking-connector-1.0.0-beta1-test-sources.jar`
- `mule-chunking-connector-1.0.0-beta1-extension-model-4.9.6.json`

### Step 4: Deploy to the Maven Central Portal

```bash
mvn deploy -Prelease
```

**Expected:**

```
[INFO] Deployment succeeded. Deployment information:
[INFO]    Publishing URL: https://central.sonatype.com/publishing/deployments/{deployment-id}
[INFO]    Deployment Name: mule-chunking-connector-1.0.0-beta1
[INFO]    Deployment State: VALIDATED
```

Nothing is public yet: the release profile sets `autoPublish` to `false`.

### Step 5: Publish on the Central Portal

1. Open https://central.sonatype.com/publishing/deployments and sign in.
2. Find the deployment `mule-chunking-connector-1.0.0-beta1`, in state Validated.
3. Review its files: the seven above, each with its signature and checksums.
4. Click **Publish**. The state moves to Publishing and then Published.

### Step 6: Confirm the version is on Maven Central

It can take 15 to 30 minutes after publishing. The version is there when
https://repo1.maven.org/maven2/com/mulesoftforge/mule-chunking-connector/1.0.0-beta1/ lists
`mule-chunking-connector-1.0.0-beta1-mule-plugin.jar`, and
https://repo1.maven.org/maven2/com/mulesoftforge/mule-chunking-connector/maven-metadata.xml lists `1.0.0-beta1`.

Do not go on until it is there.

### Step 7: Push `main` and tag the release

```bash
git push origin main
git tag -a v1.0.0-beta1 -m "Release 1.0.0-beta1

A preview of 1.0.0: chunking by delimiter, streaming strategies that do what their settings say, and invalid
values that fail. See CHANGELOG.md for every change from 0.2.0."
git push origin v1.0.0-beta1
```

The tag message summarises the release for someone reading the repository's tags.

### Step 8: Set the next development version

Set the version in `pom.xml` to the next version to be released, with `-SNAPSHOT`:

- after a preview, the version it previews: after `1.0.0-beta1`, `1.0.0-SNAPSHOT`;
- after a release, the next patch version: after `1.0.0`, `1.0.1-SNAPSHOT`.

Only the build file changes. The README and the sample application keep naming the released version, which anyone
can download.

```bash
git add pom.xml
git commit -m "Prepare for next development iteration"
git push origin main
```

## Checking the API documentation

- **Documentation comments.** Generate the javadoc for the `api` packages with strict checking, using the
  connector's classpath. `javadoc -Xdoclint:all -public` over `com.mulesoftforge.mule.extension.chunking.api` and
  its subpackages should report no warning. Then check that every public type and member there has `@since`;
  methods marked `@Override`, such as `toString`, take their documentation from the method they override.
- **What the tools show.** The build packages `META-INF/chunking-extension-descriptions.xml` inside the
  `-mule-plugin.jar`. Every element in it with a `name` (the extension, the configuration, the operation, its
  parameters, and every strategy and setting) should have a non-empty description.

## Troubleshooting

### Java version

**Symptom:**
```
java.lang.module.FindException: Module org.apache.logging.log4j not found
```

**Solution:** build with Java 17. Set `JAVA_HOME` as above.

### MUnit tests and `config-ref`

**Symptom:**
```
cvc-complex-type.3.2.2: Attribute 'config-ref' is not allowed
```

**Solution:** `read-chunked` takes no configuration. Use it without `config-ref`:
```xml
<!-- Correct -->
<chunking:read-chunked/>

<!-- Incorrect -->
<chunking:read-chunked config-ref="Chunking_Config"/>
```

### GPG signing

**Symptom:** the build hangs or fails while signing.

**Solutions:**
1. Make sure the GPG agent is running: `gpg-agent --daemon`
2. Test signing by hand: `echo "test" | gpg --clearsign`
3. Let the passphrase prompt complete without interruption.
4. With a terminal passphrase prompt, set `GPG_TTY`: `export GPG_TTY=$(tty)`

### Deployment validation errors

If the Central Portal rejects the deployment:

1. Check that `pom.xml` has the required metadata: name, description, url, licenses, developers, and scm
   (connection, developerConnection, url).
2. Check that every artifact is signed (a `.asc` beside each).
3. Check that the groupId is `com.mulesoftforge`.

## Release checklist

Before releasing:

- [ ] Java 17
- [ ] Version set in `pom.xml`, the README's dependency snippet and the sample application
- [ ] README describes the release
- [ ] CHANGELOG has a dated entry for the version
- [ ] API documentation complete
- [ ] 56 unit and 82 MUnit tests passing; the sample's 16 passing against the installed version
- [ ] Release commit on the local `main`, not pushed

Releasing:

- [ ] `mvn clean verify -Prelease` succeeds, seven artifacts signed
- [ ] `mvn deploy -Prelease` succeeds with state VALIDATED
- [ ] Published on the Central Portal
- [ ] Version visible on Maven Central
- [ ] `main` pushed; tag created and pushed
- [ ] Next development version committed and pushed

After releasing:

- [ ] Add the version as a dependency to a Mule project and build it
- [ ] Update the documentation site, for a release that is not a preview
- [ ] Announce the release

## The release profile

The `release` profile in `pom.xml`:

- signs every artifact with `maven-gpg-plugin` in the `verify` phase;
- skips the standard deploy, and uploads instead with `central-publishing-maven-plugin` to the server `central`,
  as a deployment named `${project.artifactId}-${project.version}`, with `autoPublish` set to `false` so that a
  person reviews and publishes it;
- with the parent POM's profile of the same name, builds the sources and test-sources jars and checks that the
  version and the dependencies are not snapshots and that every plugin's version is declared.

## References

- Maven Central Portal: https://central.sonatype.com
- Publishing with Maven: https://central.sonatype.org/publish/publish-portal-maven/
- Repository: https://github.com/MuleSoft-Forge/mule-chunking-connector
- The connector on Maven Central: https://central.sonatype.com/artifact/com.mulesoftforge/mule-chunking-connector
