/*
 * Copyright 2016 Guillaume Nodet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.cdi.extension2.support;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InjectionPoint;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class SimpleBean<T> implements Bean<T> {

    private final Class clazz;
    private final Class<? extends Annotation> scope;
    private final InjectionPoint injectionPoint;
    private final Supplier<T> supplier;

    public SimpleBean(Class clazz, Class<? extends Annotation> scope, InjectionPoint injectionPoint, Supplier<T> supplier) {
        this.clazz = clazz;
        this.scope = scope;
        this.injectionPoint = injectionPoint;
        this.supplier = supplier;
    }

    @Override
    public Class<?> getBeanClass() {
        return clazz;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return Collections.emptySet();
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public Set<Type> getTypes() {
        return Collections.singleton(injectionPoint.getType());
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return new HashSet<>(injectionPoint.getQualifiers());
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return scope;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return Collections.emptySet();
    }

    @Override
    public boolean isAlternative() {
        return false;
    }

    @Override
    public T create(CreationalContext<T> creationalContext) {
        return supplier.get();
    }

    @Override
    public void destroy(T instance, CreationalContext<T> creationalContext) {
    }
}