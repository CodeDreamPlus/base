package com.codedream.auto.factories;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;

import static java.lang.String.join;
import static java.lang.System.lineSeparator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
/**
 * <p>Description: [自动配置注解类型]</p >
 * Created on 2022-03-17
 *
 * @author mo
 */
@SupportedAnnotationTypes("*")
@SupportedOptions("debug")
public class SpringFactoryProcessor extends AbstractProcessor {
    /**
     * The location to look for factories.
     * <p>Can be present in multiple JAR files.
     */
    private static final String FACTORIES_RESOURCE_LOCATION = "META-INF/spring.factories";
    /**
     * 数据承载
     */
    final protected Map<String, Set<String>> factories = new HashMap<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    /**
     * 这至关于每一个处理器的主函数main()。你在这里写你的扫描、评估和处理注解的代码，以及生成Java文件。
     *
     * @param annotations 请求处理的注解类型集合
     * @param roundEnv    RoundEnviroment，可让你查询出包含特定注解的被注解元素，至关于“有关全局源码的上下文环境”。
     * @return 若是返回 true，则这些注解已声明而且不要求后续 Processor 处理它们；若是返回 false，则这些注解未声明而且可能要求后续 Processor 处理它们
     */
    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        processAnnotations(annotations, roundEnv);
        // 处理至最后一轮时会进入此方法
        if (roundEnv.processingOver()) {
            try {
                writeSpringFactoriesFile();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to write META-INF/spring.factories", e);
            }
        }
        //这里必须为false，因为@SupportedAnnotationTypes("*")是对所有注解进行处理，返回false后面的lombokProcessor还会进行处理，如果返回true后将编译不通过
        return false;
    }

    /**
     * 注解处理
     *
     * @param annotations 请求处理的注释类型
     * @param roundEnv    有关当前和上一轮信息的环境
     */
    protected void processAnnotations(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        annotations.stream()
                .map(roundEnv::getElementsAnnotatedWith)
                .flatMap(Collection::stream)
                .filter(this::isClassOrInterface)
                .filter(e -> e instanceof TypeElement)
                .map(e -> (TypeElement) e)
                .forEach((x) -> {
                    if (isAnnotation(x, AutoTypeEnum.COMPONENT.getAnnotationName())) {
                        factories.computeIfAbsent(AutoTypeEnum.COMPONENT.getConfigureKey(), (ignored) -> new HashSet<>()).add(x.toString());
                    }
                });
    }

    /**
     * 向SpringFactories文件写入配置
     *
     * @throws IOException
     */
    protected void writeSpringFactoriesFile() throws IOException {
        if (factories.isEmpty()) {
            return;
        }
        final FileObject resource = processingEnv.getFiler().createResource(CLASS_OUTPUT, "", FACTORIES_RESOURCE_LOCATION);
        try (final OutputStream output = resource.openOutputStream(); final PrintStream printStream = new PrintStream(output, false, UTF_8.name())) {
            factories.forEach((k, v) -> {
                printStream.println(k + "=\\");
                printStream.println(join(",\\" + lineSeparator(), v));
            });
        }
    }

    /**
     * 最大支持的源码等级
     *
     * @return {@link SourceVersion}
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * 是否是目标注解
     *
     * @param e                  当前元素
     * @param annotationFullName 目标注解名称
     * @return
     */
    private boolean isAnnotation(Element e, String annotationFullName) {
        List<? extends AnnotationMirror> annotationList = e.getAnnotationMirrors();
        for (AnnotationMirror annotation : annotationList) {
            // 如果是对于的注解
            if (isAnnotation(annotationFullName, annotation)) {
                return true;
            }
            // 处理组合注解
            Element element = annotation.getAnnotationType().asElement();
            // 如果是 java 元注解，继续循环
            if (element.toString().startsWith("java.lang")) {
                continue;
            }
            // 递归处理 组合注解
            if (isAnnotation(element, annotationFullName)) {
                return true;
            }
        }
        return false;
    }


    /**
     * 注解是否匹配
     *
     * @param annotationFullName 目标注解名称
     * @param annotation         当前注解
     * @return
     */
    private boolean isAnnotation(String annotationFullName, AnnotationMirror annotation) {
        return annotationFullName.equals(annotation.getAnnotationType().toString());
    }

    /**
     * 是类或者接口
     *
     * @param e 元素
     * @return
     */
    private boolean isClassOrInterface(final Element e) {
        ElementKind kind = e.getKind();
        return kind == ElementKind.CLASS || kind == ElementKind.INTERFACE;
    }
}
