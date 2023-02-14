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

package org.wildfly.prospero.cli;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.api.ProvisioningProgressEvent;
import org.wildfly.prospero.api.ArtifactChange;

public class CliConsole implements Console {

    @Override
    public void progressUpdate(ProvisioningProgressEvent update) {
        if (update.getEventType() != ProvisioningProgressEvent.EventType.STARTING) {
            getStdOut().print("\r");
        }

        if (update.getEventType() == ProvisioningProgressEvent.EventType.STARTING) {
            switch (update.getStage()) {
                case "LAYOUT_BUILD":
                    getStdOut().print(CliMessages.MESSAGES.resolvingFeaturePack());
                    break;
                case "PACKAGES":
                    getStdOut().print(CliMessages.MESSAGES.installingPackages());
                    break;
                case "CONFIGS":
                    getStdOut().print(CliMessages.MESSAGES.generatingConfiguration());
                    break;
                case "JBMODULES":
                    getStdOut().print(CliMessages.MESSAGES.installingJBossModules());
                    break;
            }
        }

        if (update.getEventType() == ProvisioningProgressEvent.EventType.PULSE) {
            final double progress = update.getProgress();
            switch (update.getStage()) {
                case "LAYOUT_BUILD":
                    getStdOut().print("\r");
                    getStdOut().printf(CliMessages.MESSAGES.resolvingFeaturePack() + " %.0f%%", progress);
                    break;
                case "PACKAGES":
                    getStdOut().print("\r");
                    getStdOut().printf(CliMessages.MESSAGES.installingPackages() + " %.0f%%", progress);
                    break;
                case "CONFIGS":
                    getStdOut().print("\r");
                    getStdOut().printf(CliMessages.MESSAGES.generatingConfiguration() + " %.0f%%", progress);
                    break;
                case "JBMODULES":
                    getStdOut().print("\r");
                    getStdOut().printf(CliMessages.MESSAGES.installingJBossModules() + " %.0f%%", progress);
                    break;
            }
        }

        if (update.getEventType() == ProvisioningProgressEvent.EventType.COMPLETED) {
            switch (update.getStage()) {
                case "LAYOUT_BUILD":
                    getStdOut().print("\r");
                    getStdOut().println(CliMessages.MESSAGES.featurePacksResolved());
                    break;
                case "PACKAGES":
                    getStdOut().print("\r");
                    getStdOut().println(CliMessages.MESSAGES.packagesInstalled());
                    break;
                case "CONFIGS":
                    getStdOut().print("\r");
                    getStdOut().println(CliMessages.MESSAGES.configurationsGenerated());
                    break;
                case "JBMODULES":
                    getStdOut().print("\r");
                    getStdOut().println(CliMessages.MESSAGES.jbossModulesInstalled());
                    break;
            }
        }
    }

    public void updatesFound(List<ArtifactChange> artifactUpdates) {
        if (artifactUpdates.isEmpty()) {
            getStdOut().println(CliMessages.MESSAGES.noUpdatesFound());
        } else {
            getStdOut().println(CliMessages.MESSAGES.updatesFound());
            for (ArtifactChange artifactUpdate : artifactUpdates) {
                final Optional<String> newVersion = artifactUpdate.getNewVersion();
                final Optional<String> oldVersion = artifactUpdate.getOldVersion();
                final String artifactName = artifactUpdate.getArtifactName();

                getStdOut().printf("  %s%-50s    %-20s ==>  %-20s%n", artifactUpdate.isDowngrade()?"[*]":"", artifactName, oldVersion.orElse("[]"),
                        newVersion.orElse("[]"));
            }

            if (artifactUpdates.stream().anyMatch(ArtifactChange::isDowngrade)) {
                getStdOut().printf(CliMessages.MESSAGES.possibleDowngrade());
            }
        }
    }

    public boolean confirmUpdates() {
        return confirm(CliMessages.MESSAGES.continueWithUpdate(),
                CliMessages.MESSAGES.applyingUpdates(),
                CliMessages.MESSAGES.updateCancelled());
    }

    public boolean confirmBuildUpdates() {
        return confirm(CliMessages.MESSAGES.continueWithBuildUpdate(),
                CliMessages.MESSAGES.buildingUpdates(),
                CliMessages.MESSAGES.buildUpdateCancelled());
    }

    public boolean confirm(String prompt, String accepted, String cancelled) {
        getStdOut().print(prompt);
        Scanner sc = new Scanner(getInput());
        while (true) {
            String resp = sc.nextLine();
            if (resp.equalsIgnoreCase(CliMessages.MESSAGES.noShortcut()) || resp.isBlank()) {
                println(cancelled);
                return false;
            } else if (resp.equalsIgnoreCase(CliMessages.MESSAGES.yesShortcut())) {
                println(accepted);
                return true;
            } else {
                getStdOut().print(CliMessages.MESSAGES.chooseYN());
            }
        }
    }

    public void updatesComplete() {
        println(CliMessages.MESSAGES.updateComplete());
    }

    public void buildUpdatesComplete() {
        println(CliMessages.MESSAGES.buildUpdateComplete());
    }

    public PrintStream getStdOut() {
        return System.out;
    }

    public PrintStream getErrOut() {
        return System.err;
    }

    public InputStream getInput() {
        return System.in;
    }

    public void error(String message, String... args) {
        getErrOut().println(String.format(message, args));
    }

    @Override
    public void println(String text) {
        getStdOut().println(text);
    }

}
