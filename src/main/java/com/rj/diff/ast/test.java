package com.rj.diff.ast;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class test {

    @Test
    public void test() throws IOException {
        // 读取 Java 文件
        File file1 = new File("C:\\Users\\Administrator\\IdeaProjects\\test\\src\\AControllerPingaaaaaaController.java");

        // 解析 AST
        JavaParser javaParser = new JavaParser();
        ParseResult<CompilationUnit> parseResult = javaParser.parse(file1);

        if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
            CompilationUnit cu = parseResult.getResult().get();

            // 查找方法 aaa()
            cu.findAll(MethodDeclaration.class).stream()
                    .filter(m -> "aaa".equals(m.getNameAsString()) && m.getParameters().isEmpty()) // 只修改无参方法
                    .forEach(m -> m.addParameter(new Parameter(new PrimitiveType(PrimitiveType.Primitive.INT), "age")));

            // 将修改后的 AST 写回文件
            Files.write(file1.toPath(), cu.toString().getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("已成功修改方法 aaa()，添加参数 int age");
        } else {
            System.out.println("解析 A.java 失败");
        }
    }

    public static void main(String[] args) throws FileNotFoundException {
        // 读取两个 Java 文件
        File file1 = new File("D:\\idea_project\\code-diff-plugin\\src\\main\\java\\com\\rj\\diff\\ast\\A.java");
        File file2 = new File("D:\\idea_project\\code-diff-plugin\\src\\main\\java\\com\\rj\\diff\\ast\\B.java");

        // 解析 AST
        JavaParser javaParser = new JavaParser();
        ParseResult<CompilationUnit> cu1 = javaParser.parse(file1);
        ParseResult<CompilationUnit> cu2 = javaParser.parse(file2);

        // 进行差异对比
        //compareAST(cu1.getResult().get(), cu2.getResult().get());

        ASTDiffUtil.compareMethods(cu1.getResult().get(), cu2.getResult().get());
    }

    private static void compareAST(CompilationUnit oldVersion, CompilationUnit newVersion) {
        // 直接将 AST 转换为字符串，方便做文本比对（可替换为更细粒度的 AST 级别对比）
        String oldCode = new DefaultPrettyPrinter().print(oldVersion);
        String newCode = new DefaultPrettyPrinter().print(newVersion);

        System.out.println("==== 旧版本代码 ====");
        System.out.println(oldCode);

        System.out.println("\n==== 新版本代码 ====");
        System.out.println(newCode);

        // 简单字符串比对（更好的方式是使用 Diff 工具进行 AST 级别对比）
        if (oldCode.equals(newCode)) {
            System.out.println("代码没有变化");
        } else {
            System.out.println("代码有改动");
        }
    }
}

