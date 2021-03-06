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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.syndesis.core.CollectionsUtils;
import io.syndesis.core.Optionals;
import io.syndesis.core.Predicates;
import io.syndesis.integration.component.proxy.ComponentProxyComponent;
import io.syndesis.integration.component.proxy.ComponentProxyCustomizer;
import io.syndesis.integration.component.proxy.ComponentProxyFactory;
import io.syndesis.integration.runtime.IntegrationRouteBuilder;
import io.syndesis.model.action.ConnectorAction;
import io.syndesis.model.action.ConnectorDescriptor;
import io.syndesis.model.connection.Connection;
import io.syndesis.model.connection.Connector;
import io.syndesis.model.integration.Step;
import org.apache.camel.CamelContext;
import org.apache.camel.TypeConverter;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;

@SuppressWarnings("PMD.ExcessiveImports")
public class ConnectorStepHandler extends AbstractEndpointStepHandler {
    @Override
    public boolean canHandle(Step step) {
        if (!"endpoint".equals(step.getStepKind()) && !"connector".equals(step.getStepKind())) {
            return false;
        }

        if (!step.getConnection().isPresent()) {
            return false;
        }
        if (!step.getConnection().get().getConnector().isPresent()) {
            return false;
        }
        if (!step.getAction().filter(ConnectorAction.class::isInstance).isPresent()) {
            return false;
        }

        return Optionals.first(
            step.getAction().filter(ConnectorAction.class::isInstance).map(ConnectorAction.class::cast).get().getDescriptor().getComponentScheme(),
            step.getConnection().get().getConnector().get().getComponentScheme()
        ).isPresent();
    }

    @SuppressWarnings("PMD")
    @Override
    public Optional<ProcessorDefinition> handle(Step step, ProcessorDefinition route, IntegrationRouteBuilder builder) {
        // Model
        final Connection connection = step.getConnection().get();
        final Connector connector = connection.getConnector().get();
        final ConnectorAction action = step.getAction().filter(ConnectorAction.class::isInstance).map(ConnectorAction.class::cast).get();
        final ConnectorDescriptor descriptor = action.getDescriptor();

        // Camel
        final String index = step.getMetadata(Step.METADATA_STEP_INDEX).orElseThrow(() -> new IllegalArgumentException("Missing index for step:" + step));
        final String scheme = Optionals.first(descriptor.getComponentScheme(), connector.getComponentScheme()).get();
        final CamelContext context = builder.getContext();
        final TypeConverter converter = context.getTypeConverter();
        final String componentId = scheme + "-" + index;
        final ComponentProxyComponent component = resolveComponent(componentId, scheme, context, descriptor);
        final List<String> customizers = CollectionsUtils.aggregate(ArrayList::new, connector.getConnectorCustomizers(), descriptor.getConnectorCustomizers());

        Map<String, String> properties = CollectionsUtils.aggregate(connection.getConfiguredProperties(), step.getConfiguredProperties());

        // if the option is marked as secret use property placeholder as the
        // value is added to the integration secret.
        properties.entrySet()
            .stream()
            .filter(Predicates.or(connector::isSecret, action::isSecret))
            .forEach(e -> e.setValue(String.format("{{%s-%s.%s}}", scheme, index, e.getKey())));

        //Connector/Action properties have the precedence
        connector.getConfiguredProperties().forEach(properties::put);
        descriptor.getConfiguredProperties().forEach(properties::put);

        try {
            final Map<String, Object> customizersOptions = new HashMap<>();
            properties.forEach(
                (k, v) -> {
                    try {
                        customizersOptions.put(k, context.resolvePropertyPlaceholders(v));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            );

            for (String customizerType : customizers) {
                ComponentProxyCustomizer customizer = resolveCustomizer(context, customizerType);

                // Set the camel context if the customizer implements
                // the CamelContextAware interface.
                ObjectHelper.trySetCamelContext(customizer, context);

                // Bind properties to the customizer
                IntrospectionSupport.setProperties(converter, customizer, customizersOptions);

                customizer.customize(component, customizersOptions);
            }

            // Set connector options with remaining properties and original values
            // to keep placeholders and delegate resolution to the final endpoint.
            component.setOptions(
                properties.entrySet().stream()
                    .filter(e -> customizersOptions.containsKey(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
            );

            // Remove component
            context.removeComponent(component.getComponentId());
            context.removeService(component);

            // Register component
            context.addService(component, true, true);
            context.addComponent(component.getComponentId(), component);

            if (route == null) {
                route = builder.from(componentId);
            } else {
                route = route.to(componentId);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Handle split
        return handleSplit(descriptor, route, builder);
    }

    // *************************
    // Helpers
    // *************************

    private ComponentProxyComponent resolveComponent(String componentId, String componentScheme, CamelContext context, ConnectorDescriptor descriptor) {
        ComponentProxyFactory factory = ComponentProxyComponent::new;

        if (descriptor.getConnectorFactory().isPresent()) {
            final String factoryType = descriptor.getConnectorFactory().get();
            final ClassResolver resolver = context.getClassResolver();

            Class<? extends ComponentProxyFactory> type = resolver.resolveClass(factoryType, ComponentProxyFactory.class);
            if (type == null) {
                throw new IllegalArgumentException("Unable to resolve a ComponentProxyFactory of type: " + factoryType);
            }

            factory = context.getInjector().newInstance(type);
            if (factory == null) {
                throw new IllegalArgumentException("Unable to instantiate a ComponentProxyFactory of type: " + factoryType);
            }
        }

        return factory.newInstance(componentId, componentScheme);
    }


    private ComponentProxyCustomizer resolveCustomizer(CamelContext context, String customizerType) {
        Class<ComponentProxyCustomizer> type = context.getClassResolver().resolveClass(customizerType, ComponentProxyCustomizer.class);
        if (type == null) {
            throw new IllegalArgumentException("Unable to resolve a ComponentProxyCustomizer of type: " + customizerType);
        }

        final ComponentProxyCustomizer customizer = context.getInjector().newInstance(type);
        if (customizer == null) {
            throw new IllegalArgumentException("Unable to instantiate a ComponentProxyCustomizer of type: " + customizerType);
        }

        return customizer;
    }
}
