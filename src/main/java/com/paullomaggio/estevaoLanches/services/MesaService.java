package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.MesaRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.MesaResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Mesa;
import com.paullomaggio.estevaoLanches.enums.StatusMesa;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.MesaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Camada de serviço de desacoplamento completo para gestão de regras de negócio de Mesas.
 */
@Service
public class MesaService {

    @Autowired
    private MesaRepository mesaRepository;

    /**
     * Cria uma nova mesa garantindo a regra de unicidade de numeração no salão.
     */
    @Transactional
    public MesaResponseDTO criar(MesaRequestDTO dto) {
        if (mesaRepository.findByNumero(dto.numero()).isPresent()) {
            throw new BusinessRuleException("Operação negada! Já existe uma mesa cadastrada com o número: " + dto.numero());
        }

        Mesa mesa = new Mesa();
        mesa.setNumero(dto.numero());
        mesa.setStatus(dto.status() != null ? dto.status() : StatusMesa.LIVRE);
        mesa.setEmpresaId(dto.empresaId());
        mesa.setFilialId(dto.filialId());

        Mesa mesaSalva = mesaRepository.save(mesa);
        return new MesaResponseDTO(mesaSalva);
    }

    /**
     * Atualiza os dados cadastrais da mesa por ID.
     */
    @Transactional
    public MesaResponseDTO atualizar(UUID id, MesaRequestDTO dto) {
        Mesa mesa = mesaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa não encontrada com o ID: " + id));

        // Se alterou o número, valida se o novo número já está em uso por outra mesa
        if (!mesa.getNumero().equals(dto.numero()) && mesaRepository.findByNumero(dto.numero()).isPresent()) {
            throw new BusinessRuleException("Não é possível alterar para o número " + dto.numero() + " pois ele já está em uso.");
        }

        mesa.setNumero(dto.numero());
        mesa.setStatus(dto.status());
        if (dto.empresaId() != null) mesa.setEmpresaId(dto.empresaId());
        if (dto.filialId() != null) mesa.setFilialId(dto.filialId());

        Mesa mesaAtualizada = mesaRepository.save(mesa);
        return new MesaResponseDTO(mesaAtualizada);
    }

    /**
     * Altera exclusivamente o status operacional da mesa (LIVRE, OCUPADA, BLOQUEADA).
     */
    @Transactional
    public MesaResponseDTO alterarStatus(UUID id, StatusMesa novoStatus) {
        Mesa mesa = mesaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa não encontrada com o ID: " + id));

        mesa.setStatus(novoStatus);
        Mesa mesaAtualizada = mesaRepository.save(mesa);
        return new MesaResponseDTO(mesaAtualizada);
    }

    /**
     * Retorna todas as mesas do salão ordenadas por número.
     */
    @Transactional(readOnly = true)
    public List<MesaResponseDTO> listarTodas() {
        return mesaRepository.findAll().stream()
                .map(MesaResponseDTO::new)
                .sorted((m1, m2) -> m1.numero().compareTo(m2.numero()))
                .collect(Collectors.toList());
    }

    /**
     * Busca uma mesa específica por seu ID identificador único.
     */
    @Transactional(readOnly = true)
    public MesaResponseDTO buscarPorId(UUID id) {
        Mesa mesa = mesaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa não encontrada com o ID: " + id));
        return new MesaResponseDTO(mesa);
    }

    /**
     * Busca uma mesa baseado em sua numeração de digitação.
     */
    @Transactional(readOnly = true)
    public MesaResponseDTO buscarPorNumero(Integer numero) {
        Mesa mesa = mesaRepository.findByNumero(numero)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa de número " + numero + " não está cadastrada."));
        return new MesaResponseDTO(mesa);
    }

    /**
     * Filtra as mesas com base em seu status operacional (Útil para carregar painel de mesas livres).
     */
    @Transactional(readOnly = true)
    public List<MesaResponseDTO> buscarPorStatus(StatusMesa status) {
        return mesaRepository.findByStatus(status).stream()
                .map(MesaResponseDTO::new)
                .sorted((m1, m2) -> m1.numero().compareTo(m2.numero()))
                .collect(Collectors.toList());
    }

    /**
     * Remove uma mesa do catálogo físico.
     */
    @Transactional
    public void deletar(UUID id) {
        Mesa mesa = mesaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa não localizada para exclusão."));

        if (mesa.getStatus() == StatusMesa.OCUPADA) {
            throw new BusinessRuleException("Não é permitido excluir uma mesa que está com o status OCUPADA.");
        }

        mesaRepository.delete(mesa);
    }
}