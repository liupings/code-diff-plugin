package com.rj.diff.ast;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ASTDiffUtil {
    public static void compareMethods(CompilationUnit oldVersion, CompilationUnit newVersion) {
        List<String> oldMethods = extractMethods(oldVersion);
        List<String> newMethods = extractMethods(newVersion);

        List<String> oldFileds = extractFields(oldVersion);
        List<String> newFileds = extractFields(newVersion);

        System.out.println("=== 对比结果 ===");
        for (String method : oldMethods) {
            if (!newMethods.contains(method)) {
                System.out.println("方法被删除:" + method);
            }
        }
        for (String method : newMethods) {
            if (!oldMethods.contains(method)) {
                System.out.println("方法新增:" + method);
            }
        }
    }

    private static List<String> extractMethods(CompilationUnit cu) {
        VoidVisitor<List<String>> methodCollector = new VoidVisitorAdapter<>() {
            @Override
            public void visit(MethodDeclaration md, List<String> collector) {
                super.visit(md, collector);
                collector.add(md.getSignature().asString());
            }
        };
        List<String> methods = new java.util.ArrayList<>();
        methodCollector.visit(cu, methods);
        return methods;
    }

    private static List<String> extractFields(CompilationUnit cu) {
        VoidVisitor<List<String>> fieldCollector = new VoidVisitorAdapter<>() {
            @Override
            public void visit(FieldDeclaration fd, List<String> collector) {
                super.visit(fd, collector);
                fd.getVariables().forEach(var -> collector.add(var.getNameAsString()));
            }
        };
        List<String> fields = new ArrayList<>();
        fieldCollector.visit(cu, fields);
        return fields;
    }
}
