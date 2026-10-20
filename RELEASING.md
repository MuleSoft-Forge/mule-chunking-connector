# Releasing to Maven Central

This guide documents the process for releasing the mule-chunking-connector to Maven Central.

## Prerequisites

### 1. Java 17 Environment

**CRITICAL**: Always build with Java 17. Other versions (especially Java 24) will cause MUnit module resolution failures.

```bash
# Set JAVA_HOME to Temurin 17
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home

# Verify Java version
$JAVA_HOME/bin/java -version
# Should output: openjdk version "17.x.x"
```

### 2. GPG Key Configuration

You need a GPG key registered with Maven Central for artifact signing.

```bash
# List available keys
gpg --list-secret-keys --keyid-format=long

# Expected output should include MuleSoft Forge key:
# sec   rsa4096/12EC221A65D2DFA7 ...
```

### 3. Maven Settings

Your `~/.m2/settings.xml` must include:

- **Central server credentials** (username/password from https://central.sonatype.com)
- **Release profile** with GPG keyname configuration

Example profile:
```xml
<profile>
    <id>release</id>
    <properties>
        <gpg.keyname>12EC221A65D2DFA7</gpg.keyname>
    </properties>
</profile>
```

### 4. Repository Access

Ensure you have publishing rights to the `com.mulesoftforge` groupId on Maven Central.

## Release Process

### Step 1: Update Version

Edit `pom.xml` to remove `-SNAPSHOT` suffix:

```xml
<!-- Change from -->
<version>0.2.0.7-SNAPSHOT</version>

<!-- To -->
<version>0.2.0</version>
```

Commit this change:
```bash
git add pom.xml
git commit -m "Prepare release 0.2.0"
```

### Step 2: Run Full Test Suite

Verify all tests pass before releasing:

```bash
mvn clean test
```

**Expected Output:**
```
Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
- 19 JUnit tests (Java unit tests)
- 1 MUnit test (Mule integration test)
```

### Step 3: Build and Sign Artifacts

Build with the release profile to sign artifacts:

```bash
mvn clean verify -Prelease
```

**What happens:**
1. Compiles and packages the connector
2. Runs all tests (unit + MUnit)
3. Generates sources and javadoc jars
4. Signs all artifacts with GPG (you'll be prompted for passphrase)

**Expected Artifacts (7 files signed):**
- `mule-chunking-connector-{version}.jar`
- `mule-chunking-connector-{version}-mule-plugin.jar`
- `mule-chunking-connector-{version}-sources.jar`
- `mule-chunking-connector-{version}-javadoc.jar`
- `mule-chunking-connector-{version}.pom`
- Plus `.asc` signature files for each

**Success Output:**
```
[INFO] BUILD SUCCESS
[INFO] Total time: X.XXX s
```

### Step 4: Deploy to Maven Central Portal

Deploy signed artifacts to the Central Portal:

```bash
mvn deploy -Prelease
```

**Expected Output:**
```
[INFO] Deployment succeeded. Deployment information:
[INFO]    Publishing URL: https://central.sonatype.com/publishing/deployments/{deployment-id}
[INFO]    Deployment Name: mule-chunking-connector-{version}
[INFO]    Deployment State: VALIDATED
```

**Save the deployment ID** from the output (e.g., `3595bf2b-b4f4-42e0-a677-b6191c665253`).

### Step 5: Publish on Central Portal

Since `autoPublish` is set to `false` in pom.xml, you must manually publish:

1. Open https://central.sonatype.com/publishing/deployments
2. Log in with your Central credentials
3. Find your deployment by ID or name
4. Review the uploaded artifacts
5. Click **"Publish"** to release to Maven Central

**Note:** It may take 15-30 minutes for artifacts to appear in Maven Central search after publishing.

### Step 6: Tag the Release

Create an annotated git tag for the release:

```bash
git tag -a v0.2.0 -m "Release 0.2.0

- Streaming strategies: non-repeatable, in-memory, file-store, sliding-window
- Constant memory: Non-repeatable uses O(chunkSize) only
- Bounded repeatability: Sliding-window with configurable cache
- DataWeave zombie cursor workaround for foreach bug
- Scatter-gather support with multiple foreach loops
- Comprehensive error handling with CACHE_OVERFLOW type
- All tests passing (19 unit + 1 MUnit)"
```

Push the tag to GitHub:

```bash
git push origin v0.2.0
```

### Step 7: Update to Next Development Version

Immediately after releasing, update pom.xml to the next SNAPSHOT version:

```xml
<version>0.2.1-SNAPSHOT</version>
```

Commit and push:

```bash
git add pom.xml
git commit -m "Prepare for next development iteration"
git push origin main
```

## Verification

After publishing to Maven Central, verify the release:

1. **Maven Central Search**: https://central.sonatype.com/artifact/com.mulesoftforge/mule-chunking-connector
2. **Maven Repository**: https://repo1.maven.org/maven2/com/mulesoftforge/mule-chunking-connector/

Test installation in a Mule project:

```xml
<dependency>
    <groupId>com.mulesoftforge</groupId>
    <artifactId>mule-chunking-connector</artifactId>
    <version>0.2.0</version>
    <classifier>mule-plugin</classifier>
</dependency>
```

## Troubleshooting

### Java Version Issues

**Symptom:**
```
java.lang.module.FindException: Module org.apache.logging.log4j not found
```

**Solution:**
Always use Java 17. Set `JAVA_HOME` before building:
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
```

### MUnit Test Failures

**Symptom:**
```
cvc-complex-type.3.2.2: Attribute 'config-ref' is not allowed
```

**Solution:**
MUnit tests should use operations directly without config-ref:
```xml
<!-- Correct -->
<chunking:read-chunked chunkSize="20"/>

<!-- Incorrect -->
<chunking:read-chunked config-ref="Chunking_Config" chunkSize="20"/>
```

### GPG Signing Issues

**Symptom:** Build hangs or fails during GPG signing

**Solutions:**
1. Ensure GPG agent is running: `gpg-agent --daemon`
2. Test GPG signing manually: `echo "test" | gpg --clearsign`
3. Allow pinentry prompt to complete without interruption
4. If using terminal pinentry, ensure `GPG_TTY` is set: `export GPG_TTY=$(tty)`

### Deployment Validation Errors

If deployment fails validation on Central Portal:

1. Check all required metadata is present in pom.xml:
   - name, description, url
   - licenses
   - developers
   - scm (connection, developerConnection, url)

2. Verify all artifacts are properly signed (`.asc` files present)

3. Ensure groupId matches your Central namespace (`com.mulesoftforge`)

## Release Checklist

Before releasing:

- [ ] All tests passing (19 unit + 1 MUnit)
- [ ] Java 17 environment configured
- [ ] Version updated from SNAPSHOT to release version
- [ ] README.md updated with new version examples
- [ ] CHANGELOG.md updated (if applicable)
- [ ] GPG key available and configured

During release:

- [ ] `mvn clean verify -Prelease` succeeds
- [ ] All 7 artifact files signed (.asc files present)
- [ ] `mvn deploy -Prelease` succeeds with VALIDATED status
- [ ] Manual publish completed on Central Portal
- [ ] Git tag created and pushed
- [ ] Version bumped to next SNAPSHOT

After release:

- [ ] Artifacts visible on Maven Central (wait 15-30 minutes)
- [ ] Test installation in a Mule project
- [ ] Update documentation site (if applicable)
- [ ] Announce release (GitHub releases, community channels)

## Release Profile Configuration

The release profile in `pom.xml` includes:

```xml
<profile>
    <id>release</id>
    <build>
        <plugins>
            <!-- GPG Signing -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-gpg-plugin</artifactId>
                <version>3.2.8</version>
                <executions>
                    <execution>
                        <id>sign-artifacts</id>
                        <phase>verify</phase>
                        <goals>
                            <goal>sign</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>

            <!-- Central Publishing -->
            <plugin>
                <groupId>org.sonatype.central</groupId>
                <artifactId>central-publishing-maven-plugin</artifactId>
                <version>0.8.0</version>
                <extensions>true</extensions>
                <configuration>
                    <publishingServerId>central</publishingServerId>
                    <autoPublish>false</autoPublish>
                    <deploymentName>${project.artifactId}-${project.version}</deploymentName>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

**Note:** `autoPublish: false` requires manual approval on Central Portal, allowing final review before public release.

## Important Reminders

1. **Never modify these without consulting the planning agent:**
   - Parent POM version (`mule-modules-parent`)
   - SDK version (`mule-sdk-api`)
   - MUnit plugin versions
   - Java compiler target version

2. **Always use Java 17** - Other versions will cause module resolution failures

3. **Manual publish required** - autoPublish is disabled for safety

4. **Central Portal delay** - Allow 15-30 minutes for artifacts to sync to Maven Central after publishing

5. **GPG passphrase** - Keep GPG key passphrase secure; it's required for every release

## References

- **Maven Central Portal**: https://central.sonatype.com
- **Central Publishing Documentation**: https://central.sonatype.org/publish/publish-portal-maven/
- **MuleSoft Forge**: https://github.com/MuleSoft-Forge/mule-chunking-connector
- **Maven Central Artifacts**: https://central.sonatype.com/artifact/com.mulesoftforge/mule-chunking-connector
