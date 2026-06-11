package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.ProdutoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ProdutoResponseDTO;
import com.paullomaggio.estevaoLanches.services.ProdutoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/produtos")
// Liberado para aceitar requisições de qualquer origem no deploy (evita bloqueio de CORS)
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE
})
public class ProdutoController {

    @Autowired
    private ProdutoService produtoService;

    // 1. LISTAR TODOS
    @GetMapping
    public ResponseEntity<List<ProdutoResponseDTO>> listar() {
        return ResponseEntity.ok(produtoService.listarTodos());
    }

    // 2. BUSCA DINÂMICA (Ex: /api/produtos/buscar?termo=X-Tevao)
    @GetMapping("/buscar")
    public ResponseEntity<List<ProdutoResponseDTO>> buscar(@RequestParam String termo) {
        List<ProdutoResponseDTO> resultados = produtoService.buscarPorTermo(termo);
        return ResponseEntity.ok(resultados);
    }

    // 3. BUSCAR POR ID ESPECÍFICO
    @GetMapping("/{id}")
    public ResponseEntity<ProdutoResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(produtoService.buscarPorId(id));
    }

    // 4. CRIAR NOVO PRODUTO (Com validação ativada)
    @PostMapping
    public ResponseEntity<ProdutoResponseDTO> salvar(@RequestBody @Valid ProdutoRequestDTO dto) {
        ProdutoResponseDTO produtoSalvo = produtoService.salvar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(produtoSalvo);
    }

    // 5. ATUALIZAR PRODUTO EXISTENTE (Com validação ativada)
    @PutMapping("/{id}")
    public ResponseEntity<ProdutoResponseDTO> atualizar(
            @PathVariable UUID id,
            @RequestBody @Valid ProdutoRequestDTO dto) {
        ProdutoResponseDTO produtoAtualizado = produtoService.atualizar(id, dto);
        return ResponseEntity.ok(produtoAtualizado);
    }

    // 6. EXCLUIR PRODUTO
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        produtoService.deletar(id);
        return ResponseEntity.noContent().build(); // Retorna Status 204 No Content (sucesso sem corpo)
    }
}