package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.AdicionalRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.AdicionalResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Adicional;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.AdicionalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdicionalService {

    @Autowired
    private AdicionalRepository adicionalRepository;

    @Transactional(readOnly = true)
    public List<AdicionalResponseDTO> listarTodos() {
        return adicionalRepository.findAll().stream()
                .map(AdicionalResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AdicionalResponseDTO buscarPorId(UUID id) {
        Adicional adicional = adicionalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Adicional não encontrado com o ID informado."));
        return new AdicionalResponseDTO(adicional);
    }

    @Transactional
    public AdicionalResponseDTO salvar(AdicionalRequestDTO dto) {
        Adicional adicional = new Adicional();
        copiarDtoParaEntidade(dto, adicional);

        Adicional adicionalSalvo = adicionalRepository.save(adicional);
        return new AdicionalResponseDTO(adicionalSalvo);
    }

    @Transactional
    public AdicionalResponseDTO atualizar(UUID id, AdicionalRequestDTO dto) {
        Adicional adicional = adicionalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Não é possível editar. Adicional não encontrado!"));

        copiarDtoParaEntidade(dto, adicional);

        Adicional adicionalAtualizado = adicionalRepository.save(adicional);
        return new AdicionalResponseDTO(adicionalAtualizado);
    }

    @Transactional
    public void deletar(UUID id) {
        if (!adicionalRepository.existsById(id)) {
            throw new ResourceNotFoundException("Não é possível excluir. Adicional não encontrado!");
        }

        // Se tentar deletar um adicional que já está vinculado a um lanche,
        // o nosso GlobalExceptionHandler vai interceptar o erro do banco de dados
        // e devolver um 409 Conflict bonitão pro front-end!
        adicionalRepository.deleteById(id);
    }

    // =========================================================================
    // Método auxiliar para evitar repetição de código no Salvar e Atualizar
    // =========================================================================
    private void copiarDtoParaEntidade(AdicionalRequestDTO dto, Adicional adicional) {
        adicional.setNome(dto.nome());
        adicional.setPreco(dto.preco());
    }
}