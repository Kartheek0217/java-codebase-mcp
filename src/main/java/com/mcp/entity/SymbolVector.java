package com.mcp.entity;

import jakarta.persistence.*;
import com.mcp.util.FloatArrayToByteArrayConverter;

@Entity
@Table(name = "symbol_vectors")
public class SymbolVector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "symbol_id", unique = true)
    private Symbol symbol;

    @Lob
    @Convert(converter = FloatArrayToByteArrayConverter.class)
    private float[] vector;

    @Column(columnDefinition = "TEXT")
    private String content;

    public SymbolVector() {
    }

    public SymbolVector(Symbol symbol, float[] vector, String content) {
        this.symbol = symbol;
        this.vector = vector;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Symbol getSymbol() {
        return symbol;
    }

    public void setSymbol(Symbol symbol) {
        this.symbol = symbol;
    }

    public float[] getVector() {
        return vector;
    }

    public void setVector(float[] vector) {
        this.vector = vector;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
