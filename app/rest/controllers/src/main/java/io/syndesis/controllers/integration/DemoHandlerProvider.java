/*
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.syndesis.controllers.integration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.syndesis.controllers.StateChangeHandler;
import io.syndesis.controllers.StateChangeHandlerProvider;
import io.syndesis.controllers.StateUpdate;

import io.syndesis.model.integration.IntegrationDeployment;
import io.syndesis.model.integration.IntegrationDeploymentState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "controllers.integration.enabled", havingValue = "false")
public class DemoHandlerProvider implements StateChangeHandlerProvider {

    @Override
    public List<StateChangeHandler> getStatusChangeHandlers() {
        return Arrays.asList(
            new DemoHandler(IntegrationDeploymentState.Active, 5000L),
            new DemoHandler(IntegrationDeploymentState.Inactive, 5000L),
            new DemoHandler(IntegrationDeploymentState.Undeployed, 5000L));
    }

    /* default */ static class DemoHandler implements StateChangeHandler {
        private final IntegrationDeploymentState state;
        private final long waitMillis;

        /* default */ DemoHandler(IntegrationDeploymentState state, long waitMillis) {
            this.state = state;
            this.waitMillis = waitMillis;
        }

        @Override
        public Set<IntegrationDeploymentState> getTriggerStates() {
            return Collections.singleton(state);
        }

        @Override
        public StateUpdate execute(IntegrationDeployment integrationDeployment) {

            try {
                Thread.sleep(waitMillis);
                return new StateUpdate(state);
            } catch (InterruptedException e) {
                return null;
            }
        }
    }
}
