package com.rj.diff.current.utils;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CodeElementDiffer {
    private final RSyntaxTextArea rightTextArea;
    private final Highlighter.HighlightPainter addedPainter;

    public CodeElementDiffer(RSyntaxTextArea rightTextArea, Highlighter.HighlightPainter addedPainter) {
        this.rightTextArea = rightTextArea;
        this.addedPainter = addedPainter;
    }

    public void highlightDifferences(String leftCode, String rightCode) {
        try {
            rightTextArea.getHighlighter().removeAllHighlights();

            JavaParser javaParser = new JavaParser();
            CompilationUnit leftCu = javaParser.parse(leftCode).getResult().orElse(null);
            CompilationUnit rightCu = javaParser.parse(rightCode).getResult().orElse(null);

            if (leftCu == null || rightCu == null) return;

            compareImports(leftCu, rightCu);

            Optional<ClassOrInterfaceDeclaration> leftClassOpt = leftCu.findFirst(ClassOrInterfaceDeclaration.class);
            Optional<ClassOrInterfaceDeclaration> rightClassOpt = rightCu.findFirst(ClassOrInterfaceDeclaration.class);

            if (leftClassOpt.isPresent() && rightClassOpt.isPresent()) {
                ClassOrInterfaceDeclaration leftClass = leftClassOpt.get();
                ClassOrInterfaceDeclaration rightClass = rightClassOpt.get();

                compareClassAnnotations(leftClass, rightClass);
                compareFields(leftClass, rightClass);
                compareMethods(leftClass, rightClass);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void compareImports(CompilationUnit leftCu, CompilationUnit rightCu) throws BadLocationException {
        Set<String> leftImports = leftCu.getImports().stream()
                .map(ImportDeclaration::getNameAsString)
                .collect(Collectors.toSet());

        for (ImportDeclaration rightImport : rightCu.getImports()) {
            if (!leftImports.contains(rightImport.getNameAsString())) {
                highlightNode(rightImport);
            }
        }
    }

    private void compareClassAnnotations(ClassOrInterfaceDeclaration leftClass,
                                         ClassOrInterfaceDeclaration rightClass) throws BadLocationException {
        Set<String> leftAnnotations = leftClass.getAnnotations().stream()
                .map(this::getAnnotationKey)
                .collect(Collectors.toSet());

        for (AnnotationExpr rightAnnotation : rightClass.getAnnotations()) {
            if (!leftAnnotations.contains(getAnnotationKey(rightAnnotation))) {
                highlightNode(rightAnnotation);
            }
        }
    }

    private void compareFields(ClassOrInterfaceDeclaration leftClass,
                               ClassOrInterfaceDeclaration rightClass) throws BadLocationException {
        Set<String> leftFields = leftClass.getFields().stream()
                .flatMap(f -> f.getVariables().stream())
                .map(v -> v.getNameAsString() + ":" + v.getTypeAsString())
                .collect(Collectors.toSet());

        for (FieldDeclaration rightField : rightClass.getFields()) {
            boolean hasNewField = false;
            for (VariableDeclarator rightVar : rightField.getVariables()) {
                String fieldKey = rightVar.getNameAsString() + ":" + rightVar.getTypeAsString();
                if (!leftFields.contains(fieldKey)) {
                    hasNewField = true;
                    break;
                }
            }
            if (hasNewField) {
                highlightNode(rightField);
            }
        }
    }

    private void compareMethods(ClassOrInterfaceDeclaration leftClass,
                                ClassOrInterfaceDeclaration rightClass) throws BadLocationException {
        Map<String, MethodDeclaration> leftMethods = leftClass.getMethods().stream()
                .collect(Collectors.toMap(MethodDeclaration::getNameAsString, m -> m));
        // 收集左边所有方法名
        Set<String> leftMethodNames = leftClass.getMethods().stream()
                .map(MethodDeclaration::getNameAsString)
                .collect(Collectors.toSet());
        for (MethodDeclaration rightMethod : rightClass.getMethods()) {
            MethodDeclaration leftMethod = leftMethods.get(rightMethod.getNameAsString());

            String rightMethodName = rightMethod.getNameAsString();
            if (leftMethod == null) {
                // 全新方法
                highlightNode(rightMethod.getName());
            } else {
                // 对比方法细节
                compareMethodDetails(leftMethod, rightMethod);
            }
            // 如果左边没有同名方法，高亮右边的方法名
            if (!leftMethodNames.contains(rightMethodName)) {
                highlightNode(rightMethod.getName());
            }
        }
    }

    private void compareMethodDetails(MethodDeclaration leftMethod,
                                      MethodDeclaration rightMethod) throws BadLocationException {
        // 1. 对比方法注解（排除Parameters）
        compareMethodAnnotations(leftMethod, rightMethod);

        // 2. 对比方法参数
        compareMethodParameters(leftMethod, rightMethod);

        // 3. 对比Parameters注解
        compareParametersAnnotations(leftMethod, rightMethod);

        // 4. 对比返回类型
        if (!leftMethod.getType().equals(rightMethod.getType())) {
            highlightNode(rightMethod.getType());
        }
    }

    private void compareMethodAnnotations(MethodDeclaration leftMethod,
                                          MethodDeclaration rightMethod) throws BadLocationException {
        Set<String> leftAnnotations = leftMethod.getAnnotations().stream()
                .filter(a -> !a.getNameAsString().equals("Parameters"))
                .map(this::getAnnotationKey)
                .collect(Collectors.toSet());

        for (AnnotationExpr rightAnnotation : rightMethod.getAnnotations()) {
            if (!rightAnnotation.getNameAsString().equals("Parameters") &&
                    !leftAnnotations.contains(getAnnotationKey(rightAnnotation))) {
                highlightNode(rightAnnotation);
            }
        }
    }

    private void compareMethodParameters(MethodDeclaration leftMethod,
                                         MethodDeclaration rightMethod) throws BadLocationException {
        Map<String, Parameter> leftParams = leftMethod.getParameters().stream()
                .collect(Collectors.toMap(
                        p -> p.getNameAsString() + ":" + p.getTypeAsString(),
                        p -> p
                ));

        for (Parameter rightParam : rightMethod.getParameters()) {
            String paramKey = rightParam.getNameAsString() + ":" + rightParam.getTypeAsString();
            Parameter leftParam = leftParams.get(paramKey);

            if (leftParam == null) {
                // 新参数
                highlightNode(rightParam);
            } else {
                // 对比参数注解
                compareParameterAnnotations(leftParam, rightParam);
            }
        }
    }

    private void compareParameterAnnotations(Parameter leftParam,
                                             Parameter rightParam) throws BadLocationException {
        Set<String> leftAnnotations = leftParam.getAnnotations().stream()
                .map(this::getAnnotationKey)
                .collect(Collectors.toSet());

        for (AnnotationExpr rightAnnotation : rightParam.getAnnotations()) {
            if (!leftAnnotations.contains(getAnnotationKey(rightAnnotation))) {
                highlightNode(rightAnnotation);
            }
        }
    }

    private void compareParametersAnnotations(MethodDeclaration leftMethod,
                                              MethodDeclaration rightMethod) throws BadLocationException {
        Optional<AnnotationExpr> leftParamsOpt = leftMethod.getAnnotationByName("Parameters");
        Optional<AnnotationExpr> rightParamsOpt = rightMethod.getAnnotationByName("Parameters");

        if (!rightParamsOpt.isPresent()) return;

        // 提取左侧参数注解
        Set<String> leftParamKeys = new HashSet<>();
        if (leftParamsOpt.isPresent()) {
            leftParamKeys = extractParameterAnnotations(leftParamsOpt.get()).stream()
                    .map(this::getParameterAnnotationKey)
                    .collect(Collectors.toSet());
        }

        // 检查右侧参数注解
        for (AnnotationExpr rightParam : extractParameterAnnotations(rightParamsOpt.get())) {
            String paramKey = getParameterAnnotationKey(rightParam);
            if (!leftParamKeys.contains(paramKey)) {
                highlightNode(rightParam);
            }
        }
    }

    private String getAnnotationKey(AnnotationExpr annotation) {
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normal = (NormalAnnotationExpr) annotation;
            return normal.getNameAsString() + ":" +
                    normal.getPairs().stream()
                            .sorted(Comparator.comparing(MemberValuePair::getNameAsString))
                            .map(p -> p.getNameAsString() + "=" + p.getValue())
                            .collect(Collectors.joining(","));
        }
        return annotation.toString();
    }

    private String getParameterAnnotationKey(AnnotationExpr annotation) {
        StringBuilder key = new StringBuilder(annotation.getNameAsString());

        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normal = (NormalAnnotationExpr) annotation;

            // 按属性名排序以保证一致性
            normal.getPairs().stream()
                    .sorted(Comparator.comparing(MemberValuePair::getNameAsString))
                    .forEach(p -> {
                        key.append(":").append(p.getNameAsString()).append("=");

                        if (p.getNameAsString().equals("schema") && p.getValue().isAnnotationExpr()) {
                            // 特殊处理schema注解
                            AnnotationExpr schema = p.getValue().asAnnotationExpr();
                            schema.getChildNodes().stream()
                                    .filter(n -> n instanceof MemberValuePair)
                                    .map(n -> (MemberValuePair) n)
                                    .filter(mvp -> mvp.getNameAsString().equals("type"))
                                    .findFirst()
                                    .ifPresent(mvp -> key.append(mvp.getValue()));
                        } else {
                            key.append(p.getValue());
                        }
                    });
        }

        return key.toString();
    }

    private List<AnnotationExpr> extractParameterAnnotations(AnnotationExpr parameters) {
        if (parameters instanceof NormalAnnotationExpr) {
            return ((NormalAnnotationExpr) parameters).getPairs().stream()
                    .filter(p -> p.getNameAsString().equals("value"))
                    .flatMap(p -> {
                        if (p.getValue().isArrayInitializerExpr()) {
                            return p.getValue().asArrayInitializerExpr().getValues().stream();
                        }
                        return Stream.of(p.getValue());
                    })
                    .filter(e -> e instanceof AnnotationExpr)
                    .map(e -> (AnnotationExpr) e)
                    .collect(Collectors.toList());
        } else if (parameters instanceof SingleMemberAnnotationExpr) {
            Expression value = ((SingleMemberAnnotationExpr) parameters).getMemberValue();
            if (value.isArrayInitializerExpr()) {
                return value.asArrayInitializerExpr().getValues().stream()
                        .filter(e -> e instanceof AnnotationExpr)
                        .map(e -> (AnnotationExpr) e)
                        .collect(Collectors.toList());
            } else if (value instanceof AnnotationExpr) {
                return Collections.singletonList((AnnotationExpr) value);
            }
        }
        return Collections.emptyList();
    }

    private void highlightNode(Node node) throws BadLocationException {
        if (node.getRange().isPresent()) {
            int start = node.getRange().get().begin.column;
            int end = node.getRange().get().end.column;
            int line = node.getRange().get().begin.line - 1;

            try {
                int lineStart = rightTextArea.getLineStartOffset(line);
                rightTextArea.getHighlighter().addHighlight(
                        lineStart + start - 1,
                        lineStart + end - 1,
                        addedPainter);
            } catch (Exception e) {
                // 忽略高亮错误
            }
        }
    }
}