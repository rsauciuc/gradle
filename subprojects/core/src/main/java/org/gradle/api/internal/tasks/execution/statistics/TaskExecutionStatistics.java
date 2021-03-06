/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.tasks.execution.statistics;

import com.google.common.base.Preconditions;

public class TaskExecutionStatistics {
    private final int avoidedTasksCount;
    private final int executedTasksCount;

    public TaskExecutionStatistics(int executedTasksCount, int avoidedTasksCount) {
        Preconditions.checkArgument(avoidedTasksCount >= 0, "numAvoidedTasks must be non-negative");
        Preconditions.checkArgument(executedTasksCount >= 0, "numExecutedTasks must be non-negative");

        this.avoidedTasksCount = avoidedTasksCount;
        this.executedTasksCount = executedTasksCount;
    }

    public int getAvoidedTasksCount() {
        return avoidedTasksCount;
    }

    public int getExecutedTasksCount() {
        return executedTasksCount;
    }
}
