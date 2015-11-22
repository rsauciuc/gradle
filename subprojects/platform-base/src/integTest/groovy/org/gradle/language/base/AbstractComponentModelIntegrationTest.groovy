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

package org.gradle.language.base

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

abstract class AbstractComponentModelIntegrationTest extends AbstractIntegrationSpec {
    /**
     * Registers CustomComponent type
     */
    void withCustomComponentType() {
        buildFile << """
            @Managed interface CustomComponent extends ComponentSpec {}

            class ComponentTypeRules extends RuleSource {
                @ComponentType
                void registerCustomComponentType(ComponentTypeBuilder<CustomComponent> builder) {}
            }

            apply type: ComponentTypeRules
        """
    }

    /**
     * Registers CustomBinary type
     */
    void withCustomBinaryType() {
        buildFile << """
            interface CustomBinary extends BinarySpec {
                String getData()
                void setData(String value)
            }
            class DefaultCustomBinary extends BaseBinarySpec implements CustomBinary {
                String data = "bar"
            }

            class BinaryRules extends RuleSource {
                @BinaryType
                void registerCustomBinary(BinaryTypeBuilder<CustomBinary> builder) {
                    builder.defaultImplementation(DefaultCustomBinary)
                }
            }

            apply type: BinaryRules
        """
    }

    /**
     * Registers CustomLanguageSourceSet type
     */
    void withCustomLanguageType() {
        buildFile << """
            interface CustomLanguageSourceSet extends LanguageSourceSet {
                String getData();
            }

            class DefaultCustomLanguageSourceSet extends BaseLanguageSourceSet implements CustomLanguageSourceSet {
                final String data = "foo"

                public boolean getMayHaveSources() {
                    true
                }
            }

            class LanguageTypeRules extends RuleSource {
                @LanguageType
                void registerCustomLanguage(LanguageTypeBuilder<CustomLanguageSourceSet> builder) {
                    builder.defaultImplementation(DefaultCustomLanguageSourceSet)
                }
            }

            apply type: LanguageTypeRules
        """
    }

    /**
     * Registers a transform for CustomLanguageSourceSet as input to any binary
     */
    void withCustomLanguageTransform() {
        buildFile << """
            import org.gradle.language.base.internal.registry.*
            import org.gradle.language.base.internal.*
            import org.gradle.language.base.*
            import org.gradle.internal.reflect.*
            import org.gradle.internal.service.*


            class CustomLanguageTransformation implements LanguageTransform {
                Class getSourceSetType() {
                    CustomLanguageSourceSet
                }

                Class getOutputType() {
                    CustomTransformationFileType
                }

                Map<String, Class<?>> getBinaryTools() {
                    throw new UnsupportedOperationException()
                }

                SourceTransformTaskConfig getTransformTask() {
                    new SourceTransformTaskConfig() {
                        String getTaskPrefix() {
                            "custom"
                        }

                        Class<? extends DefaultTask> getTaskType() {
                            DefaultTask
                        }

                        void configureTask(Task task, BinarySpec binary, LanguageSourceSet sourceSet, ServiceRegistry serviceRegistry) {
                        }
                    }
                }

                boolean applyToBinary(BinarySpec binary) {
                    true
                }
            }

            class CustomTransformationFileType implements TransformationFileType {
            }

            class LanguageRules extends RuleSource {
                @Mutate
                void registerLanguageTransformation(LanguageTransformContainer transforms) {
                    transforms.add(new CustomLanguageTransformation())
                }
            }

            apply type: LanguageRules
        """
    }

}
