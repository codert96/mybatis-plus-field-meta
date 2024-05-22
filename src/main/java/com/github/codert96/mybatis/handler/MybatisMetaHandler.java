package com.github.codert96.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.codert96.mybatis.MybatisMetaContext;
import com.github.codert96.mybatis.annotation.Meta;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public class MybatisMetaHandler implements MetaObjectHandler {
    private final ObjectMapper objectMapper;
    private final MybatisMetaContext mybatisMetaContext;
    private final SpelExpressionParser expressionParser;
    private final BeanFactoryResolver beanFactoryResolver;

    private final Map<Field, Meta> tableClassMetaCache = new HashMap<>();
    private final Map<Class<?>, List<Field>> tableClassFieldCache = new HashMap<>();
    private final Map<String, Method> authenticationSupportedFieldCache = new HashMap<>();

    public MybatisMetaHandler(ApplicationContext applicationContext, MybatisMetaContext mybatisMetaContext) {
        this.objectMapper = applicationContext.getBean(ObjectMapper.class);
        this.mybatisMetaContext = mybatisMetaContext;
        this.beanFactoryResolver = new BeanFactoryResolver(applicationContext);
        this.expressionParser = new SpelExpressionParser(new SpelParserConfiguration(true, true));
    }

    private void setFieldValue(MetaObject metaObject, Boolean override, String fieldName, Object fieldValue) {
        if (StringUtils.hasText(Objects.toString(metaObject.getValue(fieldName), null)) && !override) {
            return;
        }
        if (Objects.nonNull(fieldValue) && metaObject.hasSetter(fieldName)) {
            Class<?> getterType = metaObject.getGetterType(fieldName);
            if (ClassUtils.isAssignableValue(getterType, fieldValue)) {
                metaObject.setValue(fieldName, fieldValue);
            } else if (String.class.equals(getterType)) {
                metaObject.setValue(fieldName, fieldValue.toString());
            }
        }
    }

    private void fill(MetaObject metaObject, boolean override) {
        Object authentication = mybatisMetaContext.getAuthentication();
        Class<?> metaContextType = mybatisMetaContext.getType();
        if (authenticationSupportedFieldCache.isEmpty()) {
            Stream.of(BeanUtils.getPropertyDescriptors(mybatisMetaContext.getType()))
                    .forEach(propertyDescriptor -> {
                                Method readMethod = propertyDescriptor.getReadMethod();
                                if (Objects.nonNull(readMethod)) {
                                    authenticationSupportedFieldCache.put(propertyDescriptor.getName(), readMethod);
                                }
                            }
                    );
        }
        Object originalObject = metaObject.getOriginalObject();
        tableClassFieldCache.computeIfAbsent(
                        originalObject.getClass(),
                        clazz -> Stream.of(clazz.getDeclaredFields())
                                .map(field -> {
                                    Meta metaColumn = field.getAnnotation(Meta.class);
                                    if (Objects.nonNull(metaColumn)) {
                                        tableClassMetaCache.put(field, metaColumn);
                                        return field;
                                    }
                                    return null;
                                })
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList())
                )
                .forEach(field -> {
                            Meta metaColumn = tableClassMetaCache.get(field);
                            Method method = authenticationSupportedFieldCache.get(StringUtils.hasText(metaColumn.value()) ? metaColumn.value() : field.getName());
                            if (Objects.nonNull(method) && !StringUtils.hasText(metaColumn.el())) {
                                if (Objects.nonNull(authentication) && metaContextType.isAssignableFrom(authentication.getClass())) {
                                    Object metaColumnValue = ReflectionUtils.invokeMethod(method, authentication);
                                    Class<?> type = field.getType();
                                    if (Objects.nonNull(metaColumnValue) && !type.isAssignableFrom(metaColumnValue.getClass())) {
                                        metaColumnValue = objectMapper.convertValue(metaColumnValue, type);
                                    }
                                    setFieldValue(metaObject, override, field.getName(), metaColumnValue);
                                }
                            } else if (StringUtils.hasText(metaColumn.el())) {
                                String string = metaColumn.el();
                                if (StringUtils.hasText(string)) {
                                    StandardEvaluationContext standardEvaluationContext = new StandardEvaluationContext(originalObject);
                                    standardEvaluationContext.setBeanResolver(beanFactoryResolver);
                                    if (Objects.nonNull(authentication) && metaContextType.isAssignableFrom(authentication.getClass())) {
                                        standardEvaluationContext.setVariable("authentication", authentication);
                                    }
                                    Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                                            .ifPresent(requestAttributes -> standardEvaluationContext.setVariable("requestAttributes", requestAttributes));
                                    standardEvaluationContext.setVariable("now", LocalDateTime.now());
                                    Object value = expressionParser.parseExpression(string).getValue(standardEvaluationContext, field.getType());
                                    setFieldValue(metaObject, override, field.getName(), value);
                                }
                            }
                        }
                );
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        fill(metaObject, false);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        fill(metaObject, true);
    }

}
