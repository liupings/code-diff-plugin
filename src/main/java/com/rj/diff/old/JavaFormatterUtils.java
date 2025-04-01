package com.rj.diff.old;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class JavaFormatterUtils {

    private static final String FORMATTER_JAR = "/google-java-format-1.26.0-all-deps.jar";

    /**
     * 格式化给定的Java代码
     *
     * @param sourceCode 要格式化的Java源代码
     * @return 格式化后的代码
     * @throws IOException          如果读取格式化工具或格式化过程中出错
     * @throws InterruptedException 如果格式化过程被中断
     */
    public static String format(String sourceCode) {
        try {
            // 从resources加载格式化工具
            InputStream jarStream = JavaFormatterUtils.class.getResourceAsStream(FORMATTER_JAR);
            if (jarStream == null) {
                throw new IOException("Google Java Format tool not found in resources: " + FORMATTER_JAR);
            }

            // 创建临时文件来存储JAR内容
            java.nio.file.Path tempJar = java.nio.file.Files.createTempFile("google-java-format", ".jar");
            try {
                // 将JAR内容复制到临时文件
                java.nio.file.Files.copy(jarStream, tempJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                // 准备执行格式化命令
                ProcessBuilder processBuilder = new ProcessBuilder("java", "-jar", tempJar.toAbsolutePath().toString(), "-");

                // 启动进程
                Process process = processBuilder.start();

                // 向进程输入源代码
                try (OutputStream stdin = process.getOutputStream()) {
                    stdin.write(sourceCode.getBytes(StandardCharsets.UTF_8));
                }

                // 读取格式化后的输出
                ByteArrayOutputStream formattedOutput = new ByteArrayOutputStream();
                try (InputStream stdout = process.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = stdout.read(buffer)) != -1) {
                        formattedOutput.write(buffer, 0, bytesRead);
                    }
                }

                // 读取错误流（用于调试）
                ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
                try (InputStream stderr = process.getErrorStream()) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = stderr.read(buffer)) != -1) {
                        errorOutput.write(buffer, 0, bytesRead);
                    }
                }

                // 等待进程结束
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new IOException("Formatting failed with exit code " + exitCode + "\nError output: " + errorOutput.toString(StandardCharsets.UTF_8));
                }

                return formattedOutput.toString(StandardCharsets.UTF_8);
            } finally {
                // 删除临时文件
                try {
                    java.nio.file.Files.deleteIfExists(tempJar);
                } catch (IOException e) {
                    // 忽略删除失败
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}