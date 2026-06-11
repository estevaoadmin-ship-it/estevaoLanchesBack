package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.CategoriaRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.CategoriaResponseDTO;
import com.paullomaggio.estevaoLanches.services.CategoriaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categorias")
// Liberado para aceitar requisições de qualquer origem no deploy (evita bloqueio de CORS)
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE
})
public class CategoriaController {

    @Autowired
    private CategoriaService categoriaService;

    // 1. LISTAR TODAS (Ordenadas por ordem de exibição)
    @GetMapping
    public ResponseEntity<List<CategoriaResponseDTO>> listar() {
        return ResponseEntity.ok(categoriaService.listarTodas());
    }

    // 2. BUSCA DINÂMICA PELO NOME (Ex: /api/categorias/buscar?nome=Bebidas)
    @GetMapping("/buscar")
    public ResponseEntity<List<CategoriaResponseDTO>> buscar(@RequestParam String nome) {
        List<CategoriaResponseDTO> resultados = categoriaService.buscarPorNome(nome);
        return ResponseEntity.ok(resultados);
    }

    // 3. BUSCAR CATEGORIA POR ID
    @GetMapping("/{id}")
    public ResponseEntity<CategoriaResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(categoriaService.buscarPorId(id));
    }

    // 4. CADASTRAR NOVA CATEGORIA (Com validação ativada)
    @PostMapping
    public ResponseEntity<CategoriaResponseDTO> salvar(@RequestBody @Valid CategoriaRequestDTO dto) {
        CategoriaResponseDTO categoriaSalva = categoriaService.salvar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(categoriaSalva);
    }

    // 5. ATUALIZAR CATEGORIA EXISTENTE (Com validação ativada)
    @PutMapping("/{id}")
    public ResponseEntity<CategoriaResponseDTO> atualizar(
            @PathVariable UUID id,
            @RequestBody @Valid CategoriaRequestDTO dto) {
        CategoriaResponseDTO categoriaAtualizada = categoriaService.atualizar(id, dto);
        return ResponseEntity.ok(categoriaAtualizada);
    }

    // 6. EXCLUIR CATEGORIA (Respeitando a trava de Chave Estrangeira do banco)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        categoriaService.deletar(id);
        return ResponseEntity.noContent().build(); // Retorna Status 204 No Content
    }
}