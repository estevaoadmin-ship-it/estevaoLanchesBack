package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.FilaImpressao;
import com.paullomaggio.estevaoLanches.entities.FilaImpressao.DestinoImpressao;
import com.paullomaggio.estevaoLanches.entities.FilaImpressao.StatusImpressao;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO para representar um item da fila de impressão com dados enriquecidos do pedido.
 */
public class FilaImpressaoDTO {

    private UUID id;
    private PedidoResponseDTO pedido;
    private DestinoImpressao destino;
    private StatusImpressao status;
    private Integer tentativas;
    private LocalDateTime ultimaTentativa;
    private String logErro;
    private LocalDateTime criadoEm;
    private LocalDateTime impressoEm;

    public FilaImpressaoDTO(FilaImpressao entity, PedidoResponseDTO pedidoEnriquecido) {
        this.id = entity.getId();
        this.pedido = pedidoEnriquecido;
        this.destino = entity.getDestino();
        this.status = entity.getStatus();
        this.tentativas = entity.getTentativas();
        this.ultimaTentativa = entity.getUltimaTentativa();
        this.logErro = entity.getLogErro();
        this.criadoEm = entity.getCriadoEm();
        this.impressoEm = entity.getImpressoEm();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public PedidoResponseDTO getPedido() {
        return pedido;
    }

    public void setPedido(PedidoResponseDTO pedido) {
        this.pedido = pedido;
    }

    public DestinoImpressao getDestino() {
        return destino;
    }

    public void setDestino(DestinoImpressao destino) {
        this.destino = destino;
    }

    public StatusImpressao getStatus() {
        return status;
    }

    public void setStatus(StatusImpressao status) {
        this.status = status;
    }

    public Integer getTentativas() {
        return tentativas;
    }

    public void setTentativas(Integer tentativas) {
        this.tentativas = tentativas;
    }

    public LocalDateTime getUltimaTentativa() {
        return ultimaTentativa;
    }

    public void setUltimaTentativa(LocalDateTime ultimaTentativa) {
        this.ultimaTentativa = ultimaTentativa;
    }

    public String getLogErro() {
        return logErro;
    }

    public void setLogErro(String logErro) {
        this.logErro = logErro;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public LocalDateTime getImpressoEm() {
        return impressoEm;
    }

    public void setImpressoEm(LocalDateTime impressoEm) {
        this.impressoEm = impressoEm;
    }
}