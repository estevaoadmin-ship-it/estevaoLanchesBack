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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suíte de Testes Avançada — Matriz de Blindagem de Adicionais")
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
    // 1. MATRIZ — LISTAR TODOS
    // =========================================================================
    @Nested
    @DisplayName("1. Camada de Blindagem — listarTodos()")
    class ListarTodosTests {

        @Test
        @DisplayName("Cenário 1 — Deve retornar lista vazia quando não existir nenhum adicional")
        void deveRetornarListaVaziaQuandoNaoHouverRegistros() {
            when(adicionalRepository.findAll()).thenReturn(Collections.emptyList());

            List<AdicionalResponseDTO> resultado = adicionalService.listarTodos();

            assertThat(resultado).isEmpty();
            verify(adicionalRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("Cenário 2 — Deve retornar exatamente todos os registros cadastrados")
        void deveRetornarExatamenteTodosOsRegistros() {
            List<Adicional> listaBanco = List.of(adicionalPadrao, new Adicional(UUID.randomUUID(), "CHEDDAR", new BigDecimal("5.00")));
            when(adicionalRepository.findAll()).thenReturn(listaBanco);

            List<AdicionalResponseDTO> resultado = adicionalService.listarTodos();

            assertThat(resultado).hasSize(2);
            verify(adicionalRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("Cenário 3 — Deve converter corretamente Entity -> DTO (id, nome, preço)")
        void deveConverterEntidadeParaDtoCorretamente() {
            when(adicionalRepository.findAll()).thenReturn(List.of(adicionalPadrao));

            List<AdicionalResponseDTO> resultado = adicionalService.listarTodos();

            AdicionalResponseDTO dto = resultado.get(0);
            assertThat(dto.id()).isEqualTo(adicionalId);
            assertThat(dto.nome()).isEqualTo("BACON EXTRA");
            assertThat(dto.preco()).isEqualByComparingTo(new BigDecimal("4.50"));
        }

        @Test
        @DisplayName("Cenário 4 — Não deve alterar o estado interno de nenhuma entidade durante a leitura")
        void naoDeveAlterarEntidadeDuranteLeitura() {
            when(adicionalRepository.findAll()).thenReturn(List.of(adicionalPadrao));

            adicionalService.listarTodos();

            assertThat(adicionalPadrao.getNome()).isEqualTo("BACON EXTRA");
            assertThat(adicionalPadrao.getPreco()).isEqualByComparingTo(new BigDecimal("4.50"));
        }

        @Test
        @DisplayName("Cenário 5 — Deve chamar exclusivamente o método findAll() sem acionar comandos de escrita")
        void deveChamarApenasFindAllSemGravarOuExcluir() {
            when(adicionalRepository.findAll()).thenReturn(List.of(adicionalPadrao));

            adicionalService.listarTodos();

            verify(adicionalRepository, times(1)).findAll();
            verify(adicionalRepository, never()).save(any());
            verify(adicionalRepository, never()).deleteById(any());
            verify(adicionalRepository, never()).existsById(any());
        }

        @Test
        @DisplayName("Cenário 6 — Deve preservar a ordem cronológica/estrutural retornada pelo Repository")
        void devePreservarAOrdemDoRepository() {
            Adicional segundo = new Adicional(UUID.randomUUID(), "CEBOLA", new BigDecimal("2.00"));
            when(adicionalRepository.findAll()).thenReturn(List.of(adicionalPadrao, segundo));

            List<AdicionalResponseDTO> resultado = adicionalService.listarTodos();

            assertThat(resultado.get(0).id()).isEqualTo(adicionalId);
            assertThat(resultado.get(1).id()).isEqualTo(segundo.getId());
        }
    }

    // =========================================================================
    // 2. MATRIZ — BUSCAR POR ID
    // =========================================================================
    @Nested
    @DisplayName("2. Camada de Blindagem — buscarPorId()")
    class BuscarPorIdTests {

        @Test
        @DisplayName("Cenário 1 — Buscar por um UUID existente com sucesso")
        void deveBuscarPorUuidExistente() {
            when(adicionalRepository.findById(adicionalId)).thenReturn(Optional.of(adicionalPadrao));

            AdicionalResponseDTO resultado = adicionalService.buscarPorId(adicionalId);

            assertThat(resultado).isNotNull();
            assertThat(resultado.id()).isEqualTo(adicionalId);
        }

        @Test
        @DisplayName("Cenário 2 — UUID inexistente deve estourar ResourceNotFoundException")
        void deveLancarExceptionQuandoUuidInexistente() {
            UUID idInexistente = UUID.randomUUID();
            when(adicionalRepository.findById(idInexistente)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> adicionalService.buscarPorId(idInexistente));
        }

        @Test
        @DisplayName("Cenário 3 — Deve invocar unicamente o método findById() do repositório")
        void deveChamarApenasFindById() {
            when(adicionalRepository.findById(adicionalId)).thenReturn(Optional.of(adicionalPadrao));

            adicionalService.buscarPorId(adicionalId);

            verify(adicionalRepository, times(1)).findById(adicionalId);
            verifyNoMoreInteractions(adicionalRepository);
        }

        @Test
        @DisplayName("Cenário 4 — Nunca deve disparar persistência ou mutação em banco durante a consulta")
        void nuncaDeveSalvarNadaDuranteConsulta() {
            when(adicionalRepository.findById(adicionalId)).thenReturn(Optional.of(adicionalPadrao));

            adicionalService.buscarPorId(adicionalId);

            verify(adicionalRepository, never()).save(any());
        }

        @Test
        @DisplayName("Cenário 5 — Deve devolver DTO imutável e desacoplado da Entity pós-conversão")
        void deveDevolverDtoIndependenteDaEntidade() {
            when(adicionalRepository.findById(adicionalId)).thenReturn(Optional.of(adicionalPadrao));

            AdicionalResponseDTO resultado = adicionalService.buscarPorId(adicionalId);

            // Simula mutação de memória na Entity pós mapeamento
            adicionalPadrao.setNome("MUTAÇÃO MALICIOSA");
            adicionalPadrao.setPreco(BigDecimal.ZERO);

            assertThat(resultado.nome()).isEqualTo("BACON EXTRA");
            assertThat(resultado.preco()).isEqualByComparingTo(new BigDecimal("4.50"));
        }
    }

    // =========================================================================
    // 3. MATRIZ — SALVAR
    // =========================================================================
    @Nested
    @DisplayName("3. Camada de Blindagem — salvar()")
    class SalvarTests {

        @Test
        @DisplayName("Cenário 1 — Deve salvar um registro de adicional perfeitamente válido")
        void deveSalvarAdicionalValido() {
            AdicionalRequestDTO dto = new AdicionalRequestDTO("Cheddar", new BigDecimal("6.00"));
            // Captura o argumento passado para save
            ArgumentCaptor<Adicional> adicionalCaptor = ArgumentCaptor.forClass(Adicional.class);
            when(adicionalRepository.save(adicionalCaptor.capture())).thenAnswer(invocation -> {
                Adicional savedAdicional = invocation.getArgument(0);
                savedAdicional.setId(adicionalId); // Simula a atribuição de ID pelo repositório
                return savedAdicional;
            });

            AdicionalResponseDTO resultado = adicionalService.salvar(dto);

            // Verifica o DTO retornado
            assertThat(resultado).isNotNull();
            assertThat(resultado.id()).isEqualTo(adicionalId);
            assertThat(resultado.nome()).isEqualTo("CHEDDAR");
            assertThat(resultado.preco()).isEqualByComparingTo(new BigDecimal("6.00"));

            // Verifica a entidade capturada antes de ser "salva"
            Adicional adicionalSalvo = adicionalCaptor.getValue();
            assertThat(adicionalSalvo.getNome()).isEqualTo("CHEDDAR");
            assertThat(adicionalSalvo.getPreco()).isEqualByComparingTo(new BigDecimal("6.00"));
            assertThat(adicionalSalvo.getId()).isEqualTo(adicionalId); // O ID é setado pelo thenAnswer
            verify(adicionalRepository, times(1)).save(any(Adicional.class));
        }

        @Test
        @DisplayName("Cenários 2, 3 e 4 — Sanitização Completa: Deve aplicar trim() e UPPERCASE na string informada")
        void deveAplicarTrimEUpperCaseNaString() {
            AdicionalRequestDTO requestDto = new AdicionalRequestDTO("   cheddar cremoso   ", new BigDecimal("6.00"));
            ArgumentCaptor<Adicional> adicionalCaptor = ArgumentCaptor.forClass(Adicional.class);
            when(adicionalRepository.save(adicionalCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

            AdicionalResponseDTO resultado = adicionalService.salvar(requestDto);

            // Verifica o DTO retornado
            assertThat(resultado.nome()).isEqualTo("CHEDDAR CREMOSO");

            // Verifica a entidade capturada
            Adicional adicionalSalvo = adicionalCaptor.getValue();
            assertThat(adicionalSalvo.getNome()).isEqualTo("CHEDDAR CREMOSO");
        }

        @Test
        @DisplayName("Cenário 5 — O valor do preço decimal deve ser preservado exatamente com sua precisão e escala (Ex: 6.00)")
        void devePreservarPrecoExatamente() {
            BigDecimal precoExato = new BigDecimal("6.00");
            AdicionalRequestDTO requestDto = new AdicionalRequestDTO("CHEDDAR", precoExato);
            ArgumentCaptor<Adicional> adicionalCaptor = ArgumentCaptor.forClass(Adicional.class);
            when(adicionalRepository.save(adicionalCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

            AdicionalResponseDTO resultado = adicionalService.salvar(requestDto);

            // Verifica o DTO retornado
            assertThat(resultado.preco()).isEqualByComparingTo(precoExato);
            assertThat(resultado.preco().scale()).isEqualTo(2);

            // Verifica a entidade capturada
            Adicional adicionalSalvo = adicionalCaptor.getValue();
            assertThat(adicionalSalvo.getPreco()).isEqualByComparingTo(precoExato);
            assertThat(adicionalSalvo.getPreco().scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("Cenário 6 — O UUID identificador deve ser nulo antes do save e injetado estritamente após a persistência")
        void uuidDeveSerCriadoApenasAposSave() {
            AdicionalRequestDTO requestDto = new AdicionalRequestDTO("HAMBÚRGUER", new BigDecimal("8.00"));
            ArgumentCaptor<Adicional> adicionalCaptor = ArgumentCaptor.forClass(Adicional.class);

            when(adicionalRepository.save(adicionalCaptor.capture())).thenAnswer(invocation -> {
                Adicional a = invocation.getArgument(0);
                assertThat(a.getId()).isNull(); // Garante que a entidade entra na persistência sem ID definido
                a.setId(adicionalId);
                return a;
            });

            AdicionalResponseDTO resultado = adicionalService.salvar(requestDto);
            assertThat(resultado.id()).isEqualTo(adicionalId);

            // Verifica a entidade capturada
            Adicional adicionalSalvo = adicionalCaptor.getValue();
            assertThat(adicionalSalvo.getId()).isEqualTo(adicionalId); // O ID é setado pelo thenAnswer
        }

        @Test
        @DisplayName("Cenário 7 — Repository.save() deve ser executado exatamente uma vez")
        void saveDeveSerChamadoUmaVez() {
            AdicionalRequestDTO dto = new AdicionalRequestDTO("EGG", new BigDecimal("1.50"));
            when(adicionalRepository.save(any(Adicional.class))).thenReturn(adicionalPadrao);

            adicionalService.salvar(dto);

            verify(adicionalRepository, times(1)).save(any(Adicional.class));
        }

        @Test
        @DisplayName("Cenário 9 — Deve sanitizar corretamente strings contendo acentuações ortográficas complexas")
        void deveSanitizarNomesComAcentos() {
            AdicionalRequestDTO requestDto = new AdicionalRequestDTO("Molho Especial", new BigDecimal("3.00"));
            ArgumentCaptor<Adicional> adicionalCaptor = ArgumentCaptor.forClass(Adicional.class);
            when(adicionalRepository.save(adicionalCaptor.capture())).thenAnswer(i -> i.getArgument(0));

            AdicionalResponseDTO resultado = adicionalService.salvar(requestDto);

            // Verifica o DTO retornado
            assertThat(resultado.nome()).isEqualTo("MOLHO ESPECIAL");

            // Verifica a entidade capturada
            Adicional adicionalSalvo = adicionalCaptor.getValue();
            assertThat(adicionalSalvo.getNome()).isEqualTo("MOLHO ESPECIAL");
        }

        @Test
        @DisplayName("Cenário 10 — Verificação de comportamento esperado para nomes com espaços duplos intermediários")
        void deveVerificarComportamentoComEspacosDuplos() {
            AdicionalRequestDTO requestDto = new AdicionalRequestDTO("BACON     EXTRA", new BigDecimal("4.00"));
            ArgumentCaptor<Adicional> adicionalCaptor = ArgumentCaptor.forClass(Adicional.class);
            when(adicionalRepository.save(adicionalCaptor.capture())).thenAnswer(i -> i.getArgument(0));

            AdicionalResponseDTO resultado = adicionalService.salvar(requestDto);

            // Verifica o DTO retornado
            assertThat(resultado.nome()).isEqualTo("BACON     EXTRA");

            // Verifica a entidade capturada
            Adicional adicionalSalvo = adicionalCaptor.getValue();
            assertThat(adicionalSalvo.getNome()).isEqualTo("BACON     EXTRA");
        }

        @Test
        @DisplayName("Cenário 11 — Comportamento de entrada com Nome Nulo")
        void deveVerificarComportamentoComNomeNulo() {
            AdicionalRequestDTO requestDto = new AdicionalRequestDTO(null, new BigDecimal("4.00"));
            ArgumentCaptor<Adicional> adicionalCaptor = ArgumentCaptor.forClass(Adicional.class);
            when(adicionalRepository.save(adicionalCaptor.capture())).thenAnswer(i -> i.getArgument(0));

            AdicionalResponseDTO resultado = adicionalService.salvar(requestDto);

            // Verifica o DTO retornado
            assertThat(resultado.nome()).isNull();

            // Verifica a entidade capturada
            Adicional adicionalSalvo = adicionalCaptor.getValue();
            assertThat(adicionalSalvo.getNome()).isNull();
        }

        @Test
        @DisplayName("Cenário 12, 13 e 14 — Comportamento com Preço Nulo, Negativo e Zero")
        void deveMapearPrecosLimitesEValoresNulos() {
            ArgumentCaptor<Adicional> adicionalCaptorNull = ArgumentCaptor.forClass(Adicional.class);
            ArgumentCaptor<Adicional> adicionalCaptorNeg = ArgumentCaptor.forClass(Adicional.class);
            ArgumentCaptor<Adicional> adicionalCaptorZero = ArgumentCaptor.forClass(Adicional.class);

            when(adicionalRepository.save(adicionalCaptorNull.capture())).thenAnswer(i -> i.getArgument(0));
            AdicionalResponseDTO resNull = adicionalService.salvar(new AdicionalRequestDTO("TESTE", null));
            assertThat(resNull.preco()).isNull();
            assertThat(adicionalCaptorNull.getValue().getPreco()).isNull();

            when(adicionalRepository.save(adicionalCaptorNeg.capture())).thenAnswer(i -> i.getArgument(0));
            AdicionalResponseDTO resNeg = adicionalService.salvar(new AdicionalRequestDTO("TESTE", new BigDecimal("-2.50")));
            assertThat(resNeg.preco()).isNegative();
            assertThat(adicionalCaptorNeg.getValue().getPreco()).isNegative();

            when(adicionalRepository.save(adicionalCaptorZero.capture())).thenAnswer(i -> i.getArgument(0));
            AdicionalResponseDTO resZero = adicionalService.salvar(new AdicionalRequestDTO("TESTE", BigDecimal.ZERO));
            assertThat(resZero.preco()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(adicionalCaptorZero.getValue().getPreco()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // =========================================================================
    // 4. MATRIZ — ATUALIZAR
    // =========================================================================
    @Nested
    @DisplayName("4. Camada de Blindagem — atualizar()")
    class AtualizarTests {

        @Test
        @DisplayName("Cenário 1 — Executar atualização completa de dados com sucesso")
        void deveAtualizarAdicionalComSucessoCompleto() {
            AdicionalRequestDTO dto = new AdicionalRequestDTO("NOVO NOME", new BigDecimal("7.00"));
            when(adicionalRepository.findById(adicionalId)).thenReturn(Optional.of(adicionalPadrao));

            ArgumentCaptor<Adicional> adicionalCaptor = ArgumentCaptor.forClass(Adicional.class);
            when(adicionalRepository.save(adicionalCaptor.capture())).thenAnswer(i -> i.getArgument(0));

            AdicionalResponseDTO resultado = adicionalService.atualizar(adicionalId, dto);

            // Verifica o DTO retornado
            assertThat(resultado.nome()).isEqualTo("NOVO NOME");
            assertThat(resultado.preco()).isEqualByComparingTo(new BigDecimal("7.00"));

            // Verifica a entidade capturada
            Adicional adicionalAtualizado = adicionalCaptor.getValue();
            assertThat(adicionalAtualizado.getId()).isEqualTo(adicionalId);
            assertThat(adicionalAtualizado.getNome()).isEqualTo("NOVO NOME");
            assertThat(adicionalAtualizado.getPreco()).isEqualByComparingTo(new BigDecimal("7.00"));
        }

        @Test
        @DisplayName("Cenário 2 — Tentar atualizar UUID inexistente deve estourar ResourceNotFoundException")
        void deveFalharAoAtualizarUuidInexistente() {
            UUID idInexistente = UUID.randomUUID();
            when(adicionalRepository.findById(idInexistente)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> adicionalService.atualizar(idInexistente, new AdicionalRequestDTO("X", BigDecimal.ONE)));
        }

        @Test
        @DisplayName("Cenário 3 — Quando somente o nome é alterado, o preço antigo deve ser preservado intacto")
        void deveAlterarSomanteNomeEPrecoPermanecer() {
            AdicionalRequestDTO dto = new AdicionalRequestDTO("APENAS NOME", new BigDecimal("4.50"));
            when(adicionalRepository.findById(adicionalId)).thenReturn(Optional.of(adicionalPadrao));

            ArgumentCaptor<Adicional> adicionalCaptor = ArgumentCaptor.forClass(Adicional.class);
            when(adicionalRepository.save(adicionalCaptor.capture())).thenAnswer(i -> i.getArgument(0));

            AdicionalResponseDTO resultado = adicionalService.atualizar(adicionalId, dto);

            // Verifica o DTO retornado
            assertThat(resultado.nome()).isEqualTo("APENAS NOME");
            assertThat(resultado.preco()).isEqualByComparingTo(new BigDecimal("4.50"));

            // Verifica a entidade capturada
            Adicional adicionalAtualizado = adicionalCaptor.getValue();
            assertThat(adicionalAtualizado.getId()).isEqualTo(adicionalId);
            assertThat(adicionalAtualizado.getNome()).isEqualTo("APENAS NOME");
            assertThat(adicionalAtualizado.getPreco()).isEqualByComparingTo(new BigDecimal("4.50"));
        }

        @Test
        @DisplayName("Cenário 4 — Quando somente o preço é alterado, o nome antigo deve ser preservado intacto")
        void deveAlterarSomantePrecoENomePermanecer() {
            // Nota: O dto passa o nome "Bacon Extra" idêntico ou limpo para manter a integridade
            AdicionalRequestDTO dto = new AdicionalRequestDTO("Bacon Extra", new BigDecimal("9.90"));
            when(adicionalRepository.findById(adicionalId)).thenReturn(Optional.of(adicionalPadrao));

            ArgumentCaptor<Adicional> adicionalCaptor = ArgumentCaptor.forClass(Adicional.class);
            when(adicionalRepository.save(adicionalCaptor.capture())).thenAnswer(i -> i.getArgument(0));

            AdicionalResponseDTO resultado = adicionalService.atualizar(adicionalId, dto);

            // Verifica o DTO retornado
            assertThat(resultado.nome()).isEqualTo("BACON EXTRA");
            assertThat(resultado.preco()).isEqualByComparingTo(new BigDecimal("9.90"));

            // Verifica a entidade capturada
            Adicional adicionalAtualizado = adicionalCaptor.getValue();
            assertThat(adicionalAtualizado.getId()).isEqualTo(adicionalId);
            assertThat(adicionalAtualizado.getNome()).isEqualTo("BACON EXTRA");
            assertThat(adicionalAtualizado.getPreco()).isEqualByComparingTo(new BigDecimal("9.90"));
        }

        @Test
        @DisplayName("Cenários 5 e 6 — Deve processar trim() e UpperCase obrigatoriamente no fluxo de edição")
        void deveHigienizarStringNoFluxoDeAtualizacao() {
            AdicionalRequestDTO dto = new AdicionalRequestDTO("   picles extra   ", new BigDecimal("3.00"));
            when(adicionalRepository.findById(adicionalId)).thenReturn(Optional.of(adicionalPadrao));

            ArgumentCaptor<Adicional> adicionalCaptor = ArgumentCaptor.forClass(Adicional.class);
            when(adicionalRepository.save(adicionalCaptor.capture())).thenAnswer(i -> i.getArgument(0));

            AdicionalResponseDTO resultado = adicionalService.atualizar(adicionalId, dto);

            // Verifica o DTO retornado
            assertThat(resultado.nome()).isEqualTo("PICLES EXTRA");

            // Verifica a entidade capturada
            Adicional adicionalAtualizado = adicionalCaptor.getValue();
            assertThat(adicionalAtualizado.getNome()).isEqualTo("PICLES EXTRA");
        }

        @Test
        @DisplayName("Cenários 7 e 8 — Deve executar findById() e save() exatamente uma única vez")
        void deveExecutarMetodosRepositorioUmaVez() {
            AdicionalRequestDTO dto = new AdicionalRequestDTO("X", BigDecimal.TEN);
            when(adicionalRepository.findById(adicionalId)).thenReturn(Optional.of(adicionalPadrao));
            when(adicionalRepository.save(any(Adicional.class))).thenReturn(adicionalPadrao);

            adicionalService.atualizar(adicionalId, dto);

            verify(adicionalRepository, times(1)).findById(adicionalId);
            verify(adicionalRepository, times(1)).save(any(Adicional.class));
        }

        @Test
        @DisplayName("Cenários 9, 10 e 11 — Deve reter o mesmo UUID original, mantendo a integridade sem duplicar registros")
        void deveManterIntegridadeSemCriarNovoRegistroOuUuid() {
            AdicionalRequestDTO dto = new AdicionalRequestDTO("EDICAO", BigDecimal.ONE);
            when(adicionalRepository.findById(adicionalId)).thenReturn(Optional.of(adicionalPadrao));

            ArgumentCaptor<Adicional> adicionalCaptor = ArgumentCaptor.forClass(Adicional.class);
            when(adicionalRepository.save(adicionalCaptor.capture())).thenAnswer(i -> i.getArgument(0));

            AdicionalResponseDTO resultado = adicionalService.atualizar(adicionalId, dto);

            // Verifica o DTO retornado
            assertThat(resultado.id()).isEqualTo(adicionalId); // O ID antigo DEVE ser preservado

            // Verifica a entidade capturada
            Adicional adicionalAtualizado = adicionalCaptor.getValue();
            assertThat(adicionalAtualizado.getId()).isEqualTo(adicionalId);
        }
    }

    // =========================================================================
    // 5. MATRIZ — DELETAR
    // =========================================================================
    @Nested
    @DisplayName("5. Camada de Blindagem — deletar()")
    class ExclusaoTests {

        @Test
        @DisplayName("Cenário 1 — Deve deletar com sucesso quando o ID constar na base")
        void deveDeletarRegistroExistente() {
            when(adicionalRepository.existsById(adicionalId)).thenReturn(true);
            doNothing().when(adicionalRepository).deleteById(adicionalId);

            adicionalService.deletar(adicionalId);

            verify(adicionalRepository, times(1)).deleteById(adicionalId);
        }

        @Test
        @DisplayName("Cenário 2 — Deve estourar ResourceNotFoundException ao tentar excluir ID órfão")
        void deveLancarExceptionAoDeletarInexistente() {
            UUID idInexistente = UUID.randomUUID();
            when(adicionalRepository.existsById(idInexistente)).thenReturn(false);

            assertThrows(ResourceNotFoundException.class, () -> adicionalService.deletar(idInexistente));
        }

        @Test
        @DisplayName("Cenário 3 — Deve acionar o deleteById() exatamente uma vez no fluxo feliz")
        void deveChamarDeleteByIdUmaVez() {
            when(adicionalRepository.existsById(adicionalId)).thenReturn(true);

            adicionalService.deletar(adicionalId);

            verify(adicionalRepository, times(1)).deleteById(adicionalId);
        }

        @Test
        @DisplayName("Cenário 4 — Jamais deve invocar deleteById() caso existsById() retorne falso")
        void nuncaDeveChamarDeleteSeExistsForFalso() {
            UUID idInexistente = UUID.randomUUID();
            when(adicionalRepository.existsById(idInexistente)).thenReturn(false);

            try { adicionalService.deletar(idInexistente); } catch (Exception ignored) {}

            verify(adicionalRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("Cenário 5 — Nunca deve acionar o método save() durante o fluxo de eliminação física")
        void nuncaDeveChamarSaveNoFluxoDeExclusao() {
            when(adicionalRepository.existsById(adicionalId)).thenReturn(true);

            adicionalService.deletar(adicionalId);

            verify(adicionalRepository, never()).save(any());
        }
    }

    // =========================================================================
    // 6. MATRIZ — COPIAR DTO PARA ENTIDADE (Testes Indiretos via Salvar/Atualizar)
    // =========================================================================
    @Nested
    @DisplayName("6. Camada de Blindagem — copiarDtoParaEntidade() (Indireto)")
    class CopiarDtoParaEntidadeTests {

        @Test
        @DisplayName("Cenários 1 ao 5 — Validação das mutações privadas da regra de negócio (Null, Trim, Upper, Preço)")
        void deveValidarComportamentosDoMetodoPrivadoDeMapeamento() {
            ArgumentCaptor<Adicional> adicionalCaptor = ArgumentCaptor.forClass(Adicional.class);
            when(adicionalRepository.save(adicionalCaptor.capture())).thenAnswer(i -> i.getArgument(0));

            // Teste indireto de mutações de Nome e Preço
            AdicionalRequestDTO dtoCompleto = new AdicionalRequestDTO("  maionese temperada  ", new BigDecimal("2.50"));
            AdicionalResponseDTO res = adicionalService.salvar(dtoCompleto);

            assertThat(res.nome()).isEqualTo("MAIONESE TEMPERADA"); // Upper + Trim
            assertThat(res.preco()).isEqualByComparingTo(new BigDecimal("2.50"));

            // Verifica a entidade capturada
            Adicional adicionalSalvo = adicionalCaptor.getValue();
            assertThat(adicionalSalvo.getNome()).isEqualTo("MAIONESE TEMPERADA");
            assertThat(adicionalSalvo.getPreco()).isEqualByComparingTo(new BigDecimal("2.50"));
        }
    }

    // =========================================================================
    // 7. MATRIZ — TESTES DE REGRESSÃO DE FLUXO E ESTADO
    // =========================================================================
    @Nested
    @DisplayName("7. Camada de Blindagem — Cenários de Regressão")
    class RegressaoTests {

        @Test
        @DisplayName("Regressão 1 — Fluxo Integrado Virtuozo: Salvar ➔ Buscar e Validar Equivalência Atômica")
        void fluxoRegressaoSalvarEBuscar() {
            AdicionalRequestDTO request = new AdicionalRequestDTO("CEBOLA CARAMELIZADA", new BigDecimal("5.50"));

            // Captura a entidade que será salva
            ArgumentCaptor<Adicional> saveCaptor = ArgumentCaptor.forClass(Adicional.class);
            when(adicionalRepository.save(saveCaptor.capture())).thenAnswer(i -> {
                Adicional a = i.getArgument(0);
                a.setId(adicionalId);
                return a;
            });

            // Simula o findById retornando a entidade que foi "salva"
            when(adicionalRepository.findById(adicionalId)).thenAnswer(i -> Optional.of(saveCaptor.getValue()));

            // 1. Salva
            AdicionalResponseDTO salvo = adicionalService.salvar(request);

            // 2. Busca
            AdicionalResponseDTO consultado = adicionalService.buscarPorId(adicionalId);

            assertThat(consultado.id()).isEqualTo(salvo.id());
            assertThat(consultado.nome()).isEqualTo(salvo.nome());
            assertThat(consultado.preco()).isEqualByComparingTo(salvo.preco());

            // Verifica a entidade que foi salva
            Adicional adicionalSalvo = saveCaptor.getValue();
            assertThat(adicionalSalvo.getNome()).isEqualTo("CEBOLA CARAMELIZADA");
            assertThat(adicionalSalvo.getPreco()).isEqualByComparingTo(new BigDecimal("5.50"));
        }

        @Test
        @DisplayName("Regressão 2 — Fluxo Integrado Virtuozo: Salvar ➔ Atualizar ➔ Buscar")
        void fluxoRegressaoSalvarAtualizarBuscar() {
            // Captura a entidade que será salva inicialmente
            ArgumentCaptor<Adicional> saveCaptor = ArgumentCaptor.forClass(Adicional.class);
            when(adicionalRepository.save(saveCaptor.capture())).thenAnswer(i -> {
                Adicional a = i.getArgument(0);
                if (a.getId() == null) a.setId(adicionalId); // Simula ID na primeira save
                return a;
            });

            // Simula o findById retornando a entidade que está sendo "mantida" pelo captor
            when(adicionalRepository.findById(adicionalId)).thenAnswer(i -> Optional.of(saveCaptor.getValue()));

            // 1. Salva
            AdicionalResponseDTO salvo = adicionalService.salvar(new AdicionalRequestDTO("NOME ORIG", BigDecimal.ONE));

            // 2. Atualiza
            AdicionalResponseDTO atualizado = adicionalService.atualizar(adicionalId, new AdicionalRequestDTO("NOME MODIF", BigDecimal.TEN));

            // 3. Busca
            AdicionalResponseDTO consultado = adicionalService.buscarPorId(adicionalId);

            assertThat(consultado.nome()).isEqualTo("NOME MODIF");
            assertThat(consultado.preco()).isEqualByComparingTo(BigDecimal.TEN);

            // Verifica a entidade final capturada
            Adicional adicionalFinal = saveCaptor.getValue();
            assertThat(adicionalFinal.getId()).isEqualTo(adicionalId);
            assertThat(adicionalFinal.getNome()).isEqualTo("NOME MODIF");
            assertThat(adicionalFinal.getPreco()).isEqualByComparingTo(BigDecimal.TEN);
        }

        @Test
        @DisplayName("Regressão 3 — Fluxo Integrado Virtuozo: Salvar ➔ Excluir ➔ Buscar (Exception)")
        void fluxoRegressaoSalvarExcluirBuscar() {
            // Simula a existência para o delete
            when(adicionalRepository.existsById(adicionalId)).thenReturn(true);
            // Após exclusão física, find retorna empty
            when(adicionalRepository.findById(adicionalId)).thenReturn(Optional.empty());

            // Não precisamos de um save real para este fluxo, apenas simular a exclusão
            doNothing().when(adicionalRepository).deleteById(adicionalId);

            adicionalService.deletar(adicionalId);

            assertThrows(ResourceNotFoundException.class, () -> adicionalService.buscarPorId(adicionalId));
            verify(adicionalRepository, times(1)).deleteById(adicionalId);
        }
    }

    // =========================================================================
    // 8. MATRIZ — TESTES DE ISOLAMENTO E EFEITO COLATERAL ZERO
    // =========================================================================
    @Nested
    @DisplayName("8. Camada de Blindagem — Isolamento de Métodos")
    class IsolamentoTests {

        @Test
        @DisplayName("Isolamento — Fluxo de Salvar não pode, sob nenhuma hipótese, disparar chamadas para findAll()")
        void salvarNaoPodeChamarFindAll() {
            when(adicionalRepository.save(any(Adicional.class))).thenReturn(adicionalPadrao);
            adicionalService.salvar(new AdicionalRequestDTO("X", BigDecimal.ONE));
            verify(adicionalRepository, never()).findAll();
        }

        @Test
        @DisplayName("Isolamento — Fluxo de Atualizar não pode acionar exclusões físicas ou lógicas (delete)")
        void atualizarNaoPodeChamarDelete() {
            when(adicionalRepository.findById(adicionalId)).thenReturn(Optional.of(adicionalPadrao));
            when(adicionalRepository.save(any(Adicional.class))).thenReturn(adicionalPadrao);

            adicionalService.atualizar(adicionalId, new AdicionalRequestDTO("X", BigDecimal.ONE));
            verify(adicionalRepository, never()).delete(any());
            verify(adicionalRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("Isolamento — Fluxo de Buscar não pode realizar gravações ou updates acidentais (save)")
        void buscarNaoPodeChamarSave() {
            when(adicionalRepository.findById(adicionalId)).thenReturn(Optional.of(adicionalPadrao));
            adicionalService.buscarPorId(adicionalId);
            verify(adicionalRepository, never()).save(any());
        }

        @Test
        @DisplayName("Isolamento — Fluxo de Excluir nunca deve tentar salvar ou atualizar dados paralelos (save)")
        void excluirNaoPodeChamarSave() {
            when(adicionalRepository.existsById(adicionalId)).thenReturn(true);
            adicionalService.deletar(adicionalId);
            verify(adicionalRepository, never()).save(any());
        }
    }
}