package com.rj.diff.current.utils;

import com.github.javaparser.JavaParser;
import com.github.javaparser.Range;
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

    //private void highlightNode(Node node) {
    //    if (node.getRange().isPresent()) {
    //        int start = node.getRange().get().begin.column;
    //        int end = node.getRange().get().end.column;
    //        int line = node.getRange().get().begin.line - 1;
    //
    //        try {
    //            int lineStart = rightTextArea.getLineStartOffset(line);
    //            rightTextArea.getHighlighter().addHighlight(
    //                    lineStart + start - 1,
    //                    lineStart + end - 1,
    //                    addedPainter);
    //        } catch (Exception e) {
    //            // 忽略高亮错误
    //        }
    //    }
    //}

    private void highlightNode(Node node) throws BadLocationException {
        if (!node.getRange().isPresent()) {
            return;
        }

        // 获取节点的起始和结束行号
        int startLine = node.getRange().get().begin.line - 1;
        int endLine = node.getRange().get().end.line - 1;

        /// 处理不同类型的节点
        if (node instanceof SimpleName) {
            Node parent = node.getParentNode().orElse(null);
            if (parent instanceof MethodDeclaration) {
                MethodDeclaration method = (MethodDeclaration) parent;
                // 1. 扩展范围包含所有注解（包括注解中的注解）
                for (AnnotationExpr annotation : method.getAnnotations()) {
                    Range annotationRange = annotation.getRange().orElse(null);
                    if (annotationRange != null) {
                        startLine = Math.min(startLine, annotationRange.begin.line - 1);
                        endLine = Math.max(endLine, annotationRange.end.line - 1);

                        // 特殊处理包含数组值的注解（如@Parameters）
                        if (annotation instanceof NormalAnnotationExpr) {
                            NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
                            for (MemberValuePair pair : normalAnnotation.getPairs()) {
                                if (pair.getValue() instanceof ArrayInitializerExpr) {
                                    ArrayInitializerExpr array = (ArrayInitializerExpr) pair.getValue();
                                    for (Expression expr : array.getValues()) {
                                        if (expr.getRange().isPresent()) {
                                            endLine = Math.max(endLine, expr.getRange().get().end.line - 1);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. 包含方法体（如果存在）
                if (method.getBody().isPresent()) {
                    Range bodyRange = method.getBody().get().getRange().get();
                    endLine = Math.max(endLine, bodyRange.end.line - 1);
                }

                // 3. 包含参数列表中的所有注解
                for (Parameter parameter : method.getParameters()) {
                    for (AnnotationExpr paramAnnotation : parameter.getAnnotations()) {
                        if (paramAnnotation.getRange().isPresent()) {
                            startLine = Math.min(startLine, paramAnnotation.getRange().get().begin.line - 1);
                        }
                    }
                }
            }
        }
        // 处理注解节点（如@Parameter）
        else if (node instanceof AnnotationExpr) {
            AnnotationExpr annotation = (AnnotationExpr) node;

            // 对于多行注解，确保获取完整的结束位置
            if (annotation instanceof NormalAnnotationExpr) {
                NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
                // 获取注解的右括号位置
                if (normalAnnotation.getTokenRange().isPresent()) {
                    int closingBraceLine = normalAnnotation.getTokenRange().get()
                            .getEnd().getRange().get().begin.line - 1;
                    endLine = Math.max(endLine, closingBraceLine);
                }
            }
            // 处理单成员注解（如@Parameter(name="value")）
            else if (annotation instanceof SingleMemberAnnotationExpr) {
                SingleMemberAnnotationExpr singleAnnotation = (SingleMemberAnnotationExpr) annotation;
                // 获取完整的注解结束位置
                if (singleAnnotation.getTokenRange().isPresent()) {
                    int closingParenLine = singleAnnotation.getTokenRange().get()
                            .getEnd().getRange().get().begin.line - 1;
                    endLine = Math.max(endLine, closingParenLine);
                }
            }
            // 处理标记注解（如@Deprecated）
            else if (annotation instanceof MarkerAnnotationExpr) {
                // 不需要特殊处理，保持原样
            }
        }
        // 处理参数节点
        else if (node instanceof Parameter) {
            Parameter parameter = (Parameter) node;
            // 包含参数上的所有注解
            if (!parameter.getAnnotations().isEmpty()) {
                int firstAnnotationLine = parameter.getAnnotations().get(0).getRange().get().begin.line -1 ;
                startLine = Math.min(startLine, firstAnnotationLine);
            }
        }


        // 确保行号有效
        startLine = Math.max(0, startLine);
        endLine = Math.min(rightTextArea.getLineCount() - 1, endLine);

        // 计算高亮范围
        int highlightStart = rightTextArea.getLineStartOffset(startLine);
        int highlightEnd = rightTextArea.getLineEndOffset(endLine);

        // 添加高亮
        rightTextArea.getHighlighter().addHighlight(highlightStart, highlightEnd, addedPainter);
    }
}