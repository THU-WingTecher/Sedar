package com.transfer;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class CommandRunner {
    public static String run(String command, String... args) throws IOException, InterruptedException {
        List<String> commandArgs = new ArrayList<String>();
        commandArgs.add(command);
        for (String arg : args) {
            commandArgs.add(arg);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(commandArgs);
        Process process = processBuilder.start();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        InputStream inputStream = process.getInputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command exited with non-zero exit code " + exitCode);
        }

        return new String(outputStream.toByteArray());
    }
}