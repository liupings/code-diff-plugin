package com.rj.diff.test;

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

public class ControllerDiffUpdaterTest {

    public static void updateControllerWithDifferences(Path sourceAPath, Path sourceBPath, Path targetPath) throws IOException, FormatterException, InterruptedException {
        // 读取文件内容
        String aClassContent = new String(Files.readAllBytes(sourceAPath));
        String bClassContent = new String(Files.readAllBytes(sourceBPath));

        // 解析两个类
        JavaParser javaParser = new JavaParser();
        CompilationUnit aCu = javaParser.parse(aClassContent).getResult().get();
        CompilationUnit bCu = javaParser.parse(bClassContent).getResult().get();

        // 获取两个类的声明
        ClassOrInterfaceDeclaration aClass = aCu.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow(() -> new RuntimeException("在文件中找不到类"));
        ClassOrInterfaceDeclaration bClass = bCu.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow(() -> new RuntimeException("在文件中找不到类"));

        // 1. 处理类级别的注解差异
        processClassAnnotations(aClass, bClass);

        // 2. 找出方法差异并应用到A类
        processMethodDifferences(aClass, bClass);

        // 3. 找出字段差异并应用到A类
        processFieldDifferences(aClass, bClass);

        // 保存修改后的A类到目标文件
        saveUpdatedClass(aCu, targetPath);
    }

    // ========== 类注解处理 ==========
    private static void processClassAnnotations(ClassOrInterfaceDeclaration aClass, ClassOrInterfaceDeclaration bClass) {
        // 获取B类中A类没有的注解
        List<AnnotationExpr> newAnnotations = bClass.getAnnotations().stream()
                .filter(bAnnotation -> aClass.getAnnotations().stream()
                        .noneMatch(aAnnotation -> annotationsEqual(aAnnotation, bAnnotation)))
                .collect(Collectors.toList());

        // 添加新注解到A类
        newAnnotations.forEach(aClass::addAnnotation);
    }

    // ========== 方法处理 ==========
    private static void processMethodDifferences(ClassOrInterfaceDeclaration aClass, ClassOrInterfaceDeclaration bClass) {
        // 获取B类中的所有方法
        for (MethodDeclaration bMethod : bClass.getMethods()) {
            String methodName = bMethod.getNameAsString();

            // 检查A类中是否已有该方法
            Optional<MethodDeclaration> aMethodOpt = aClass.getMethods().stream()
                    .filter(m -> m.getNameAsString().equals(methodName))
                    .findFirst();

            if (aMethodOpt.isPresent()) {
                // 方法已存在，处理参数和注解差异
                MethodDeclaration aMethod = aMethodOpt.get();
                processMethodParameters(aMethod, bMethod);
                processMethodAnnotations(aMethod, bMethod);
            } else {
                // 方法不存在，直接添加整个方法
                aClass.addMember(bMethod.clone());
            }
        }
    }

    private static void processMethodParameters(MethodDeclaration aMethod, MethodDeclaration bMethod) {
        // 获取B方法中A方法没有的参数
        List<Parameter> newParameters = bMethod.getParameters().stream()
                .filter(bParam -> aMethod.getParameters().stream()
                        .noneMatch(aParam -> parametersEqual(aParam, bParam)))
                .collect(Collectors.toList());

        // 添加新参数到A方法
        newParameters.forEach(aMethod::addParameter);
    }

    private static void processMethodAnnotations(MethodDeclaration aMethod, MethodDeclaration bMethod) {
        // 1. 先处理 Parameters 注解（确保它排在第一位）
        Optional<AnnotationExpr> aParametersOpt = aMethod.getAnnotationByName("Parameters");
        Optional<AnnotationExpr> bParametersOpt = bMethod.getAnnotationByName("Parameters");

        // 临时存储所有注解（先移除 Parameters）
        List<AnnotationExpr> allAnnotations = new ArrayList<>(aMethod.getAnnotations());
        allAnnotations.removeIf(a -> a.getNameAsString().equals("Parameters"));

        if (bParametersOpt.isPresent()) {
            AnnotationExpr mergedParameters;
            if (aParametersOpt.isPresent()) {
                // 合并两个 Parameters 注解
                mergedParameters = mergeParametersAnnotations(aParametersOpt.get(), bParametersOpt.get());
            } else {
                // 直接使用 B 的 Parameters 注解
                mergedParameters = bParametersOpt.get().clone();
            }
            // 插入到第一位
            allAnnotations.add(0, mergedParameters);
        }

        // 2. 处理其他普通注解（不包含 Parameters）
        bMethod.getAnnotations().stream()
                .filter(bAnnotation -> !bAnnotation.getNameAsString().equals("Parameters"))
                .filter(bAnnotation -> aMethod.getAnnotations().stream()
                        .noneMatch(aAnnotation -> annotationsEqual(aAnnotation, bAnnotation)))
                .forEach(allAnnotations::add);

        // 清空原注解，并按新顺序重新添加
        aMethod.getAnnotations().clear();
        allAnnotations.forEach(aMethod::addAnnotation);
    }

