package com.transfer;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.LogManager;
import java.sql.*;
import java.io.IOException;
import java.io.File;
import java.io.FileWriter;

/**
 * Hello world!
 *
 */
public class App {
    static String readFromFile(String fileName) throws IOException {
        String fileContent = new String(Files.readAllBytes(Paths.get(fileName)));
            return fileContent;
    }

    public static void main(String[] args)
            throws SQLException, SQLTimeoutException, ClassNotFoundException, IOException, InterruptedException {

        LogManager.getLogManager().readConfiguration(App.class.getResourceAsStream("/logging.properties"));
        
        String input_dir = null;
        String output_dir = null;
        String origin_dialect = null;
        String target_dialect = null;

        for (int i = 0; i + 1 < args.length; ++ i) {
            if (args[i].equals("--input_dir")) {
                input_dir = args[i + 1];
            } else if (args[i].equals("--output_dir")) {
                output_dir = args[i + 1];
            } else if (args[i].equals("--origin_dialect")) {
                origin_dialect = args[i + 1];
            } else if (args[i].equals("--target_dialect")) {
                target_dialect = args[i + 1];
            } else {
                throw new RuntimeException("Unknown arg: " + args[i]);
            }
            i += 1;
        }

        File indir = new File(input_dir);
        File[] in_files = indir.listFiles();

        File dir = new File(output_dir);
        dir.mkdirs();

        System.out.println("input_dir = " + input_dir);
        System.out.println("output_dir = " + output_dir);
        System.out.println("origin_dialect = " + origin_dialect.toString());
        System.out.println("target_dialect = " + target_dialect.toString());

        // v1.
        // for (File incase : in_files) {
        //     handle_file(incase, output_dir, origin_dialect, target_dialect);
        // }

        // v2 parallel.
        
        final List<File> in_files_synchronized_list = Collections
                .synchronizedList(new ArrayList<>(Arrays.asList(in_files)));
        final String output_dir_final = output_dir;
        final String origin_dialect_final = origin_dialect;
        final String target_dialect_final = target_dialect;

        Runnable task = () -> {
            try {
                handle_file_with_chatgpt_only_multithread(
                        new OpenAIChat(origin_dialect_final, target_dialect_final),
                        in_files_synchronized_list,
                        output_dir_final, origin_dialect_final, target_dialect_final);
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        int instance_count = 100;

        Thread[] threads = new Thread[instance_count];

        for (int i = 0; i < instance_count; ++i) {
            Thread thread = new Thread(task);
            thread.setName(String.format("Thread%03d", i));
            thread.start();
            threads[i] = thread;
        }

        for (int i = 0; i < instance_count; ++ i) {
            threads[i].join();
        }
    }

    public static void handle_file_with_chatgpt_only_multithread(OpenAIChat openai,
            List<File> in_files_synchronized_list, String output_dir,
            String origin_dialect,
            String target_dialect) throws InterruptedException, IOException {
        
        while (true) {

            File incase;

            synchronized (in_files_synchronized_list) {
                if (in_files_synchronized_list.isEmpty()) {
                    // all file is finished converting.
                    break;
                } else {
                    incase = in_files_synchronized_list.get(0);
                    in_files_synchronized_list.remove(0);
                }
            }

            handle_file_with_chatgpt_only(openai, incase, output_dir, origin_dialect, target_dialect);
        }
    }

    public static void handle_file_with_chatgpt_only(OpenAIChat openai, File incase, String output_dir,
        String origin_dialect,
        String target_dialect) throws InterruptedException, IOException {
        
        File output_f = new File(output_dir + "/" + incase.getName());

        if (output_f.exists()) {
            System.out.println("[DEBUG] Translate skip: " + output_f.getAbsolutePath() + " already translated. Skip.");
            return ;
        }

        String incase_content = new String(Files.readAllBytes(incase.toPath()));
        String[] stmts = incase_content.split("\000");
        String[] stmt_after_translate = openai.query_sql_stmts_translate_v2_persistence(stmts, origin_dialect,
                target_dialect);

        StringBuilder new_tcBuilder = new StringBuilder();

        for (String stmt : stmt_after_translate) {
            if (stmt != null) {
                new_tcBuilder.append(stmt);
                new_tcBuilder.append("\n");
                new_tcBuilder.append('\000');
            }
        }

        String new_tc = new_tcBuilder.toString();

        FileWriter fw = new FileWriter(output_f);
        fw.write(new_tc);
        fw.close();

        System.out.println(String.format("count of stmts: %4d -> %4d, File name: %s", stmts.length,
                stmt_after_translate.length, incase.getName()));
    }
}
