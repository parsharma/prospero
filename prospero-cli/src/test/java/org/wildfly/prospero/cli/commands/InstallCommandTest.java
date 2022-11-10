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

package org.wildfly.prospero.cli.commands;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.xml.ProvisioningXmlWriter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.actions.ProvisioningAction;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InstallCommandTest extends AbstractMavenCommandTest {

    public static final String KNOWN_FPL = "known-fpl";
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock
    private ActionFactory actionFactory;

    @Mock
    private ProvisioningAction provisionAction;

    @Captor
    private ArgumentCaptor<ProvisioningConfig> configCaptor;

    @Captor
    private ArgumentCaptor<List<Channel>> channelCaptor;

    @Captor
    private ArgumentCaptor<MavenSessionManager> mavenSessionManager;

    @Override
    protected ActionFactory createActionFactory() {
        return actionFactory;
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        when(actionFactory.install(any(), any(), any())).thenReturn(provisionAction);
    }

    @Test
    public void errorIfTargetPathIsNotPresent() {
        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL);
        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(String.format("Missing required option: '--dir=<directory>'",
                CliConstants.DIR)));
    }

    @Test
    public void errorIfFplIsNotPresent() {
        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(String.format(
                "Missing required argument (specify one of these): (%s=%s | %s=%s)",
                CliConstants.FPL, CliConstants.FEATURE_PACK_REFERENCE, CliConstants.DEFINITION, CliConstants.PATH)));
    }

    @Test
    public void errorIfChannelsIsNotPresentAndUsingCustomFplOnInstall() {
        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test",
                CliConstants.FPL, "foo:bar");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue("output: " + getErrorOutput(), getErrorOutput().contains(String.format(
                CliMessages.MESSAGES.prosperoConfigMandatoryWhenCustomFpl().getMessage(), CliConstants.PROVISION_CONFIG)));
    }

    @Test
    public void callProvisionOnInstallCommandWithCustomFpl() throws Exception {
        final File provisionConfigFile = temporaryFolder.newFile();
        Channel channel = new Channel("", "", null, null,
                List.of(new Repository("test", "http://test.org")),
                new ChannelManifestCoordinate("org.test", "test"));
        new ProsperoConfig(List.of(channel)).writeConfig(provisionConfigFile.toPath());

        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test",
                CliConstants.FPL, "org.wildfly:wildfly-ee-galleon-pack",
                CliConstants.PROVISION_CONFIG, provisionConfigFile.getAbsolutePath());
        assertEquals(ReturnCodes.SUCCESS, exitCode);
        Mockito.verify(provisionAction).provision(configCaptor.capture(), channelCaptor.capture());
        assertThat(configCaptor.getValue().getFeaturePackDeps())
                .map(fp->fp.getLocation().getProducerName())
                .containsExactly("org.wildfly:wildfly-ee-galleon-pack::zip");
    }

    @Test
    public void callProvisionOnInstallKnownCommand() throws Exception {
        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test", CliConstants.FPL, KNOWN_FPL);
        commandLine.getOut();
        commandLine.getErr();

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        Mockito.verify(provisionAction).provision(configCaptor.capture(), channelCaptor.capture());
        assertThat(configCaptor.getValue().getFeaturePackDeps())
                .map(fp->fp.getLocation().getProducerName())
                .containsExactly("org.wildfly.core:wildfly-core-galleon-pack");
    }

    @Test
    public void callProvisionOnInstallKnownFplOverrideChannelsCommand() throws Exception {
        final File provisionConfigFile = temporaryFolder.newFile();
        Channel channel = new Channel("", "", null, null,
                List.of(new Repository("dev", "http://test.test")),
                new ChannelManifestCoordinate("org.wildfly", "wildfly-channel"));
        new ProsperoConfig(List.of(channel)).writeConfig(provisionConfigFile.toPath());

        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test", CliConstants.FPL, KNOWN_FPL,
                CliConstants.PROVISION_CONFIG, provisionConfigFile.getAbsolutePath());

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        Mockito.verify(provisionAction).provision(configCaptor.capture(), channelCaptor.capture());
        assertThat(configCaptor.getValue().getFeaturePackDeps())
                .map(fp->fp.getLocation().getProducerName())
                .containsExactly("org.wildfly.core:wildfly-core-galleon-pack");
        assertThat(channelCaptor.getValue())
                .flatMap(c->c.getRepositories())
                .map(r->r.getId())
                .containsExactly("dev");
    }

    @Test
    public void usingProvisionDefinitonRequiresChannel() throws Exception {
        final File provisionDefinitionFile = temporaryFolder.newFile("provision.xml");
        ProvisioningXmlWriter.getInstance().write(ProvisioningConfig.builder()
                        .addFeaturePackDep(FeaturePackLocation.fromString("org.wildfly.core:wildfly-core-galleon-pack::zip"))
                        .build(),
                provisionDefinitionFile.toPath());

        final File provisionConfigFile = temporaryFolder.newFile();
        Channel channel = new Channel("", "", null, null,
                List.of(new Repository("dev", "http://test.test")),
                new ChannelManifestCoordinate("org.wildfly", "wildfly-channel"));
        new ProsperoConfig(List.of(channel)).writeConfig(provisionConfigFile.toPath());

        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test",
                CliConstants.PROVISION_CONFIG, provisionConfigFile.getAbsolutePath(),
                CliConstants.DEFINITION, provisionDefinitionFile.getAbsolutePath());

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        Mockito.verify(provisionAction).provision(configCaptor.capture(), channelCaptor.capture());
        assertThat(configCaptor.getValue().getFeaturePackDeps())
                .map(fp->fp.getLocation().getProducerName())
                .containsExactly("org.wildfly.core:wildfly-core-galleon-pack::zip");
        assertThat(channelCaptor.getValue())
                .flatMap(c->c.getRepositories())
                .map(r->r.getId())
                .containsExactly("dev");
    }

    @Test
    public void fplAndDefinitionAreNotAllowedTogether() throws Exception {
        final File provisionDefinitionFile = temporaryFolder.newFile("provision.xml");
        final File provisionConfigFile = temporaryFolder.newFile();

        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test",
                CliConstants.DEFINITION, provisionDefinitionFile.getAbsolutePath(),
                CliConstants.PROVISION_CONFIG, provisionConfigFile.getAbsolutePath(),
                CliConstants.FPL, "test");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
    }

