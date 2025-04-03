package com.rj.diff.old;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.google.googlejavaformat.java.FormatterException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class ControllerDiffUpdater {

    public static void updateControllerWithDifferences(Path sourceAPath, Path sourceBPath, Path targetPath) throws IOException, FormatterException, InterruptedException {
        // 读取文件内容
        String aClassContent = new String(Files.readAllBytes(sourceAPath));
        String bClassContent = new String(Files.readAllBytes(sourceBPath));

        // 解析两个类
        JavaParser javaParser = new JavaParser();
        CompilationUnit aCu = javaParser.parse(aClassContent).getResult().get();
        CompilationUnit bCu = javaParser.parse(bClassContent).getResult().get();

        // 获取两个类的声明
        ClassOrInterfaceDeclaration aClass = aCu.getClassByName(aCu.getTypes().get(0).getNameAsString()).orElseThrow();
        ClassOrInterfaceDeclaration bClass = bCu.getClassByName(bCu.getTypes().get(0).getNameAsString()).orElseThrow();

        // 1. 找出方法差异并应用到A类
        Map<String, MethodDeclaration> methodDiffs = findMethodDifferences(aClass, bClass);
        applyMethodDifferencesToAClass(aClass, methodDiffs);

        // 2. 找出字段差异并应用到A类
        List<FieldDeclaration> fieldDiffs = findFieldDifferences(aClass, bClass);
        applyFieldDifferencesToAClass(aClass, fieldDiffs);

        // 保存修改后的A类到目标文件
        saveUpdatedClass(aCu, targetPath);
    }

    // ========== 方法比较相关 ==========
    private static Map<String, MethodDeclaration> findMethodDifferences(ClassOrInterfaceDeclaration aClass, ClassOrInterfaceDeclaration bClass) {

        Map<String, MethodDeclaration> aMethods = getMethodsByName(aClass);
        Map<String, MethodDeclaration> bMethods = getMethodsByName(bClass);
        Map<String, MethodDeclaration> differences = new HashMap<>();

        for (Map.Entry<String, MethodDeclaration> entry : bMethods.entrySet()) {
            String methodName = entry.getKey();
            MethodDeclaration bMethod = entry.getValue();

            if (aMethods.containsKey(methodName)) {
                MethodDeclaration aMethod = aMethods.get(methodName);
                if (hasParameterDifferences(aMethod, bMethod)) {
                    differences.put(methodName, bMethod);
                }
            }
        }
        return differences;
    }

    private static void applyMethodDifferencesToAClass(
            ClassOrInterfaceDeclaration aClass,
            Map<String, MethodDeclaration> methodDiffs) {

        for (Map.Entry<String, MethodDeclaration> entry : methodDiffs.entrySet()) {
            String methodName = entry.getKey();
            MethodDeclaration bMethod = entry.getValue();

            aClass.getMethods().stream()
                    .filter(m -> m.getNameAsString().equals(methodName))
                    .findFirst()
                    .ifPresent(aMethod -> {
                        List<Parameter> newParams = findNewParameters(aMethod, bMethod);
                        newParams.forEach(p -> aMethod.addParameter(p.clone()));
                    });
        }
    }

    // ========== 字段比较相关 ==========
    private static List<FieldDeclaration> findFieldDifferences(
            ClassOrInterfaceDeclaration aClass,
            ClassOrInterfaceDeclaration bClass) {

        List<FieldDeclaration> aFields = aClass.getFields();
        List<FieldDeclaration> bFields = bClass.getFields();
        List<FieldDeclaration> differences = new ArrayList<>();

        // 获取所有字段名的集合
        Set<String> aFieldNames = aFields.stream()
                .flatMap(fd -> fd.getVariables().stream())
                .map(v -> v.getNameAsString())
                .collect(Collectors.toSet());

        // 找出B类中有但A类中没有的字段
        for (FieldDeclaration bField : bFields) {
            for (VariableDeclarator bVar : bField.getVariables()) {
                if (!aFieldNames.contains(bVar.getNameAsString())) {
                    differences.add(bField);
                    break; // 只要这个FieldDeclaration中有一个变量是新的，就整个加入
                }
            }
        }

        return differences;
    }

    private static void applyFieldDifferencesToAClass(ClassOrInterfaceDeclaration aClass, List<FieldDeclaration> fieldDiffs) {
        for (FieldDeclaration diffField : fieldDiffs) {
            // 复制整个字段声明（包括注解和修饰符）
            FieldDeclaration newField = diffField.clone();
            //aClass.addMember(newField);
            aClass.getMembers().add(0, newField);

        }
    }

    // ========== 通用工具方法 ==========
    private static boolean hasParameterDifferences(MethodDeclaration aMethod, MethodDeclaration bMethod) {
        List<Parameter> aParams = aMethod.getParameters();
        List<Parameter> bParams = bMethod.getParameters();

        if (aParams.size() != bParams.size()) return true;

        for (int i = 0; i < bParams.size(); i++) {
            if (!parametersEqual(aParams.get(i), bParams.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean parametersEqual(Parameter aParam, Parameter bParam) {
        return aParam.getNameAsString().equals(bParam.getNameAsString()) &&
                aParam.getType().toString().equals(bParam.getType().toString());
    }

    private static List<Parameter> findNewParameters(MethodDeclaration aMethod, MethodDeclaration bMethod) {
        List<Parameter> aParams = aMethod.getParameters();
        return bMethod.getParameters().stream()
                .filter(bParam -> aParams.stream().noneMatch(aParam -> parametersEqual(aParam, bParam)))
                .collect(Collectors.toList());
    }

    private static Map<String, MethodDeclaration> getMethodsByName(ClassOrInterfaceDeclaration classDecl) {
        return classDecl.getMethods().stream()
                .collect(Collectors.toMap(MethodDeclaration::getNameAsString, m -> m));
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

        updateControllerWithDifferences(Paths.get(aClassContent), Paths.get(bClassContent), Paths.get(aClassContent));

        // 输出修改后的A类代码
        System.out.println(aClassContent);
    }
}