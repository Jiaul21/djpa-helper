# DJPA Generic Helper

Single Maven library jar for generic JPA helper APIs and `@GenerateFields` annotation processing.

## Build

```powershell
.\mvnw.cmd clean verify
```

The jar is created at:

```text
target/djpa-generic-helper-1.0.0.jar
```

Install it into your local Maven repository:

```powershell
.\mvnw.cmd clean install
```

## Maven Usage

Add the library as a normal dependency:

```xml
<dependency>
    <groupId>com.djpa.generichelper</groupId>
    <artifactId>djpa-generic-helper</artifactId>
    <version>1.0.0</version>
</dependency>
```

Use the same artifact as the annotation processor:

```xml
<annotationProcessorPaths>
    <path>
        <groupId>com.djpa.generichelper</groupId>
        <artifactId>djpa-generic-helper</artifactId>
        <version>1.0.0</version>
    </path>
</annotationProcessorPaths>
```

## Annotation Usage

```java
import com.djpa.annotations.GenerateFields;

@GenerateFields
public class App {
    private Long id;
    private String name;
}
```

After compilation, the processor generates `AppFields` in the same package.
