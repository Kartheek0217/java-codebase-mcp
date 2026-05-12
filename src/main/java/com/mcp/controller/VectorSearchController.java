package com.mcp.controller;

import com.mcp.entity.Symbol;
import com.mcp.entity.SymbolVector;
import com.mcp.repository.SymbolRepository;
import com.mcp.repository.SymbolVectorRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Random;

@RestController
@RequestMapping("/api/vectors")
@Tag(name = "Vector Search", description = "Endpoints for testing H2 Vector similarity search")
public class VectorSearchController {

    private final SymbolVectorRepository symbolVectorRepository;
    private final SymbolRepository symbolRepository;

    public VectorSearchController(SymbolVectorRepository symbolVectorRepository, SymbolRepository symbolRepository) {
        this.symbolVectorRepository = symbolVectorRepository;
        this.symbolRepository = symbolRepository;
    }

    @PostMapping("/seed")
    @Operation(summary = "Seed some symbols with random vectors for testing")
    public String seed(@RequestParam(defaultValue = "10") int count) {
        List<Symbol> symbols = symbolRepository.findAll().stream().limit(count).toList();
        Random random = new Random();

        for (Symbol symbol : symbols) {
            float[] vector = new float[128];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = random.nextFloat();
            }

            SymbolVector sv = new SymbolVector(symbol, vector, "Vector embedding for " + symbol.getName());
            symbolVectorRepository.save(sv);
        }

        return "Seeded " + symbols.size() + " symbols with random vectors.";
    }

    @GetMapping("/search")
    @Operation(summary = "Search for nearest symbols using a random query vector")
    public List<SymbolVector> search(@RequestParam(defaultValue = "5") int k) {
        float[] queryVector = new float[128];
        Random random = new Random();
        for (int i = 0; i < queryVector.length; i++) {
            queryVector[i] = random.nextFloat();
        }

        byte[] vectorBytes = floatArrayToByteArray(queryVector);
        return symbolVectorRepository.findNearestNeighbors(vectorBytes, k);
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
