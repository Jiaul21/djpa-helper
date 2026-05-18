package com.djpa.processor;

import com.djpa.annotations.GenerateFields;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;

@SupportedAnnotationTypes(
        "com.djpa.annotations.GenerateFields"
)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@AutoService(Processor.class)
public class FieldsProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {

        for (Element element :
                roundEnv.getElementsAnnotatedWith(GenerateFields.class)) {

            if (!(element instanceof TypeElement typeElement)) {
                continue;
            }

            try {
                generateClass(typeElement);
            } catch (Exception e) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        e.getMessage()
                );
            }
        }

        return true;
    }

    private void generateClass(TypeElement typeElement)
            throws IOException {

        String packageName =
                processingEnv.getElementUtils()
                        .getPackageOf(typeElement)
                        .getQualifiedName()
                        .toString();

        String originalClassName =
                typeElement.getSimpleName().toString();

        String generatedClassName =
                originalClassName + "Fields";

        boolean isRecord =
                typeElement.getKind() == ElementKind.RECORD;

        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(generatedClassName)
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        classBuilder.addMethod(
                MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PRIVATE)
                        .build()
        );

        // -------------------------------
        // STEP 1: collect fields
        // -------------------------------
        List<FieldInfo> fields = new ArrayList<>();

        if (isRecord) {

            for (RecordComponentElement record :
                    typeElement.getRecordComponents()) {

                fields.add(new FieldInfo(
                        record.getSimpleName().toString(),
                        record.asType()
                ));
            }

        } else {

            for (Element enclosed :
                    typeElement.getEnclosedElements()) {

                if (enclosed.getKind() == ElementKind.FIELD) {

                    VariableElement field =
                            (VariableElement) enclosed;

                    if (field.getModifiers().contains(Modifier.STATIC)) {
                        continue;
                    }

                    fields.add(new FieldInfo(
                            field.getSimpleName().toString(),
                            field.asType()
                    ));
                }
            }
        }

        // -------------------------------
        // STEP 2: constants
        // -------------------------------
        for (FieldInfo field : fields) {

            classBuilder.addField(
                    FieldSpec.builder(
                                    String.class,
                                    field.constantName,
                                    Modifier.PUBLIC,
                                    Modifier.STATIC,
                                    Modifier.FINAL
                            )
                            .initializer("$S", field.name)
                            .build()
            );
        }

        // -------------------------------
        // STEP 3: ALL_FIELDS
        // -------------------------------
        CodeBlock.Builder listBuilder = CodeBlock.builder();
        listBuilder.add("List.of(");

        for (int i = 0; i < fields.size(); i++) {

            listBuilder.add(fields.get(i).constantName);

            if (i < fields.size() - 1) {
                listBuilder.add(", ");
            }
        }

        listBuilder.add(")");

        classBuilder.addField(
                FieldSpec.builder(
                                ParameterizedTypeName.get(
                                        ClassName.get(List.class),
                                        ClassName.get(String.class)
                                ),
                                "ALL_FIELDS",
                                Modifier.PUBLIC,
                                Modifier.STATIC,
                                Modifier.FINAL
                        )
                        .initializer(listBuilder.build())
                        .build()
        );

        // -------------------------------
        // STEP 4: getFieldMap()
        // -------------------------------
        MethodSpec.Builder method =
                MethodSpec.methodBuilder("getFieldMap")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(ParameterizedTypeName.get(
                                ClassName.get(Map.class),
                                ClassName.get(String.class),
                                ClassName.get(Object.class)
                        ))
                        .addParameter(
                                ClassName.get(typeElement),
                                "obj"
                        );

        // null check
        method.beginControlFlow("if (obj == null)");
        method.addStatement("return $T.emptyMap()", Collections.class);
        method.endControlFlow();

        method.addStatement(
                "$T<$T, $T> map = new $T<>()",
                Map.class,
                String.class,
                Object.class,
                HashMap.class
        );

        // field mapping
        for (FieldInfo field : fields) {

            String accessor;

            if (isRecord) {
                accessor = "obj." + field.name + "()";
            } else {
                accessor = "obj." + field.getterName + "()";
            }

            if (field.primitive) {
                method.addStatement("map.put($L, $L)", field.constantName, accessor);
            } else {
                method.beginControlFlow("if ($L != null)", accessor);
                method.addStatement("map.put($L, $L)", field.constantName, accessor);
                method.endControlFlow();
            }
        }

        method.addStatement("return map");

        classBuilder.addMethod(method.build());

        // -------------------------------
        // STEP 5: write file ONCE
        // -------------------------------
        JavaFile.builder(
                packageName,
                classBuilder.build()
        ).build().writeTo(processingEnv.getFiler());
    }

    private static String getterName(String fieldName, TypeMirror type) {
        String suffix =
                Character.toUpperCase(fieldName.charAt(0)) +
                        fieldName.substring(1);

        if (type.getKind() == TypeKind.BOOLEAN) {
            return "is" + suffix;
        }

        return "get" + suffix;
    }

    private static String constantName(String fieldName) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < fieldName.length(); i++) {
            char current = fieldName.charAt(i);

            if (Character.isUpperCase(current) && i > 0) {
                builder.append('_');
            }

            if (Character.isLetterOrDigit(current)) {
                builder.append(Character.toUpperCase(current));
            } else {
                builder.append('_');
            }
        }

        return builder.toString();
    }

    // -------------------------------
    // helper class
    // -------------------------------
    static class FieldInfo {
        String name;
        String constantName;
        String getterName;
        TypeMirror type;
        boolean primitive;

        FieldInfo(String name, TypeMirror type) {
            this.name = name;
            this.constantName = constantName(name);
            this.getterName = getterName(name, type);
            this.type = type;
            this.primitive = type.getKind().isPrimitive();
        }
    }
}
