package com.transfer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

public class OpenAIChat {

    String m_chatgpt_convert_script_path;

    EntityManagerFactory m_emf;
    EntityManager m_em;
    CriteriaBuilder m_cb;

    String m_original_dialect, m_target_dialect;

    static final int STMTS_WAIT_TO_TRANS_LIMIT = 1;
    static final int STMT_MAX_LENGTH = 500;

    public OpenAIChat(String _original_dialect, String _target_dialect) {
        
        // Prepare chatgpt script.
        
        InputStream scriptStream = OpenAIChat.class.getResourceAsStream("/scripts/chatgpt_convert_v2.sh");
        File tempScript = null;

        try {
            tempScript = File.createTempFile("temp_script", ".sh");
            Files.copy(scriptStream, tempScript.toPath(), StandardCopyOption.REPLACE_EXISTING);
            tempScript.setExecutable(true);
            tempScript.deleteOnExit();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        // Prepare persistent component.

        m_chatgpt_convert_script_path = tempScript.getAbsolutePath();
        m_emf = Persistence.createEntityManagerFactory("myPersistenceUnit");
        m_em = m_emf.createEntityManager();
        m_cb = m_em.getCriteriaBuilder();

        // Prepare dialect.

        m_original_dialect = _original_dialect;
        m_target_dialect = _target_dialect;
    }

    /* return null if translate failed. */
    public String[] query_sql_stmts_translate_v2(String[] stmts)
            throws IOException, InterruptedException {
        
        String begin_part = String.format("The following is SQL statements in %s.\n\n", m_original_dialect);
        String front_splitter = "1\n```SQL\n";
        String tail_splitter = "\n```\n\n";
        String end_part = String.format("The equivalent SQL statements in %s?\n", m_target_dialect);
        String weak_front_splitter = "```SQL";
        String weak_tail_splitter = "```";

        String prompt = begin_part + front_splitter + String.join(tail_splitter + front_splitter, stmts) + tail_splitter
                + end_part;

        String resp = CommandRunner.run("timeout", "60s", m_chatgpt_convert_script_path, prompt, m_original_dialect,
                m_target_dialect);

        String[] stmts_split_step1 = resp.split(weak_front_splitter, -1);
        
        List<String> stmts_split_step2_list = new ArrayList<String>();

        for (String stmt : stmts_split_step1) {
            String[] stmt_splitted = stmt.split(weak_tail_splitter);
            if (stmt_splitted.length == 2) {
                stmts_split_step2_list.add(stmt_splitted[0]);
            }
        }

        String[] stmts_after_trans = stmts_split_step2_list.toArray(new String[0]);

        if (stmts_after_trans.length == 0) {
            System.out.println("[OpenAI error message]: " + resp);
        }

        return stmts_after_trans;
    }

    public void handle_stmts_wait_to_trans(List<String> stmts_wait_to_trans, List<String> stmts_after_trans_list)
            throws IOException, InterruptedException {
        if (!stmts_wait_to_trans.isEmpty()) {
            String[] stmts_wait_to_trans_array = stmts_wait_to_trans.toArray(new String[0]);
            String[] wait_stmts_after_trans = query_sql_stmts_translate_v2(
                    stmts_wait_to_trans_array);

            if (stmts_wait_to_trans_array.length == wait_stmts_after_trans.length) {
                /* persistent */
                for (int i = 0; i < stmts_wait_to_trans_array.length; ++i) {
                    m_em.getTransaction().begin();
                    OpenAIQueryResult r = new OpenAIQueryResult();
                    r.setOriginal_dialect(m_original_dialect);
                    r.setTarget_dialect(m_target_dialect);
                    r.setOriginal_stmt(stmts_wait_to_trans_array[i]);
                    r.setTarget_stmt(wait_stmts_after_trans[i]);
                    m_em.persist(r);
                    m_em.getTransaction().commit();
                }
            }

            String thread_name = Thread.currentThread().getName();
            System.out.println(String.format("[%s][OpenAI per translate] count of stmts: %4d -> %4d",
                    thread_name, stmts_wait_to_trans.size(), wait_stmts_after_trans.length));

            stmts_after_trans_list.addAll(Arrays.asList(wait_stmts_after_trans));
            stmts_wait_to_trans.clear();
        }
    }

    public String[] query_sql_stmts_translate_v2_persistence(String[] original_stmts, String original_dialect,
            String target_dialect)
            throws IOException, InterruptedException {
        
        List<String> stmts_after_trans_list = new ArrayList<String>();
        List<String> stmts_wait_to_trans = new ArrayList<String>();

        for (String original_stmt : original_stmts) {
            CriteriaQuery<OpenAIQueryResult> cq = m_cb.createQuery(OpenAIQueryResult.class);
            Root<OpenAIQueryResult> openai_query_result_root = cq.from(OpenAIQueryResult.class);

            cq.select(openai_query_result_root)
                    .where(
                            m_cb.and(m_cb.equal(openai_query_result_root.get("original_dialect"), original_dialect),
                                    m_cb.equal(openai_query_result_root.get("target_dialect"), target_dialect),
                                    m_cb.equal(openai_query_result_root.get("original_stmt"), original_stmt)));
            
            TypedQuery<OpenAIQueryResult> query = m_em.createQuery(cq);
            List<OpenAIQueryResult> openai_result_list = query.getResultList();
            
            if (original_stmt.length() > STMT_MAX_LENGTH) {
                // ignore too large stmt.
                stmts_after_trans_list.add(original_stmt);
            } else if (openai_result_list.size() == 0) {
                // query chatgpt.
                stmts_wait_to_trans.add(original_stmt);
            } else {
                // from database.
                handle_stmts_wait_to_trans(stmts_wait_to_trans, stmts_after_trans_list);
                String cur_stmt_after_tran = openai_result_list.get(0).getTarget_stmt();
                stmts_after_trans_list.add(cur_stmt_after_tran);
            }

            if (stmts_wait_to_trans.size() >= STMTS_WAIT_TO_TRANS_LIMIT) {
                handle_stmts_wait_to_trans(stmts_wait_to_trans, stmts_after_trans_list);
            }
        }

        handle_stmts_wait_to_trans(stmts_wait_to_trans, stmts_after_trans_list);

        return stmts_after_trans_list.toArray(new String[0]);
    }
}