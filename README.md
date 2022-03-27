# codedream-base

**`Java spi`、`spring.factories`文件自动生成工具。**

## 使用场景
* 组件自动配置:比如封装了个公共组件(codedream-starter-mybatis),A项目pom.xml引入了该组件，当A项目启动自动加载该组件中MybatisPlusConfiguration这个配置类，由此可省略包扫描@ComponentScan(basePackages = {"com.codedream.mybatis"})
* 编译阶段自动生成spi配置文件，多用于公共包提供统一接口规范，各模块或各服务具体实现并应用，比如spi经典案例 **jdbc的设计**,spi还可实现根据环境profile动态读取响应配置，案例可参见codedream-base-demo

## 主要特性
* 编译时自动生成 `java spi`文件
* 编译时自动生成 `spring.factories`文件

## 使用方式

#### 1.引入

在项目的pom.xml引入codedream-base依赖，如下：

```xml
<dependency>
    <groupId>com.codedream</groupId>
    <artifactId>codedream-base</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

#### 2.使用

##### 2.1 生成spring.factories

使用以下的注解或以下注解标注过的注解，标注类文件，会生成对应的接口名称
| 注解 | 生成接口名 |备注|
| :----:| :----: |:---:|
|@Component|org.springframework.boot.autoconfigure.EnableAutoConfiguration|`@Component`注解和`@Component`标注过的注解(例如：`@Configuration`等)，都可以生效|

**示例:**

```java
/**
 * <p>Description: [mybatis配置]</p >
 * Created on 2022-03-19
 *
 * @author mo
 */
@Configuration(proxyBeanMethods = false)
public class MybatisConfig {

    public MybatisConfig() {
        System.out.println("MybatisConfig容器启动初始化");
    }
}
```

**打包编译后：** 最终在target.classes.META-INF目录下生成文件`spring.factories`：

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.codedream.mybatis.MybatisConfig
```

##### 2.2生成SPI文件
生成SPI文件需要使用@AutoService注解，注解有一项必填参数，参数为接口。

```java

@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface AutoService {
    /**
     * Returns the interfaces implemented by this service provider.
     *
     * @return interface array
     */
    Class<?>[] value();
}
```

**示例：**
spi接口为CodedreamInterface
```java
/**
 * <p>Description: [spi接口]</p >
 * Created on 2022-03-18
 *
 * @author mo
 */
public interface CodedreamInterface {

    /**
     * 启动时处理配置
     */
    void start();
}
```

SPI接口实现类

```java
/**
 * <p>Description: [spi接口实现类]</p >
 * Created on 2022-03-18
 *
 * @author mo
 */
@AutoService(CodedreamInterface.class)
public class CodedreamInterfaceImpl implements CodedreamInterface {
    @Override
    public void start() {
        try {
            Properties properties = PropertiesLoaderUtils.loadAllProperties("application.properties");
            Properties props = System.getProperties();
            props.setProperty("logging.config", "classpath:spring-logback-" + properties.getProperty("spring.profiles.active") + ".xml");
            // 处理 Druid 异常提示 discard long time none received connection
            props.setProperty("druid.mysql.usePingMethod", "false");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
```

**打包编译后：**

会在`target.classes.META-INF.services`下生成父类全限定名的一个文件，以上示例最后生成的名称为`com.codedream.spiinterface.CodedreamInterface`, 文件内容为子类的全限定名：

````text
com.codedream.spia.CodedreamInterfaceImpl
````

## 设计原理

本工具包使用了Java SPI机制，通过实现`javax.annotation.processing.Processor`类，自定义了编译时的SPI插件，
Javac编译器在编译时会调用JavacProcessingEnvironment类，该类在实例化时， 会通过`ServiceLoader.load(Processor.class);`方式遍历的去加载声明SPI信息的Processor
SPI插件，找到该信息后，通过调用callProcessor方法， 最终调用SPI的process方法，调用到本工具包的方法时，会根据引用本包的项目中的注解信息，进行各类SPI文件生成。

```java
public class JavacProcessingEnvironment implements ProcessingEnvironment, Closeable {

    // 其他方法已忽略

    private boolean callProcessor(Processor proc,
                                  Set<? extends TypeElement> tes,
                                  RoundEnvironment renv) {
        Handler prevDeferredHandler = dcfh.setHandler(dcfh.userCodeHandler);
        try {
            // 最终调用
            return proc.process(tes, renv);
        } catch (ClassFinder.BadClassFile ex) {
            log.error(Errors.ProcCantAccess1(ex.sym, ex.getDetailValue()));
            return false;
        } catch (CompletionFailure ex) {
            StringWriter out = new StringWriter();
            ex.printStackTrace(new PrintWriter(out));
            log.error(Errors.ProcCantAccess(ex.sym, ex.getDetailValue(), out.toString()));
            return false;
        } catch (ClientCodeException e) {
            throw e;
        } catch (Throwable t) {
            throw new AnnotationProcessingError(t);
        } finally {
            dcfh.setHandler(prevDeferredHandler);
        }
    }
}
```

## 参考
* Java Spi: https://www.developer.com/java/service-provider-interface-creating-extensible-java-applications/
* Google Auto: https://github.com/google/auto
* SpringBoot自动配置: https://docs.spring.io/spring-boot/docs/2.6.4/reference/htmlsingle/#using.auto-configuration