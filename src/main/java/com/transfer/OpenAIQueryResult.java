package com.transfer;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Index;
import javax.persistence.Column;
@Entity
@Table(name = "OpenAIQueryResult", indexes = {
    @Index(name = "dialect_and_stmt", columnList = "original_dialect, target_dialect, original_stmt")
})
public class OpenAIQueryResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String original_dialect;
    private String target_dialect;
    @Column(columnDefinition = "text")
    private String original_stmt;
    @Column(columnDefinition = "text")
    private String target_stmt;

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getOriginal_dialect() {
        return original_dialect;
    }
    public void setOriginal_dialect(String original_dialect) {
        this.original_dialect = original_dialect;
    }
    public String getTarget_dialect() {
        return target_dialect;
    }
    public void setTarget_dialect(String target_dialect) {
        this.target_dialect = target_dialect;
    }
    public String getOriginal_stmt() {
        return original_stmt;
    }
    public void setOriginal_stmt(String original_stmt) {
        this.original_stmt = original_stmt;
    }
    public String getTarget_stmt() {
        return target_stmt;
    }
    public void setTarget_stmt(String target_stmt) {
        this.target_stmt = target_stmt;
    }
    
}
