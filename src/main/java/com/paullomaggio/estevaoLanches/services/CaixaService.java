package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.CaixaAberturaRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.CaixaFechamentoRequestDTO;
import com.paullomaggio.estevaoLanches.entities.Caixa;
import com.paullomaggio.estevaoLanches.entities.Usuario;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import com.paullomaggio.estevaoLanches.repositories.CaixaRepository;
import com.paullomaggio.estevaoLanches.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CaixaService {

    @Autowired
    private CaixaRepository caixaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository; // <-- Injetado

    @Transactional
    public Caixa abrirCaixa(CaixaAberturaRequestDTO dto) {
        if (caixaRepository.existsByStatus(StatusCaixa.ABERTO)) {
            throw new RuntimeException("Já existe um caixa aberto no sistema!");
        }

        Usuario funcionario = usuarioRepository.findById(dto.usuarioId())
                .orElseThrow(() -> new RuntimeException("Funcionário não encontrado para abrir o caixa!"));

        Caixa caixa = new Caixa();
        caixa.setValorAbertura(dto.valorAbertura());
        caixa.setStatus(StatusCaixa.ABERTO);
        caixa.setDataHoraAbertura(LocalDateTime.now());
        caixa.setUsuarioAbertura(funcionario); // <-- Carimbo de quem abriu

        return caixaRepository.save(caixa);
    }

    @Transactional
    public Caixa fecharCaixa(CaixaFechamentoRequestDTO dto) {
        Caixa caixaAtivo = caixaRepository.findAll().stream()
                .filter(c -> c.getStatus() == StatusCaixa.ABERTO)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Nenhum caixa aberto encontrado!"));

        Usuario funcionario = usuarioRepository.findById(dto.usuarioId())
                .orElseThrow(() -> new RuntimeException("Funcionário não encontrado para fechar o caixa!"));

        caixaAtivo.setStatus(StatusCaixa.FECHADO);
        caixaAtivo.setValorFechamento(dto.valorFechamento());
        caixaAtivo.setDataHoraFechamento(LocalDateTime.now());
        caixaAtivo.setUsuarioFechamento(funcionario); // <-- Carimbo de quem fechou

        return caixaRepository.save(caixaAtivo);
    }

    public boolean isCaixaAberto() {
        return caixaRepository.existsByStatus(StatusCaixa.ABERTO);
    }
}