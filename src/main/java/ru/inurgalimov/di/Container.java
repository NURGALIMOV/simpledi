package ru.inurgalimov.di;

import ru.inurgalimov.di.annotation.Inject;
import ru.inurgalimov.di.exception.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Container {
    private final Map<String, Object> values = new HashMap<>();
    private final Map<Class<?>, Object> objects = new HashMap<>();
    private final Set<Class<?>> definitions = new HashSet<>();

    public void register(Class<?>... definitions) {
        final String badDefinitions = Arrays.stream(definitions)
                .filter(o -> o.getDeclaredConstructors().length != 1)
                .map(Class::getName)
                .collect(Collectors.joining(", "));
        if (!badDefinitions.isEmpty()) {
            throw new AmbiguousConstructorException(badDefinitions);
        }
        this.definitions.addAll(Arrays.asList(definitions));
    }

    public void register(String name, Object value) {
        if (values.containsKey(name)) {
            throw new AmbiguousValueNameException(String.format("%s with value %s", name, value.toString()));
        }
        values.put(name, value);
    }

    public void wire() {
        if (definitions.isEmpty()) {
            return;
        }
        final Set<Class<?>> tempDefinitions = new HashSet<>(definitions);
        while (!tempDefinitions.isEmpty()) {
            final Map<? extends Class<?>, Object> intermediate = getClassObjectMap(tempDefinitions);
            if (intermediate.isEmpty()) {
                throw new UnmetDependenciesException(tempDefinitions.stream()
                        .map(Class::getName)
                        .collect(Collectors.joining(", ")));
            }
            objects.putAll(intermediate);
            intermediate.entrySet()
                    .stream()
                    .map(entry -> Arrays.stream(entry.getKey()
                            .getInterfaces())
                            .collect(Collectors.toMap(Function.identity(), i -> entry.getValue())))
                    .forEach(objects::putAll);
            tempDefinitions.removeAll(intermediate.keySet());
        }
    }

    public <T> T getObject(Class<T> clazz) {
        return Optional.ofNullable((T) objects.get(clazz))
                .orElseThrow(() ->
                        new InstanceRetrievalException(String.format("Failed to get an instance of the class %s", clazz)));
    }

    private Map<? extends Class<?>, Object> getClassObjectMap(Set<Class<?>> tempDefinitions) {
        return tempDefinitions.stream()
                .map(clazz -> {
                    if (objects.containsKey(clazz)) {
                        throw new DIException(String.format("More one implementations by class %s", clazz));
                    }
                    return clazz.getDeclaredConstructors()[0];
                })
                .filter(constructor -> objects.isEmpty() ?
                        ((constructor.getParameterCount() == 0) || allParameterInValues(constructor)) :
                        allParameterInValues(constructor))
                .map(constructor -> {
                    try {
                        boolean isPublic = Modifier.isPublic(constructor.getModifiers());
                        if (!isPublic) {
                            constructor.setAccessible(true);
                        }
                        Object result = constructor.newInstance(Arrays.stream(constructor.getParameters())
                                .map(p -> Optional.ofNullable(objects.get(p.getType()))
                                        .or(() -> Optional.ofNullable(p.getAnnotation(Inject.class))
                                                .map(Inject::value)
                                                .map(values::get))
                                        .orElseThrow(() -> new UnmetDependenciesException(p.getName()))
                                )
                                .toArray());
                        if (!isPublic) {
                            constructor.setAccessible(false);
                        }
                        return result;
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                        throw new ObjectInstantiationException(e);
                    }
                })
                .collect(Collectors.toMap(Object::getClass, Function.identity()));
    }


    private boolean allParameterInValues(Constructor<?> constructor) {
        final var parameters = new HashSet<>(Arrays.asList(constructor.getParameters()));
        parameters.removeIf(p -> objects.containsKey(p.getType()));
        parameters.removeAll(parameters.stream()
                .filter(p -> p.isAnnotationPresent(Inject.class))
                .filter(p -> values.containsKey(p.getAnnotation(Inject.class).value()))
                .collect(Collectors.toList()));
        return parameters.isEmpty();
    }

}
