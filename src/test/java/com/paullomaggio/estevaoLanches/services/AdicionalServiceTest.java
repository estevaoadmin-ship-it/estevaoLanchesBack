package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.AdicionalRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.AdicionalResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Adicional;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.AdicionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suíte de Testes — Gestão de Adicionais (Service)")
class AdicionalServiceTest {

    @Mock
    private AdicionalRepository adicionalRepository;

    @InjectMocks
    private AdicionalService adicionalService;

    private Adicional adicionalPadrao;
    private UUID adicionalId;

    @BeforeEach
    void setUp() {
        adicionalId = UUID.randomUUID();

        adicionalPadrao = new Adicional();
        adicionalPadrao.setId(adicionalId);
        adicionalPadrao.setNome("BACON EXTRA");
        adicionalPadrao.setPreco(new BigDecimal("4.50"));
    }

    // =========================================================================
    // BLOCO 1 — CENÁRIOS DE LEITURA (LISTAGEM E BUSCA)
    // =========================================================================
    @Nested
    @DisplayName("Bloco 1 — Cenários de Leitura e Consulta")
    class LeiturasTests {

        @Test
        @DisplayName("Listar Todos: Deve retornar mapeamento imutável de todos os adicionais")
        void deveListarTodosOsAdicionaisComSucesso() {
            List<Adicional> listaBanco = List.of(adicionalPadrao);
            when(adicionalRepository.findAll()).thenReturn(listaBanco);

            List<AdicionalResponseDTO> resultado = adicionalService.listarTodos();

            assertThat(resultado).isNotEmpty().hasSize(1);
            assertThat(resultado.get(0).id()).isEqualTo(adicionalId);
            assertThat(resultado.get(0).nome()).isEqualTo("BACON EXTRA");
            assertThat(resultado.get(0).preco()).isEqualByComparingTo(new BigDecimal("4.50"));
            verify(adicionalRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("Buscar por ID (Sucesso): Deve localizar e converter entidade em DTO corretamente")
        void deveBuscarPorIdComSucesso() {
            when(adicionalRepository.findById(adicionalId)).thenReturn(Optional.of(adicionalPadrao));

            AdicionalResponseDTO resultado = adicionalService.buscarPorId(adicionalId);

            assertThat(resultado).isNotNull();
            assertThat(resultado.id()).isEqualTo(adicionalId);
            assertThat(resultado.nome()).isEqualTo("BACON EXTRA");
            verify(adicionalRepository, times(1)).findById(adicionalId);
        }

        @Test
        @DisplayName("Buscar por ID (Falha): Deve disparar ResourceNotFoundException caso ID não exista")
        void deveLancarExcecaoAoBuscarIdInexistente() {
            UUID idInexistente = UUID.randomUUID();
            when(adicionalRepository.findById(idInexistente)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> adicionalService.buscarPorId(idInexistente));
            verify(adicionalRepository, times(1)).findById(idInexistente);
        }
    }

    // =========================================================================
    // BLOCO 2 — CENÁRIOS DE GRAVAÇÃO (SALVAR E ATUALIZAR)
    // =========================================================================
    @Nested
    @DisplayName("Bloco 2 — Cenários de Persistência e Edição")
    class GravacaoTests {

        @Test
        @DisplayName("Salvar: Deve sanitizar texto em caixa alta e persistir com sucesso")
        void deveSalvarNovoAdicionalComSucesso() {
            AdicionalRequestDTO requestDto = new AdicionalRequestDTO("  Cheddar Cremoso  ", new BigDecimal("6.00"));

            when(adicionalRepository.save(any(Adicional.class))).thenAnswer(invocation -> {
                Adicional a = invocation.getArgument(0);
                a.setId(UUID.randomUUID());
                return a;
            });

            AdicionalResponseDTO resultado = adicionalService.salvar(requestDto);

            assertThat(resultado).isNotNull();
            assertThat(resultado.id()).isNotNull();
            // 🎯 Valida a higienização de strings do método auxiliar (Trim + UpperCase)
            assertThat(resultado.nome()).isEqualTo("CHEDDAR CREMOSO");
            assertThat(resultado.preco()).isEqualByComparingTo(new BigDecimal("6.00"));
            verify(adicionalRepository, times(1)).save(any(Adicional.class));
        }

        @Test
        @DisplayName("Atualizar (Sucesso): Deve modificar os dados da entidade existente com segurança")
        void deveAtualizarAdicionalComSucesso() {
            AdicionalRequestDTO requestDto = new AdicionalRequestDTO("Bacon Defumado Picado", new BigDecimal("5.00"));

            when(adicionalRepository.findById(adicionalId)).thenReturn(Optional.of(adicionalPadrao));
            when(adicionalRepository.save(any(Adicional.class))).thenAnswer(i -> i.getArgument(0));

            AdicionalResponseDTO resultado = adicionalService.atualizar(adicionalId, requestDto);

            assertThat(resultado).isNotNull();
            assertThat(resultado.id()).isEqualTo(adicionalId);
            assertThat(resultado.nome()).isEqualTo("BACON DEFUMADO PICADO");
            assertThat(resultado.preco()).isEqualByComparingTo(new BigDecimal("5.00"));
            verify(adicionalRepository, times(1)).findById(adicionalId);
            verify(adicionalRepository, times(1)).save(any(Adicional.class));
        }

        @Test
        @DisplayName("Atualizar (Falha): Deve reter operação se o ID do adicional para edição não for localizado")
        void deveLancarExcecaoAoAtualizarInexistente() {
            UUID idInexistente = UUID.randomUUID();
            AdicionalRequestDTO requestDto = new AdicionalRequestDTO("Tentativa Invalida", new BigDecimal("2.00"));

            when(adicionalRepository.findById(idInexistente)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> adicionalService.atualizar(idInexistente, requestDto));
            verify(adicionalRepository, times(1)).findById(idInexistente);
            verify(adicionalRepository, never()).save(any(Adicional.class));
        }
    }

    // =========================================================================
    // BLOCO 3 — CENÁRIOS DE EXCLUSÃO (DELETAR)
    // =========================================================================
    @Nested
    @DisplayName("Bloco 3 — Cenários de Exclusão Física")
    class ExclusaoTests {

        @Test
        @DisplayName("Deletar (Sucesso): Deve executar a remoção caso o registro conste na base")
        void deveDeletarAdicionalComSucesso() {
            when(adicionalRepository.existsById(adicionalId)).thenReturn(true);
            doNothing().when(adicionalRepository).deleteById(adicionalId);

            adicionalService.deletar(adicionalId);

            verify(adicionalRepository, times(1)).existsById(adicionalId);
            verify(adicionalRepository, times(1)).deleteById(adicionalId);
        }

        @Test
        @DisplayName("Deletar (Falha): Deve impedir o comando se o registro for órfão ou inexistente")
        void deveLancarExcecaoAoDeletarInexistente() {
            UUID idInexistente = UUID.randomUUID();
            when(adicionalRepository.existsById(idInexistente)).thenReturn(false);

            assertThrows(ResourceNotFoundException.class, () -> adicionalService.deletar(idInexistente));

            verify(adicionalRepository, times(1)).existsById(idInexistente);
            verify(adicionalRepository, never()).deleteById(any());
        }
    }
}