package com.rj.diff.currentA;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.google.googlejavaformat.java.FormatterException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ControllerDiffUpdater1 {

    public static void updateControllerWithDifferences(Path sourceAPath, Path sourceBPath) throws IOException, FormatterException, InterruptedException {
        // 读取文件内容
        String aClassContent = new String(Files.readAllBytes(sourceAPath));
        String bClassContent = new String(Files.readAllBytes(sourceBPath));

        // 解析两个类
        JavaParser javaParser = new JavaParser();
        CompilationUnit aCu = javaParser.parse(aClassContent).getResult().get();
        CompilationUnit bCu = javaParser.parse(bClassContent).getResult().get();

        ClassOrInterfaceDeclaration aClass = aCu.findFirst(ClassOrInterfaceDeclaration.class)
                .orElseThrow(() -> new RuntimeException("在文件中找不到类"));
        ClassOrInterfaceDeclaration bClass = bCu.findFirst(ClassOrInterfaceDeclaration.class)
                .orElseThrow(() -> new RuntimeException("在文件中找不到类"));

        // 1. 处理类注解（仅添加B有而A没有的）
        addMissingClassAnnotations(aClass, bClass);

        // 2. 处理方法差异：添加新方法，或为已有方法添加新参数/注解
        processMethodDifferences(aClass, bClass);

        // 3. 处理字段差异（仅添加B有而A没有的）
        addMissingFields(aClass, bClass);

        // 保存回A文件
        saveUpdatedClass(aCu, sourceAPath);

        // 保存回A文件（不覆盖原有代码）
        saveUpdatedClass(aCu, sourceAPath);
    }
    // ========== 方法处理（核心修改） ==========
    private static void processMethodDifferences(ClassOrInterfaceDeclaration aClass, ClassOrInterfaceDeclaration bClass) {
        for (MethodDeclaration bMethod : bClass.getMethods()) {
            String methodName = bMethod.getNameAsString();
            Optional<MethodDeclaration> aMethodOpt = aClass.getMethods().stream()
                    .filter(m -> m.getNameAsString().equals(methodName))
                    .findFirst();

            if (aMethodOpt.isPresent()) {
                // 已有方法：仅添加新参数和注解，不修改方法体
                MethodDeclaration aMethod = aMethodOpt.get();
                addMissingParameters(aMethod, bMethod);
                addMissingMethodAnnotations(aMethod, bMethod);
            } else {
                // 新方法：直接添加
                aClass.addMember(bMethod.clone());
            }
        }
    }
    // 为已有方法添加缺失的参数
    private static void addMissingParameters(MethodDeclaration aMethod, MethodDeclaration bMethod) {
        bMethod.getParameters().forEach(bParam -> {
            boolean paramExists = aMethod.getParameters().stream()
                    .anyMatch(aParam -> parametersEqual(aParam, bParam));
            if (!paramExists) {
                aMethod.addParameter(bParam.clone());
            }
        });
    }

    // 为已有方法添加缺失的注解
    private static void addMissingMethodAnnotations(MethodDeclaration aMethod, MethodDeclaration bMethod) {
        //bMethod.getAnnotations().forEach(bAnnotation -> {
        //    boolean annotationExists = aMethod.getAnnotations().stream()
        //            .anyMatch(aAnnotation -> annotationsEqual(aAnnotation, bAnnotation));
        //    if (!annotationExists) {
        //        aMethod.addAnnotation(bAnnotation.clone());
        //    }
        //});
        for (AnnotationExpr bAnnotation : bMethod.getAnnotations()) {
            // 特殊处理 @Parameters 注解
            if (bAnnotation.getNameAsString().equals("Parameters")) {
                mergeParametersAnnotations(aMethod, bAnnotation);
            } else {
                // 普通注解：直接检查并添加
                boolean exists = aMethod.getAnnotations().stream()
                        .anyMatch(a -> annotationsEqual(a, bAnnotation));
                if (!exists) {
                    aMethod.addAnnotation(bAnnotation.clone());
                }
            }
        }
    }
    // 合并 @Parameters 注解
    private static void mergeParametersAnnotations(MethodDeclaration aMethod, AnnotationExpr bParameters) {
        // 查找A方法中是否已有 @Parameters 注解
        Optional<AnnotationExpr> aParametersOpt = aMethod.getAnnotationByName("Parameters");

        if (!aParametersOpt.isPresent()) {
            // 如果A方法没有 @Parameters，直接添加B的整个注解
            aMethod.addAnnotation(bParameters.clone());
            return;
        }

        // 获取A和B的 @Parameters 注解内容
        List<AnnotationExpr> aParams = extractParameterAnnotations(aParametersOpt.get());
        List<AnnotationExpr> bParams = extractParameterAnnotations(bParameters);

        // 找出B中有但A中没有的参数
        List<AnnotationExpr> newParams = bParams.stream()
                .filter(bParam -> aParams.stream().noneMatch(aParam -> parameterAnnotationsEqual(aParam, bParam)))
                .collect(Collectors.toList());

        if (newParams.isEmpty()) {
            return; // 没有新参数需要添加
        }

        // 移除旧的 @Parameters 注解
        aMethod.getAnnotations().removeIf(a -> a.getNameAsString().equals("Parameters"));

        // 创建合并后的新注解
        NormalAnnotationExpr mergedAnnotation = new NormalAnnotationExpr();
        mergedAnnotation.setName("Parameters");

        // 创建参数数组
        ArrayInitializerExpr arrayInit = new ArrayInitializerExpr();
        aParams.forEach(p -> arrayInit.getValues().add(p.clone()));
        newParams.forEach(p -> arrayInit.getValues().add(p.clone()));

        // 添加参数数组到注解
        mergedAnnotation.addPair("value", arrayInit);
        aMethod.addAnnotation(mergedAnnotation);
    }

    // 从 @Parameters 注解中提取 @Parameter 列表
    private static List<AnnotationExpr> extractParameterAnnotations(AnnotationExpr parameters) {
        if (parameters instanceof NormalAnnotationExpr) {
            return ((NormalAnnotationExpr) parameters).getPairs().stream()
                    .filter(p -> p.getNameAsString().equals("value"))
                    .flatMap(p -> {
                        if (p.getValue().isArrayInitializerExpr()) {
                            return p.getValue().asArrayInitializerExpr().getValues().stream();
                        }
                        return Stream.of(p.getValue());
                    })
                    .map(e -> (AnnotationExpr) e)
                    .collect(Collectors.toList());
        } else if (parameters instanceof SingleMemberAnnotationExpr) {
            Expression value = ((SingleMemberAnnotationExpr) parameters).getMemberValue();
            if (value.isArrayInitializerExpr()) {
                return value.asArrayInitializerExpr().getValues().stream()
                        .map(e -> (AnnotationExpr) e)
                        .collect(Collectors.toList());
            }
            return Collections.singletonList((AnnotationExpr) value);
        }
        return Collections.emptyList();
    }

    // 比较两个 @Parameter 注解是否相同（根据name判断）
    private static boolean parameterAnnotationsEqual(AnnotationExpr a, AnnotationExpr b) {
        if (!(a instanceof NormalAnnotationExpr) || !(b instanceof NormalAnnotationExpr)) {
            return false;
        }

        Optional<String> aName = getAnnotationMemberValue(a, "name");
        Optional<String> bName = getAnnotationMemberValue(b, "name");

        return aName.isPresent() && bName.isPresent() && aName.get().equals(bName.get());
    }

    // 获取注解成员值
    private static Optional<String> getAnnotationMemberValue(AnnotationExpr annotation, String memberName) {
        if (annotation instanceof NormalAnnotationExpr) {
            return ((NormalAnnotationExpr) annotation).getPairs().stream()
                    .filter(p -> p.getNameAsString().equals(memberName))
                    .findFirst()
                    .map(p -> p.getValue().asStringLiteralExpr().getValue());
        }
        return Optional.empty();
    }
    // ========== 辅助方法 ==========
    private static boolean parametersEqual(Parameter aParam, Parameter bParam) {
        return aParam.getNameAsString().equals(bParam.getNameAsString())
                && aParam.getType().equals(bParam.getType());
    }

    //private static boolean annotationsEqual(AnnotationExpr a, AnnotationExpr b) {
    //    return a.toString().equals(b.toString()); // 简化比较逻辑
    //}
    // ========== 仅添加缺失的类注解 ==========
    private static void addMissingClassAnnotations(ClassOrInterfaceDeclaration aClass, ClassOrInterfaceDeclaration bClass) {
        bClass.getAnnotations().stream()
                .filter(bAnnotation -> aClass.getAnnotations().stream()
                        .noneMatch(aAnnotation -> annotationsEqual(aAnnotation, bAnnotation)))
                .forEach(aClass::addAnnotation);
    }

    // ========== 仅添加缺失的方法（不修改已有方法体） ==========
    private static void addMissingMethods(ClassOrInterfaceDeclaration aClass, ClassOrInterfaceDeclaration bClass) {
        // 获取A类中所有方法名
        Set<String> aMethodNames = aClass.getMethods().stream()
                .map(MethodDeclaration::getNameAsString)
                .collect(Collectors.toSet());

        // 添加B类中有而A类中没有的方法
        bClass.getMethods().stream()
                .filter(bMethod -> !aMethodNames.contains(bMethod.getNameAsString()))
                .forEach(aClass::addMember);
    }

    // ========== 仅添加缺失的字段 ==========
    private static void addMissingFields(ClassOrInterfaceDeclaration aClass, ClassOrInterfaceDeclaration bClass) {
        // 获取A类中所有字段名
        Set<String> aFieldNames = aClass.getFields().stream()
                .flatMap(fd -> fd.getVariables().stream())
                .map(VariableDeclarator::getNameAsString)
                .collect(Collectors.toSet());

        // 添加B类中有而A类中没有的字段
        bClass.getFields().stream()
                .filter(bField -> bField.getVariables().stream()
                        .anyMatch(v -> !aFieldNames.contains(v.getNameAsString())))
                .forEach(aClass::addMember);
    }

    // ========== 辅助方法（保持不变） ==========
    private static boolean annotationsEqual(AnnotationExpr a, AnnotationExpr b) {
        if (a.getClass() != b.getClass()) return false;
        return a.toString().equals(b.toString());
    }

    private static void saveUpdatedClass(CompilationUnit cu, Path targetPath) throws IOException, InterruptedException {
        Files.createDirectories(targetPath.getParent());
        //Files.write(targetPath, cu.toString().getBytes());

        //Files.write(targetPath, formattedCode.getBytes());

        String format = JavaFormatterUtils.format(cu.toString());

        Files.write(targetPath, format.getBytes());
        System.out.println("Successfully updated and saved to: " + targetPath);
    }

    public static void main(String[] args) throws IOException, FormatterException, InterruptedException {
        String aClassContent = "D:\\atest\\AControllerPingaaaaaaController.java"; // A类的完整代码
        String bClassContent = "D:\\atest\\AControllerPingbbbbbbController.java"; // A类的完整代码

        updateControllerWithDifferences(Paths.get(aClassContent), Paths.get(bClassContent));

        // 输出修改后的A类代码
        System.out.println(aClassContent);
    }
}