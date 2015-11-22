/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.jvm.internal;

import org.gradle.api.Action;
import org.gradle.jvm.toolchain.JavaToolChainRegistry;
import org.gradle.language.base.internal.ProjectLayout;
import org.gradle.model.Defaults;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.ComponentSpec;

import java.io.File;

@SuppressWarnings("UnusedDeclaration")
public class JarBinaryRules extends RuleSource {
    @Defaults
    void configureJarBinaries(final ComponentSpec jvmLibrary, ProjectLayout projectLayout, final JavaToolChainRegistry toolChains) {
        final File binariesDir = new File(projectLayout.getBuildDir(), "jars");
        final File classesDir = new File(projectLayout.getBuildDir(), "classes");
        final File resourcesDir = new File(projectLayout.getBuildDir(), "resources");
        jvmLibrary.getBinaries().withType(JarBinarySpecInternal.class).beforeEach(new Action<JarBinarySpecInternal>() {
            @Override
            public void execute(JarBinarySpecInternal jarBinary) {
                String jarBinaryName = jarBinary.getProjectScopedName();
                int idx = jarBinaryName.lastIndexOf("Jar");
                String apiJarBinaryName = idx>0?jarBinaryName.substring(0, idx) + "ApiJar" : jarBinaryName + "ApiJar";
                String libraryName = jarBinary.getId().getLibraryName();

                jarBinary.setClassesDir(new File(classesDir, jarBinaryName));
                jarBinary.setResourcesDir(new File(resourcesDir, jarBinaryName));
                jarBinary.setJarFile(new File(binariesDir, String.format("%s%s%s.jar", jarBinaryName, File.separator, libraryName)));
                jarBinary.setApiJarFile(new File(binariesDir, String.format("%s%s%s.jar", apiJarBinaryName, File.separator, libraryName)));
                jarBinary.setToolChain(toolChains.getForPlatform(jarBinary.getTargetPlatform()));
            }
        });
    }
}
