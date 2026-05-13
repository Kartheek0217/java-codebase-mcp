package com.mcp.service;

import com.mcp.entity.Symbol;
import com.mcp.entity.SymbolVector;
import com.mcp.repository.SymbolVectorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Random;

@Service
public class SemanticSearchService {

    private final SymbolVectorRepository symbolVectorRepository;
    private static final int VECTOR_DIMENSION = 128;

    public SemanticSearchService(SymbolVectorRepository symbolVectorRepository) {
        this.symbolVectorRepository = symbolVectorRepository;
    }

    @Transactional
    public void upsertSymbolVector(Symbol symbol) {
        float[] vector = generateMockVector(symbol.getName());

        SymbolVector sv = symbolVectorRepository.findAll().stream()
                .filter(v -> v.getSymbol().getId().equals(symbol.getId()))
                .findFirst()
                .orElse(new SymbolVector());

        sv.setSymbol(symbol);
        sv.setVector(vector);
        sv.setContent("Semantic representation of " + symbol.getType() + ": " + symbol.getName());

        symbolVectorRepository.save(sv);
    }

    public List<SymbolVector> searchSemantic(String query, int k) {
        float[] queryVector = generateMockVector(query);
        byte[] vectorBytes = floatArrayToByteArray(queryVector);
        return symbolVectorRepository.findNearestNeighbors(vectorBytes, k);
    }

    @Transactional
    public void deleteVectorsByFile(Long projectId, String filePath) {
        symbolVectorRepository.deleteBySymbolProjectIdAndSymbolFilePath(projectId, filePath);
    }

    /**
     * Generates a deterministic mock vector based on the input string.
     * In a real implementation, this would call an LLM or embedding model.
     */
    private float[] generateMockVector(String input) {
        float[] vector = new float[VECTOR_DIMENSION];
        Random random = new Random(input.hashCode());
        for (int i = 0; i < vector.length; i++) {
            vector[i] = random.nextFloat();
        }
        return vector;
    }

    private byte[] floatArrayToByteArray(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }
}