    private static AnnotationExpr mergeParametersAnnotations(AnnotationExpr aParams, AnnotationExpr bParams) {
        // 获取A注解中的所有参数
        List<AnnotationExpr> aParamAnnotations = getParameterAnnotationsFromParameters(aParams);

        // 获取B注解中的所有参数
        List<AnnotationExpr> bParamAnnotations = getParameterAnnotationsFromParameters(bParams);

        // 找出B中有而A中没有的参数
        List<AnnotationExpr> newParams = bParamAnnotations.stream()
                .filter(bParam -> aParamAnnotations.stream()
                        .noneMatch(aParam -> parameterAnnotationsEqual(aParam, bParam)))
                .collect(Collectors.toList());

        // 如果没有新参数，直接返回原始注解
        if (newParams.isEmpty()) {
            return aParams.clone();
        }

        // 创建新的合并后的Parameters注解
        if (aParams instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr merged = new NormalAnnotationExpr();
            merged.setName("Parameters");

            // 创建数组初始化表达式
            ArrayInitializerExpr arrayInit = new ArrayInitializerExpr();

            // 添加A的所有参数
            aParamAnnotations.forEach(param -> {
                arrayInit.getValues().add(param.clone());
            });

            // 添加B的新参数
            newParams.forEach(param -> {
                arrayInit.getValues().add(param.clone());
            });

            // 添加数组到注解
            merged.addPair("value", arrayInit);
            return merged;
        } else if (aParams instanceof SingleMemberAnnotationExpr) {
            // 处理单成员注解的情况
            SingleMemberAnnotationExpr singleMember = (SingleMemberAnnotationExpr) aParams;
            ArrayInitializerExpr arrayInit = new ArrayInitializerExpr();

            // 添加原有参数
            if (singleMember.getMemberValue().isArrayInitializerExpr()) {
                singleMember.getMemberValue().asArrayInitializerExpr().getValues()
                        .forEach(v -> arrayInit.getValues().add(v.clone()));
            } else {
                arrayInit.getValues().add(singleMember.getMemberValue().clone());
            }

            // 添加新参数
            newParams.forEach(param -> arrayInit.getValues().add(param.clone()));

            SingleMemberAnnotationExpr merged = new SingleMemberAnnotationExpr();
            merged.setName("Parameters");
            merged.setMemberValue(arrayInit);
            return merged;
        }

        return aParams.clone();
    }

