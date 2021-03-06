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
package io.syndesis.integration.runtime.handlers;

import java.util.Optional;

import io.syndesis.integration.runtime.IntegrationRouteBuilder;
import io.syndesis.integration.runtime.IntegrationStepHandler;
import io.syndesis.integration.runtime.util.JsonSimplePredicate;
import io.syndesis.model.filter.ExpressionFilterStep;
import io.syndesis.model.filter.FilterStep;
import io.syndesis.model.filter.RuleFilterStep;
import io.syndesis.model.integration.Step;
import org.apache.camel.CamelContext;
import org.apache.camel.Predicate;
import org.apache.camel.model.FilterDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.util.ObjectHelper;

public class FilterStepHandler implements IntegrationStepHandler {
    @Override
    public boolean canHandle(Step step) {
        if (!ExpressionFilterStep.STEP_KIND.equals(step.getStepKind()) && !RuleFilterStep.STEP_KIND.equals(step.getStepKind())) {
            return false;
        }

        return step instanceof FilterStep;
    }

    @Override
    public Optional<ProcessorDefinition> handle(Step step, ProcessorDefinition route, IntegrationRouteBuilder builder) {
        ObjectHelper.notNull(route, "route");

        final FilterStep filterStep = (FilterStep)step;
        final String expression = ObjectHelper.notNull(filterStep.getFilterExpression(), "expression");
        final CamelContext context = builder.getContext();
        final Predicate predicate = new JsonSimplePredicate(expression, context);
        final FilterDefinition filter = route.filter(predicate);

        return Optional.of(filter);
    }
}
