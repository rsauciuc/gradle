/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.ivyservice.moduleconverter
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.internal.artifacts.DefaultDependencySet
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultPublishArtifactSet
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependenciesToModuleDescriptorConverter
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.component.external.model.DefaultIvyModulePublishMetaData
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.local.model.BuildableLocalComponentMetaData
import org.gradle.util.TestUtil
import org.gradle.util.WrapUtil
import spock.lang.Specification

class DefaultConfigurationComponentMetaDataBuilderTest extends Specification {
    def dependenciesConverter = Mock(DependenciesToModuleDescriptorConverter)
    def converter = new DefaultConfigurationComponentMetaDataBuilder(dependenciesConverter)

    def componentId = DefaultModuleComponentIdentifier.newId("org", "name", "rev");
    def id = DefaultModuleVersionIdentifier.newId(componentId);

    def "adds artifacts from each configuration"() {
        def emptySet = new HashSet<String>()
        def metaData = Mock(BuildableLocalComponentMetaData)
        def config1 = Stub(Configuration)
        def config2 = Stub(Configuration)
        def artifacts1 = Stub(PublishArtifactSet)
        def artifacts2 = Stub(PublishArtifactSet)

        given:
        config1.name >> "config1"
        config1.artifacts >> artifacts1
        config2.name >> "config2"
        config2.artifacts >> artifacts2

        when:
        converter.addConfigurations(metaData, [config1, config2])

        then:
        _ * metaData.addConfiguration("config1", '', emptySet, emptySet, false, false, _ as TaskDependency)
        _ * metaData.addConfiguration("config2", '', emptySet, emptySet, false, false, _ as TaskDependency)
        1 * metaData.addArtifacts("config1", artifacts1)
        1 * metaData.addArtifacts("config2", artifacts2)
        0 * metaData._
    }

    def "adds configurations to ivy module descriptor"() {
        when:
        Configuration config1 = createNamesAndExtendedConfigurationStub("conf1");
        Configuration config2 = createNamesAndExtendedConfigurationStub("conf2", config1);
        DefaultIvyModulePublishMetaData metaData = new DefaultIvyModulePublishMetaData(id, "status");

        and:
        converter.addConfigurations(metaData, WrapUtil.toSet(config1, config2));

        then:
        ModuleDescriptor moduleDescriptor = metaData.getModuleDescriptor();
        assertIvyConfigurationIsCorrect(moduleDescriptor.getConfiguration("conf1"), expectedIvyConfiguration(config1));
        assertIvyConfigurationIsCorrect(moduleDescriptor.getConfiguration("conf2"), expectedIvyConfiguration(config2));

        moduleDescriptor.getConfigurations().length == 2
    }

    private static assertIvyConfigurationIsCorrect(org.apache.ivy.core.module.descriptor.Configuration actualConfiguration,
                                                 org.apache.ivy.core.module.descriptor.Configuration expectedConfiguration) {
        assert actualConfiguration.getDescription() == expectedConfiguration.getDescription()
        assert actualConfiguration.isTransitive() == expectedConfiguration.isTransitive()
        assert actualConfiguration.getVisibility() == expectedConfiguration.getVisibility()
        assert actualConfiguration.getName() == expectedConfiguration.getName()
        assert actualConfiguration.getExtends() as List == expectedConfiguration.getExtends() as List
        true
    }

    private static org.apache.ivy.core.module.descriptor.Configuration expectedIvyConfiguration(Configuration configuration) {
        return new org.apache.ivy.core.module.descriptor.Configuration(
                configuration.getName(),
                configuration.isVisible() ? org.apache.ivy.core.module.descriptor.Configuration.Visibility.PUBLIC : org.apache.ivy.core.module.descriptor.Configuration.Visibility.PRIVATE,
                configuration.getDescription(),
                Configurations.getNames(configuration.getExtendsFrom()).toArray(new String[configuration.getExtendsFrom().size()]),
                configuration.isTransitive(),
                null);
    }

    private Configuration createNamesAndExtendedConfigurationStub(final String name, final Configuration... extendsFromConfigurations) {
        final Configuration stub = Mock(ConfigurationInternal)
        stub.getName() >> name
        stub.getDescription() >> TestUtil.createUniqueId()
        stub.isTransitive() >> true
        stub.isVisible() >> true
        stub.getExtendsFrom() >> WrapUtil.toSet(extendsFromConfigurations)
        stub.getHierarchy() >> WrapUtil.toSet(extendsFromConfigurations)
        stub.getAllDependencies() >> new DefaultDependencySet("foo", WrapUtil.toDomainObjectSet(Dependency.class))
        stub.getArtifacts() >> new DefaultPublishArtifactSet("foo", WrapUtil.toDomainObjectSet(PublishArtifact.class))
        stub.getAllArtifacts() >> new DefaultPublishArtifactSet("foo", WrapUtil.toDomainObjectSet(PublishArtifact.class))
        return stub;
    }

}