    private static List<AnnotationExpr> getParameterAnnotationsFromParameters(AnnotationExpr parameters) {
        if (parameters instanceof NormalAnnotationExpr) {
            return ((NormalAnnotationExpr) parameters).getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals("value"))
                    .flatMap(pair -> {
                        if (pair.getValue().isArrayInitializerExpr()) {
                            return pair.getValue().asArrayInitializerExpr().getValues().stream();
                        }
                        return Stream.of(pair.getValue());
                    })
                    .map(expr -> (AnnotationExpr) expr)
                    .collect(Collectors.toList());
        } else if (parameters instanceof SingleMemberAnnotationExpr) {
            SingleMemberAnnotationExpr smae = (SingleMemberAnnotationExpr) parameters;
            if (smae.getMemberValue().isArrayInitializerExpr()) {
                return smae.getMemberValue().asArrayInitializerExpr().getValues().stream()
                        .map(expr -> (AnnotationExpr) expr)
                        .collect(Collectors.toList());
            } else {
                return Collections.singletonList((AnnotationExpr) smae.getMemberValue());
            }
        }
        return Collections.emptyList();
    }

    private static boolean parameterAnnotationsEqual(AnnotationExpr a, AnnotationExpr b) {
        if (!(a instanceof NormalAnnotationExpr) || !(b instanceof NormalAnnotationExpr)) {
            return false;
        }

        NormalAnnotationExpr aParam = (NormalAnnotationExpr) a;
        NormalAnnotationExpr bParam = (NormalAnnotationExpr) b;

        // 比较参数名
        Optional<String> aName = getParameterName(aParam);
        Optional<String> bName = getParameterName(bParam);

        return aName.isPresent() && bName.isPresent() && aName.get().equals(bName.get());
    }

    private static Optional<String> getParameterName(NormalAnnotationExpr param) {
        return param.getPairs().stream()
                .filter(p -> p.getNameAsString().equals("name"))
                .findFirst()
                .map(p -> p.getValue().asStringLiteralExpr().getValue());
    }

    // ========== 字段处理 ==========
    private static void processFieldDifferences(ClassOrInterfaceDeclaration aClass, ClassOrInterfaceDeclaration bClass) {
        // 获取A类中所有字段名
        Set<String> aFieldNames = aClass.getFields().stream()
                .flatMap(fd -> fd.getVariables().stream())
                .map(v -> v.getNameAsString())
                .collect(Collectors.toSet());

        // 处理B类中的每个字段
        for (FieldDeclaration bField : bClass.getFields()) {
            // 检查字段是否已存在于A类
            boolean fieldExists = bField.getVariables().stream()
                    .anyMatch(v -> aFieldNames.contains(v.getNameAsString()));

            if (!fieldExists) {
                // 字段不存在，添加整个字段声明
                aClass.getMembers().add(0, bField.clone());

            }
        }
    }

    // ========== 比较工具方法 ==========
    private static boolean parametersEqual(Parameter aParam, Parameter bParam) {
        return aParam.getNameAsString().equals(bParam.getNameAsString()) &&
                aParam.getType().toString().equals(bParam.getType().toString());
    }

    private static boolean annotationsEqual(AnnotationExpr a, AnnotationExpr b) {
        if (a.getClass() != b.getClass()) return false;

        // 简单注解
        if (a instanceof MarkerAnnotationExpr) {
            return a.getNameAsString().equals(b.getNameAsString());
        }
        // 单成员注解
        else if (a instanceof SingleMemberAnnotationExpr) {
            SingleMemberAnnotationExpr aSm = (SingleMemberAnnotationExpr) a;
            SingleMemberAnnotationExpr bSm = (SingleMemberAnnotationExpr) b;
            return aSm.getNameAsString().equals(bSm.getNameAsString()) &&
                    aSm.getMemberValue().toString().equals(bSm.getMemberValue().toString());
        }
        // 普通注解
        else if (a instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr aN = (NormalAnnotationExpr) a;
            NormalAnnotationExpr bN = (NormalAnnotationExpr) b;

            if (!aN.getNameAsString().equals(bN.getNameAsString())) return false;

            // 比较注解的键值对
            Map<String, String> aPairs = aN.getPairs().stream()
                    .collect(Collectors.toMap(p -> p.getNameAsString(), p -> p.getValue().toString()));
            Map<String, String> bPairs = bN.getPairs().stream()
                    .collect(Collectors.toMap(p -> p.getNameAsString(), p -> p.getValue().toString()));

            return aPairs.equals(bPairs);
        }

        return false;
    }

    private static void saveUpdatedClass(CompilationUnit cu, Path targetPath) throws IOException, InterruptedException {
        Files.createDirectories(targetPath.getParent());
        String format = JavaFormatterUtils.format(cu.toString());
        Files.write(targetPath, format.getBytes());
        System.out.println("Successfully updated and saved to: " + targetPath);
    }

    public static void main(String[] args) throws IOException, FormatterException, InterruptedException {
        String aClassContent = "D:\\atest\\AControllerPingaaaaaaController.java";
        String bClassContent = "D:\\atest\\AControllerPingbbbbbbController.java";

        updateControllerWithDifferences(Paths.get(aClassContent), Paths.get(bClassContent), Paths.get(aClassContent));
        System.out.println("Update completed.");
    }
}