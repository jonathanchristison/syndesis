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
package io.syndesis.controllers.integration.online;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.fabric8.kubernetes.client.KubernetesClientException;
import io.syndesis.controllers.StateChangeHandler;
import io.syndesis.controllers.StateUpdate;
import io.syndesis.model.integration.IntegrationDeployment;
import io.syndesis.model.integration.IntegrationDeploymentState;
import io.syndesis.openshift.OpenShiftService;

public class DeactivateHandler extends BaseHandler implements StateChangeHandler {
    DeactivateHandler(OpenShiftService openShiftService) {
        super(openShiftService);
    }

    @Override
    public Set<IntegrationDeploymentState> getTriggerStates() {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            IntegrationDeploymentState.Inactive, IntegrationDeploymentState.Draft)));
    }

    @Override
    public StateUpdate execute(IntegrationDeployment integrationDeployment) {
        try {
            openShiftService().scale(integrationDeployment.getName(), 0);
            logInfo(integrationDeployment,"Deactivated");
        } catch (KubernetesClientException e) {
            // Ignore 404 errors, means the deployment does not exist for us
            // to scale down
            if( e.getCode() != 404 ) {
                throw e;
            }
        }

        IntegrationDeploymentState currentState = openShiftService().isScaled(integrationDeployment.getName(), 0)
            ? IntegrationDeploymentState.Inactive
                : IntegrationDeploymentState.Pending;

        return new StateUpdate(currentState);
    }

}
