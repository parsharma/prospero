/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.it.cli;

import org.apache.commons.io.FileUtils;
import org.jboss.galleon.util.ZipUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.it.ExecutionUtils;
import org.wildfly.prospero.it.commonapi.WfCoreTestBase;
import org.wildfly.prospero.it.utils.DirectoryComparator;
import org.wildfly.prospero.test.MetadataTestUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.wildfly.prospero.it.ExecutionUtils.isWindows;
import static org.wildfly.prospero.test.MetadataTestUtils.upgradeStreamInManifest;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class ApplyUpdateTest extends CliTestBase  {

    private static FileLock lock;
    private static FileChannel fileChannel;
    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private File targetDir;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        targetDir = tempDir.newFolder();
    }

    @Test
    public void generateUpdateAndApply() throws Exception {
        final Path manifestPath = temp.newFile().toPath();
        final Path provisionConfig = temp.newFile().toPath();
        final Path updatePath = tempDir.newFolder("update-candidate").toPath();
        MetadataTestUtils.copyManifest("manifests/wfcore-base.yaml", manifestPath);
        MetadataTestUtils.prepareChannel(provisionConfig, List.of(manifestPath.toUri().toURL()), defaultRemoteRepositories());

        install(provisionConfig, targetDir.toPath());

        upgradeStreamInManifest(manifestPath, resolvedUpgradeArtifact);

        final URL temporaryRepo = mockTemporaryRepo(true);

        // generate update-candidate
        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.PREPARE,
                        CliConstants.REPOSITORIES, temporaryRepo.toString(),
                        CliConstants.CANDIDATE_DIR, updatePath.toAbsolutePath().toString(),
                        CliConstants.Y,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        // verify the original server has not been modified
        Optional<Stream> wildflyCliStream = getInstalledArtifact(resolvedUpgradeArtifact.getArtifactId(), targetDir.toPath());
        assertEquals(WfCoreTestBase.BASE_VERSION, wildflyCliStream.get().getVersion());

        // apply update-candidate
        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.APPLY,
                        CliConstants.CANDIDATE_DIR, updatePath.toAbsolutePath().toString(),
                        CliConstants.Y,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        // verify the original server has been modified
        wildflyCliStream = getInstalledArtifact(resolvedUpgradeArtifact.getArtifactId(), targetDir.toPath());
        assertEquals(WfCoreTestBase.UPGRADE_VERSION, wildflyCliStream.get().getVersion());
    }

    @Test
    public void generateUpdateAndApplyUsingRepositoryArchive() throws Exception {
        final Path manifestPath = temp.newFile().toPath();
        final Path provisionConfig = temp.newFile().toPath();
        final Path updatePath = tempDir.newFolder("update-candidate").toPath();
        MetadataTestUtils.copyManifest("manifests/wfcore-base.yaml", manifestPath);
        MetadataTestUtils.prepareChannel(provisionConfig, List.of(manifestPath.toUri().toURL()), defaultRemoteRepositories());

        install(provisionConfig, targetDir.toPath());

        upgradeStreamInManifest(manifestPath, resolvedUpgradeArtifact);

        final URL temporaryRepo = mockTemporaryRepo(true);
        final Path repoArchive = createRepositoryArchive(temporaryRepo);

        // generate update-candidate
        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.PREPARE,
                        CliConstants.REPOSITORIES, repoArchive.toUri().toString(),
                        CliConstants.CANDIDATE_DIR, updatePath.toAbsolutePath().toString(),
                        CliConstants.Y,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        // verify the original server has not been modified
        Optional<Stream> wildflyCliStream = getInstalledArtifact(resolvedUpgradeArtifact.getArtifactId(), targetDir.toPath());
        assertEquals(WfCoreTestBase.BASE_VERSION, wildflyCliStream.get().getVersion());

        // apply update-candidate
        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.APPLY,
                        CliConstants.CANDIDATE_DIR, updatePath.toAbsolutePath().toString(),
                        CliConstants.Y,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        // verify the original server has been modified
        wildflyCliStream = getInstalledArtifact(resolvedUpgradeArtifact.getArtifactId(), targetDir.toPath());
        assertEquals(WfCoreTestBase.UPGRADE_VERSION, wildflyCliStream.get().getVersion());
    }

    @Test
    public void generateUpdateAndApplyIntoSymbolicLink() throws Exception {
        final Path manifestPath = temp.newFile().toPath();
        final Path provisionConfig = temp.newFile().toPath();
        final Path updatePath = tempDir.newFolder("update-candidate").toPath();
        MetadataTestUtils.copyManifest("manifests/wfcore-base.yaml", manifestPath);
        MetadataTestUtils.prepareChannel(provisionConfig, List.of(manifestPath.toUri().toURL()), defaultRemoteRepositories());

        final Path targetLink = Files.createSymbolicLink(temp.newFolder().toPath().resolve("target-link"), targetDir.toPath());
        final Path candidateLink = Files.createSymbolicLink(temp.newFolder().toPath().resolve("update-candidate-link"), updatePath);

        install(provisionConfig, targetLink);

        upgradeStreamInManifest(manifestPath, resolvedUpgradeArtifact);

        final URL temporaryRepo = mockTemporaryRepo(true);

        // generate update-candidate
        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.PREPARE,
                        CliConstants.REPOSITORIES, temporaryRepo.toString(),
                        CliConstants.CANDIDATE_DIR, candidateLink.toAbsolutePath().toString(),
                        CliConstants.Y,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        // verify the original server has not been modified
        Optional<Stream> wildflyCliStream = getInstalledArtifact(resolvedUpgradeArtifact.getArtifactId(), targetDir.toPath());
        assertEquals(WfCoreTestBase.BASE_VERSION, wildflyCliStream.get().getVersion());

        // apply update-candidate
        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.APPLY,
                        CliConstants.CANDIDATE_DIR, candidateLink.toAbsolutePath().toString(),
                        CliConstants.Y, CliConstants.VERBOSE,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        // verify the original server has been modified
        wildflyCliStream = getInstalledArtifact(resolvedUpgradeArtifact.getArtifactId(), targetDir.toPath());
        assertEquals(WfCoreTestBase.UPGRADE_VERSION, wildflyCliStream.get().getVersion());
    }

    @Test
    public void failedApplyCandidate_ShouldRevertAllFileChanges() throws Exception {
        final Path manifestPath = temp.newFile().toPath();
        final Path provisionConfig = temp.newFile().toPath();
        final Path updatePath = tempDir.newFolder("update-candidate").toPath();
        MetadataTestUtils.copyManifest("manifests/wfcore-base.yaml", manifestPath);
        MetadataTestUtils.prepareChannel(provisionConfig, List.of(manifestPath.toUri().toURL()), defaultRemoteRepositories());

        install(provisionConfig, targetDir.toPath());

        upgradeStreamInManifest(manifestPath, resolvedUpgradeArtifact);

        final URL temporaryRepo = mockTemporaryRepo(true);

        // generate update-candidate
        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.PREPARE,
                        CliConstants.REPOSITORIES, temporaryRepo.toString(),
                        CliConstants.CANDIDATE_DIR, updatePath.toAbsolutePath().toString(),
                        CliConstants.Y,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        // verify the original server has not been modified
        Optional<Stream> wildflyCliStream = getInstalledArtifact(resolvedUpgradeArtifact.getArtifactId(), targetDir.toPath());
        assertEquals(WfCoreTestBase.BASE_VERSION, wildflyCliStream.get().getVersion());


        final File originalServer = temp.newFolder("server-copy");
        FileUtils.copyDirectory(targetDir,originalServer);
        final Path protectedPath = targetDir.toPath().resolve(".installation");
        try {
            // lock a resource that would be modified to force the apply to fail
            lockPath(protectedPath, false);

            // apply update-candidate
            ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.APPLY,
                            CliConstants.CANDIDATE_DIR, updatePath.toAbsolutePath().toString(),
                            CliConstants.Y, CliConstants.VERBOSE,
                            CliConstants.DIR, targetDir.getAbsolutePath())
                    .execute()
                    .assertReturnCode(ReturnCodes.PROCESSING_ERROR);
        } finally {
            lockPath(protectedPath, true);
        }

        DirectoryComparator.assertNoChanges(originalServer.toPath(), targetDir.toPath());
    }

    private static void lockPath(Path protectedPath, boolean writable) {
        if (isWindows()) {
            // On Windows setting permissions on directories isn't supported and setting read-only on file doesn't prevent it from being overwritten
            // We can lock the file though, which will prevent it from being replaced
            if (!writable) {
                try {
                    fileChannel = FileChannel.open(protectedPath.resolve("manifest.yaml"), StandardOpenOption.WRITE);
                    lock = fileChannel.lock();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                try {
                    lock.release();
                    fileChannel.close();
                    lock = null;
                    fileChannel = null;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            // On Linux for a change setting a file to be read-only doesn't prevent it from being overwritten
            assertTrue("Unable to set the read-only file permissions", protectedPath.toFile().setWritable(writable));
        }
    }


    private Path createRepositoryArchive(URL temporaryRepo) throws URISyntaxException, IOException {
        final Path repoPath = Path.of(temporaryRepo.toURI());
        final Path root = tempDir.newFolder("repo-root").toPath();
        FileUtils.createParentDirectories(root.resolve("update-repository").resolve("maven-repository").toFile());
        Files.move(repoPath, root.resolve("update-repository").resolve("maven-repository"));
        ZipUtils.zip(root, root.resolve("repository.zip"));
        return root.resolve("repository.zip");
    }
}
