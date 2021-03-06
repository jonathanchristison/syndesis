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
package io.syndesis.core;

import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CollectionsUtils {
    private CollectionsUtils() {
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <K, V> Map<K, V> aggregate(Map<K, V>... maps) {
        return Stream.of(maps)
            .flatMap(map -> map.entrySet().stream())
            .filter(entry -> entry.getValue() != null)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <C extends Collection<T>, T> C aggregate(Supplier<C> collectionFactory, Collection<T>... collections) {
        final C result = collectionFactory.get();

        for (Collection<T> collection: collections) {
            result.addAll(collection);
        }

        return result;
    }
}
