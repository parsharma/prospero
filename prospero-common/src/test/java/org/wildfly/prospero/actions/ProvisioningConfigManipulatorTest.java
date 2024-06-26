/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero.actions;

import org.assertj.core.api.Assertions;
import org.jboss.galleon.api.config.GalleonConfigurationWithLayersBuilder;
import org.jboss.galleon.api.config.GalleonFeaturePackConfig;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.junit.Test;

import static org.junit.Assert.*;

public class ProvisioningConfigManipulatorTest {

    @Test
    public void copyFeaturePackConfigCopiesInherits() throws Exception {
        final GalleonFeaturePackConfig origin = GalleonFeaturePackConfig.builder(FeaturePackLocation.fromString("org.test:test"))
                .setInheritPackages(true)
                .setInheritConfigs(true)
                .setInheritModelOnlyConfigs(true)
                .build();

        final GalleonFeaturePackConfig.Builder res = GalleonFeaturePackConfig.builder(FeaturePackLocation.fromString("org.test:test"));
        ProvisioningConfigManipulator.copyFeaturePackConfig(origin, res);

        assertEquals(origin, res.build());
    }

    @Test
    public void copyFeaturePackConfigCopiesExcludedPackages() throws Exception {
        final GalleonFeaturePackConfig origin = GalleonFeaturePackConfig.builder(FeaturePackLocation.fromString("org.test:test"))
                .excludePackage("exclude1")
                .excludePackage("exclude2")
                .excludeConfigModel("config1")
                .excludeDefaultConfig("test", "config2")
                .build();

        final GalleonFeaturePackConfig.Builder res = GalleonFeaturePackConfig.builder(FeaturePackLocation.fromString("org.test:test"));
        ProvisioningConfigManipulator.copyFeaturePackConfig(origin, res);

        assertEquals(origin, res.build());
    }

    @Test
    public void copyFeaturePackConfigCopiesIncludedPackages() throws Exception {
        final GalleonFeaturePackConfig origin = GalleonFeaturePackConfig.builder(FeaturePackLocation.fromString("org.test:test"))
                .includePackage("include1")
                .includePackage("include2")
                .includeConfigModel("config1")
                .includeDefaultConfig("test", "config2")
                .build();

        final GalleonFeaturePackConfig.Builder res = GalleonFeaturePackConfig.builder(FeaturePackLocation.fromString("org.test:test"));
        ProvisioningConfigManipulator.copyFeaturePackConfig(origin, res);

        assertEquals(origin, res.build());
    }

    @Test
    public void copyFeaturePackConfigCopiesPatches() throws Exception {
        final GalleonFeaturePackConfig origin = GalleonFeaturePackConfig.builder(FeaturePackLocation.fromString("org.test:test"))
                .addPatch(FeaturePackLocation.fromString("org.test:patch").getFPID())
                .build();

        final GalleonFeaturePackConfig.Builder res = GalleonFeaturePackConfig.builder(FeaturePackLocation.fromString("org.test:test"));
        ProvisioningConfigManipulator.copyFeaturePackConfig(origin, res);

        assertEquals(origin, res.build());
    }

    @Test
    public void copyFeaturePackConfigCopiesConfigs() throws Exception {
        final GalleonFeaturePackConfig origin = GalleonFeaturePackConfig.builder(FeaturePackLocation.fromString("org.test:test"))
                .addConfig(GalleonConfigurationWithLayersBuilder.builder().setModel("model").setName("test").build())
                .build();

        final GalleonFeaturePackConfig.Builder res = GalleonFeaturePackConfig.builder(FeaturePackLocation.fromString("org.test:test"));
        ProvisioningConfigManipulator.copyFeaturePackConfig(origin, res);

        assertEquals(origin, res.build());
    }

    @Test
    public void removeFeaturePackDefinitionRemovesFeaturePack() throws Exception {
        final GalleonProvisioningConfig.Builder builder = GalleonProvisioningConfig.builder()
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:test-one:1.0.0"))
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:test-two:1.0.0"))
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:test-three:1.0.0"));
        final int removedIndex = new ProvisioningConfigManipulator(builder).removeFeaturePackDefinition("org.test:test-two:1.0.0");

        assertEquals(1, removedIndex);

        assertEquals(GalleonProvisioningConfig.builder()
                        .addFeaturePackDep(FeaturePackLocation.fromString("org.test:test-one:1.0.0"))
                        .addFeaturePackDep(FeaturePackLocation.fromString("org.test:test-three:1.0.0"))
                        .build(),
                builder.build());
    }

    @Test
    public void convertToTransitiveRemovesSelectedFeaturePack() throws Exception {
        final GalleonProvisioningConfig.Builder builder = GalleonProvisioningConfig.builder()
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:test-one:1.0.0"))
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:test-two:1.0.0"))
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:test-three:1.0.0"));
        final int removedIndex = new ProvisioningConfigManipulator(builder).convertToTransitiveDep("org.test:test-two:1.0.0",
                builder.build());

        assertEquals(1, removedIndex);

        Assertions.assertThat(builder.build().getFeaturePackDeps())
                .map(GalleonFeaturePackConfig::getLocation)
                .containsOnly(
                        FeaturePackLocation.fromString("org.test:test-one:1.0.0"),
                        FeaturePackLocation.fromString("org.test:test-three:1.0.0"));
    }

    @Test
    public void convertToTransitiveAddsTransitiveDependency() throws Exception {

        final GalleonProvisioningConfig.Builder builder = GalleonProvisioningConfig.builder()
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:test-one:1.0.0"))
                .addFeaturePackDep(GalleonFeaturePackConfig.builder(FeaturePackLocation.fromString("org.test:test-two:1.0.0"))
                        .includePackage("org.test:package")
                        .build())
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:test-three:1.0.0"));
        final int removedIndex = new ProvisioningConfigManipulator(builder).convertToTransitiveDep("org.test:test-two:1.0.0",
                builder.build());

        assertEquals(1, removedIndex);

        Assertions.assertThat(builder.build().getTransitiveDeps())
                .containsOnly(
                        GalleonFeaturePackConfig.transitiveBuilder(FeaturePackLocation.fromString("org.test:test-two:1.0.0"))
                                .includePackage("org.test:package")
                                .build());

    }
}