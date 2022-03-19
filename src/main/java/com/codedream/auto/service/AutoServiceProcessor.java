package com.codedream.auto.service;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import javax.lang.model.util.Types;
import javax.tools.FileObject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singleton;
import static javax.tools.StandardLocation.CLASS_OUTPUT;

/**
 * java spi 服务自动处理器 参考：google auto
 * <p>生成spi服务文件 可通过{@link ServiceLoader#load(Class)}加载注解{@link AutoService#value()}指定的父类接口下的实现类</p>
 *
 * @author mo
 */
@SupportedOptions("debug")
public class AutoServiceProcessor extends AbstractProcessor {
    /**
     * spi 服务集合，key 接口 -> value 实现列表
     */
    private final Map<String, String> providers = new HashMap<>();

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        processAnnotations(annotations, roundEnv);
        // 处理至最后一轮时会进入此方法
        if (roundEnv.processingOver()) {
            try {
                writeAutoServiceFiles();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to write META-INF/services", e);
            }
        }
        return true;
    }

    /**
     * 获取所支持的所有注解类型
     *
     * @return {@link Set<String>}
     */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return singleton(AutoService.class.getCanonicalName());
    }

    /**
     * 注解处理
     * @param annotations 请求处理的注释类型
     * @param roundEnv    有关当前和上一轮信息的环境
     */
    private void processAnnotations(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(AutoService.class);
        for (Element e : elements) {
            TypeElement providerImplementer = (TypeElement) e;
            //获取实现类名称
            String providerImplementerName = providerImplementer.getQualifiedName().toString();
            List<? extends AnnotationMirror> annotationMirrors = providerImplementer.getAnnotationMirrors();
            for (AnnotationMirror annotationMirror : annotationMirrors) {
                //获取接口名称(需要先授权访问)
                getValueFieldOfClasses(annotationMirror).stream().forEach(x -> {
                    String providerInterfaceName = x.toString();
                    if (checkImplementer(providerImplementer, x)) {
                        providers.put(providerInterfaceName, providerImplementerName);
                    }
                });
            }
        }
    }

    /**
     * 写入META-INF.services文件
     * @throws IOException
     */
    private void writeAutoServiceFiles() throws IOException {
        if (providers.isEmpty()) {
            return;
        }
        for (String providerInterface : providers.keySet()) {
            String resourceFile = "META-INF/services/" + providerInterface;
            final FileObject resource = processingEnv.getFiler().createResource(CLASS_OUTPUT, "", resourceFile);
            try (final OutputStream output = resource.openOutputStream(); final PrintStream printStream = new PrintStream(output, false, UTF_8.name())) {
                printStream.println(providers.get(providerInterface));
            }
        }
    }

    /**
     * Verifies {@link java.util.spi.LocaleServiceProvider} constraints on the concrete provider class.
     * Note that these constraints are enforced at runtime via the ServiceLoader,
     * we're just checking them at compile time to be extra nice to our users.
     */
    private boolean checkImplementer(TypeElement providerImplementer, TypeMirror providerType) {
        // TODO: We're currently only enforcing the subtype relationship
        // constraint. It would be nice to enforce them all.
        Types types = processingEnv.getTypeUtils();

        return types.isSubtype(providerImplementer.asType(), providerType);
    }

    /**
     * 读取 AutoService 上的 value 值
     *
     * @param annotationMirror AnnotationMirror
     * @return value 集合
     */
    private Set<TypeMirror> getValueFieldOfClasses(AnnotationMirror annotationMirror) {
        return getAnnotationValue(annotationMirror, "value")
                .accept(new SimpleAnnotationValueVisitor8<Set<TypeMirror>, Void>() {
                    @Override
                    public Set<TypeMirror> visitType(TypeMirror typeMirror, Void v) {
                        Set<TypeMirror> declaredTypeSet = new HashSet<>(1);
                        declaredTypeSet.add(typeMirror);
                        return Collections.unmodifiableSet(declaredTypeSet);
                    }

                    @Override
                    public Set<TypeMirror> visitArray(
                            List<? extends AnnotationValue> values, Void v) {
                        return values
                                .stream()
                                .flatMap(value -> value.accept(this, null).stream())
                                .collect(Collectors.toSet());
                    }
                }, null);
    }

    /**
     * Returns a {@link ExecutableElement} and its associated {@link AnnotationValue} if such
     * an element was either declared in the usage represented by the provided
     * {@link AnnotationMirror}, or if such an element was defined with a default.
     *
     * @param annotationMirror AnnotationMirror
     * @param elementName      elementName
     * @return AnnotationValue map
     * @throws IllegalArgumentException if no element is defined with the given elementName.
     */
    public AnnotationValue getAnnotationValue(AnnotationMirror annotationMirror, String elementName) {
        Objects.requireNonNull(annotationMirror);
        Objects.requireNonNull(elementName);
        for (Map.Entry<ExecutableElement, AnnotationValue> entry : getAnnotationValuesWithDefaults(annotationMirror).entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(elementName)) {
                return entry.getValue();
            }
        }
        throw new IllegalArgumentException(String.format("does not define an element %s()", elementName));
    }

    /**
     * Returns the {@link AnnotationMirror}'s map of {@link AnnotationValue} indexed by {@link
     * ExecutableElement}, supplying default values from the annotation if the annotation property has
     * not been set. This is equivalent to {@link
     * Elements#getElementValuesWithDefaults(AnnotationMirror)} but can be called statically without
     * an {@link Elements} instance.
     *
     * <p>The iteration order of elements of the returned map will be the order in which the {@link
     * ExecutableElement}s are defined in {@code annotation}'s {@linkplain
     * AnnotationMirror#getAnnotationType() type}.
     *
     * @param annotation AnnotationMirror
     * @return AnnotationValue Map
     */
    public Map<ExecutableElement, AnnotationValue> getAnnotationValuesWithDefaults(AnnotationMirror annotation) {
        Map<ExecutableElement, AnnotationValue> values = new HashMap<>(32);
        Map<? extends ExecutableElement, ? extends AnnotationValue> declaredValues = annotation.getElementValues();
        for (ExecutableElement method : ElementFilter.methodsIn(annotation.getAnnotationType().asElement().getEnclosedElements())) {
            // Must iterate and put in this order, to ensure consistency in generated code.
            if (declaredValues.containsKey(method)) {
                values.put(method, declaredValues.get(method));
            } else if (method.getDefaultValue() != null) {
                values.put(method, method.getDefaultValue());
            } else {

                throw new IllegalStateException(
                        "Unset annotation value without default should never happen: " + method.getSimpleName() + "()");
            }
        }
        return Collections.unmodifiableMap(values);
    }
}
