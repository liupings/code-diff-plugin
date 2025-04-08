package com.rj.diff.current;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.google.googlejavaformat.java.FormatterException;
import com.intellij.openapi.project.Project;
import com.rj.diff.CodeDiffNotifications;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AstDiffUpdater {

    private static Log log = LogFactory.get(AstDiffUpdater.class);

    public static void updateControllerWithDifferences(Path sourceAPath, Path sourceBPath) throws IOException, InterruptedException {
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

        // 首先处理import语句（只添加不覆盖）
        processImports(aCu, bCu);

        //  处理类注解（仅添加B有而A没有的）
        addMissingClassAnnotations(aClass, bClass);

        // 处理方法差异：添加新方法，或为已有方法添加新参数/注解
        processMethodDifferences(aClass, bClass);

        // 处理字段差异（仅添加B有而A没有的）
        addMissingFields(aClass, bClass);

        // 保存回目标文件
        saveUpdatedClass(aCu, sourceAPath);
    }

    //A是目标；B是源
    public static String updateControllerWithDifferences(Project project,String sourceInfo, String targetInfo) {
        // 解析两个类
        JavaParser javaParser = new JavaParser();
        CompilationUnit tagetCu = javaParser.parse(targetInfo).getResult().get();
        CompilationUnit sourceCu = javaParser.parse(sourceInfo).getResult().get();

        ClassOrInterfaceDeclaration targetClass = null;
        ClassOrInterfaceDeclaration sourceClass = null;
        try {
            targetClass = tagetCu.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow(() -> new RuntimeException("在文件中找不到类"));
            sourceClass = sourceCu.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow(() -> new RuntimeException("在文件中找不到类"));
        } catch (RuntimeException e) {
            CodeDiffNotifications.showError(project,"错误","代码错误，AST无法解析！！！");
            e.printStackTrace();
        }

        // 首先处理import语句（只添加不覆盖）
        processImports(tagetCu, sourceCu);

        //  处理类注解（仅添加B有而A没有的）
        addMissingClassAnnotations(targetClass, sourceClass);

        // 处理方法差异：添加新方法，或为已有方法添加新参数/注解
        processMethodDifferences(targetClass, sourceClass);

        // 处理字段差异（仅添加B有而A没有的）
        addMissingFields(targetClass, sourceClass);

        // 保存回目标文件
        String format = JavaFormatterUtils.format(tagetCu.toString());

        log.info("AST解析并覆盖目标完成....{}", format.length());
        return format;
    }

    // 处理import语句（核心新增方法）
    private static void processImports(CompilationUnit aCu, CompilationUnit bCu) {
        // 获取A文件已有的import
        Set<String> existingImports = aCu.getImports().stream()
                .map(ImportDeclaration::toString)
                .collect(Collectors.toSet());

        // 添加目标文件中有而A文件没有的import
        bCu.getImports().stream()
                .filter(bImport -> !existingImports.contains(bImport.toString()))
                .forEach(aCu::addImport);
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
        // 1. 首先处理Parameters注解合并
        Optional<AnnotationExpr> aParameters = aMethod.getAnnotationByName("Parameters");
        Optional<AnnotationExpr> bParameters = bMethod.getAnnotationByName("Parameters");

        // 创建新的注解列表
        List<AnnotationExpr> mergedAnnotations = new ArrayList<>();

        // 处理Parameters注解
        if (aParameters.isPresent() || bParameters.isPresent()) {
            if (aParameters.isPresent() && bParameters.isPresent()) {
                // 合并两个Parameters注解
                //name不同：必定作为新参数添加
                //name相同但schema.type不同：也作为新参数添加
                //只有name和schema.type都相同时才视为重复参数
                mergedAnnotations.add(mergeParametersAnnotations(aParameters.get(), bParameters.get()));
            } else {
                // 使用存在的那个Parameters注解
                mergedAnnotations.add(aParameters.orElse(bParameters.get()).clone());
            }
        }

        // 2. 添加其他非Parameters注解（排除重复）
        // 先添加A方法的其他注解
        aMethod.getAnnotations().stream()
                .filter(a -> !a.getNameAsString().equals("Parameters"))
                .filter(a -> !containsAnnotation(mergedAnnotations, a))
                .forEach(mergedAnnotations::add);

        // 再添加B方法的新注解
        bMethod.getAnnotations().stream()
                .filter(b -> !b.getNameAsString().equals("Parameters"))
                .filter(b -> !containsAnnotation(mergedAnnotations, b))
                .filter(b -> !containsAnnotation(aMethod.getAnnotations(), b))
                .forEach(mergedAnnotations::add);

        // 3. 重新设置所有注解
        aMethod.getAnnotations().clear();
        mergedAnnotations.forEach(aMethod::addAnnotation);
    }

    // 辅助方法：检查注解列表中是否包含等效注解
    private static boolean containsAnnotation(Collection<AnnotationExpr> annotations, AnnotationExpr target) {
        return annotations.stream().anyMatch(a -> annotationsEqual(a, target));
    }

    // 合并两个Parameters注解的实现
    private static AnnotationExpr mergeParametersAnnotations(AnnotationExpr aParams, AnnotationExpr bParams) {
        List<AnnotationExpr> aParamList = extractParameterAnnotations(aParams);
        List<AnnotationExpr> bParamList = extractParameterAnnotations(bParams);

        // 创建合并后的参数列表（先包含A的所有参数）
        List<AnnotationExpr> mergedParams = new ArrayList<>(aParamList);

        // 处理B的参数
        bParamList.forEach(bParam -> {
            // 检查是否已存在同名同类型的参数
            boolean isDuplicate = aParamList.stream().anyMatch(aParam ->
                    parameterNamesEqual(aParam, bParam) &&
                            parameterSchemasEqual(aParam, bParam)
            );

            // 如果不是重复参数则添加
            if (!isDuplicate) {
                mergedParams.add(bParam.clone());
            }
        });

        // 构建合并后的@Parameters注解
        NormalAnnotationExpr merged = new NormalAnnotationExpr();
        merged.setName("Parameters");

        ArrayInitializerExpr arrayInit = new ArrayInitializerExpr();
        mergedParams.forEach(p -> arrayInit.getValues().add(p.clone()));
        merged.addPair("value", arrayInit);

        return merged;
    }

    // 辅助比较方法
    private static boolean parameterNamesEqual(AnnotationExpr a, AnnotationExpr b) {
        Optional<String> aName = getAnnotationMemberValue(a, "name");
        Optional<String> bName = getAnnotationMemberValue(b, "name");
        return aName.equals(bName);
    }

    private static boolean parameterSchemasEqual(AnnotationExpr a, AnnotationExpr b) {
        Optional<String> aType = getSchemaType(a);
        Optional<String> bType = getSchemaType(b);
        return aType.equals(bType);
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
        //if (!(a instanceof NormalAnnotationExpr) || !(b instanceof NormalAnnotationExpr)) {
        //    return false;
        //}
        //
        //Optional<String> aName = getAnnotationMemberValue(a, "name");
        //Optional<String> bName = getAnnotationMemberValue(b, "name");
        //
        //return aName.isPresent() && bName.isPresent() && aName.get().equals(bName.get());



        if (!(a instanceof NormalAnnotationExpr) || !(b instanceof NormalAnnotationExpr)) {
            return false;
        }

        NormalAnnotationExpr aParam = (NormalAnnotationExpr)a;
        NormalAnnotationExpr bParam = (NormalAnnotationExpr)b;

        // 比较name属性
        Optional<String> aName = getAnnotationMemberValue(aParam, "name");
        Optional<String> bName = getAnnotationMemberValue(bParam, "name");
        if (!aName.equals(bName)) {
            return false;
        }

        // 比较schema类型
        Optional<String> aSchemaType = getSchemaType(aParam);
        Optional<String> bSchemaType = getSchemaType(bParam);
        return aSchemaType.equals(bSchemaType);
    }

    /**
     * 从任意注解表达式中获取@Schema的type值
     * @param annotation 注解表达式（可以是NormalAnnotationExpr或SingleMemberAnnotationExpr）
     * @return Schema的type值（如果存在）
     */
    private static Optional<String> getSchemaType(AnnotationExpr annotation) {
        // 处理NormalAnnotationExpr情况（标准注解形式）
        if (annotation instanceof NormalAnnotationExpr) {
            return ((NormalAnnotationExpr) annotation).getPairs().stream()
                    .filter(p -> p.getNameAsString().equals("schema"))
                    .findFirst()
                    .flatMap(p -> extractTypeFromSchemaValue(p.getValue()));
        }
        // 处理SingleMemberAnnotationExpr情况（单值注解形式）
        else if (annotation instanceof SingleMemberAnnotationExpr) {
            return extractTypeFromSchemaValue(
                    ((SingleMemberAnnotationExpr) annotation).getMemberValue());
        }
        return Optional.empty();
    }
    /**
     * 从可能包含@Schema注解的表达式中提取type值
     */
    private static Optional<String> extractTypeFromSchemaValue(Expression value) {
        // 处理直接注解的情况
        if (value.isAnnotationExpr()) {
            AnnotationExpr schema = value.asAnnotationExpr();
            if (schema.getNameAsString().equals("Schema")) {
                return schema.getChildNodes().stream()
                        .filter(n -> n instanceof MemberValuePair)
                        .map(n -> (MemberValuePair) n)
                        .filter(p -> p.getNameAsString().equals("type"))
                        .findFirst()
                        .map(p -> p.getValue().toString());
            }
        }
        // 处理可能存在的其他表达式形式
        return Optional.empty();
    }
    /**
     * 判断是否需要添加为新的@Parameter
     */
    private static boolean shouldAddAsNewParameter(AnnotationExpr existingParam, AnnotationExpr newParam) {
        // name不同 → 需要添加
        if (!parameterNamesEqual(existingParam, newParam)) {
            return true;
        }

        // name相同但type不同 → 也需要添加
        return !parameterSchemasEqual(existingParam, newParam);
    }

    /**
     * 合并参数列表（不覆盖，类型不同视为新参数）
     */
    private static List<AnnotationExpr> mergeParameterLists(List<AnnotationExpr> aParams, List<AnnotationExpr> bParams) {
        List<AnnotationExpr> merged = new ArrayList<>(aParams);

        bParams.forEach(bParam -> {
            // 只有当不存在同名同类型参数时才添加
            if (aParams.stream().noneMatch(aParam -> !shouldAddAsNewParameter(aParam, bParam))) {
                merged.add(bParam.clone());
            }
        });

        return merged;
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

        // 收集B类中需要添加的字段（按原始顺序）
        List<FieldDeclaration> fieldsToAdd = bClass.getFields().stream()
                .filter(bField -> bField.getVariables().stream()
                        .anyMatch(v -> !aFieldNames.contains(v.getNameAsString())))
                .collect(Collectors.toList());

        // 逆序添加到最前面（保持原始声明的相对顺序）
        for (int i = fieldsToAdd.size() - 1; i >= 0; i--) {
            aClass.getMembers().add(0, fieldsToAdd.get(i).clone());
        }
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