//    @Test
//    public void passChannelReposToProvisionDef() throws Exception {
//        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test",
//                CliConstants.FPL, KNOWN_FPL, CliConstants.REMOTE_REPOSITORIES, "http://test.repo1,http://test.repo2");
//
//        assertEquals(ReturnCodes.SUCCESS, exitCode);
//        Mockito.verify(provisionAction).provision(serverDefiniton.capture());
//        assertThat(serverDefiniton.getValue().getRepositories().stream().map(RemoteRepository::getUrl)).contains(
//                "http://test.repo1",
//                "http://test.repo2"
//        );
//    }

    @Test
    public void provisionConfigAndChannelSet() throws IOException {
        final File provisionConfigFile = temporaryFolder.newFile();

        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test",
                CliConstants.PROVISION_CONFIG, provisionConfigFile.getAbsolutePath(),
                CliConstants.CHANNEL_MANIFEST, "g:a:v",
                CliConstants.FPL, "test");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES
                .exclusiveOptions(CliConstants.PROVISION_CONFIG, CliConstants.CHANNEL_MANIFEST).getMessage()));
    }

    @Test
    public void provisionConfigAndRemoteRepoSet() throws IOException {
        final File provisionConfigFile = temporaryFolder.newFile();

        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test",
                CliConstants.PROVISION_CONFIG, provisionConfigFile.getAbsolutePath(),
                CliConstants.REPOSITORIES, "file:/test",
                CliConstants.FPL, "test");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES
                .exclusiveOptions(CliConstants.PROVISION_CONFIG, CliConstants.REPOSITORIES).getMessage()));
    }

    @Override
    protected MavenSessionManager getCapturedSessionManager() {
        Mockito.verify(actionFactory).install(any(), mavenSessionManager.capture(), any());
        MavenSessionManager msm = mavenSessionManager.getValue();
        return msm;
    }

    @Override
    protected String[] getDefaultArguments() {
        return new String[]{CliConstants.Commands.INSTALL, CliConstants.DIR, "test",
                CliConstants.FPL, KNOWN_FPL};
    }
}
