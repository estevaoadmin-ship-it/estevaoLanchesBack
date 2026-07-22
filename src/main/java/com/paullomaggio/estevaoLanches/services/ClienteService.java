package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ClienteRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ClienteResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.entities.Endereco;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.DatabaseIntegrityException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.ClienteRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Transactional(readOnly = true)
    public List<ClienteResponseDTO> listarTodos() {
        return clienteRepository.findAll().stream()
                .map(ClienteResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ClienteResponseDTO buscarPorId(UUID id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado com o ID informado."));
        return new ClienteResponseDTO(cliente);
    }

    @Transactional(readOnly = true)
    public ClienteResponseDTO buscarPorCpf(String cpf) {
        Cliente cliente = clienteRepository.findByCpf(cpf)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado com o CPF informado."));
        return new ClienteResponseDTO(cliente);
    }

    @Transactional(readOnly = true)
    public List<ClienteResponseDTO> buscarPorNome(String termo) {
        // 1. Termo vazio
        if (!StringUtils.hasText(termo)) {
            return List.of();
        }

        Map<UUID, Cliente> resultadosUnicos = new HashMap<>();
        String termoNumerico = extrairNumeros(termo);

        // Classificação do termo
        if (termoNumerico.length() == 10) { // Pode ser telefone (10 dígitos)
            // 2. CPF ou telefone mascarado / 3. Termo puramente numérico (10 dígitos)
            clienteRepository.findByNumero(termoNumerico).ifPresent(c -> resultadosUnicos.put(c.getId(), c));
        } else if (termoNumerico.length() == 11) { // Pode ser CPF ou telefone (11 dígitos)
            // 2. CPF ou telefone mascarado / 3. Termo puramente numérico (11 dígitos)
            clienteRepository.findByCpf(termoNumerico).ifPresent(c -> resultadosUnicos.put(c.getId(), c));
            clienteRepository.findByNumero(termoNumerico).ifPresent(c -> resultadosUnicos.put(c.getId(), c));
        } else { // 4. Termo textual / 5. Termo alfanumérico (ou numérico que não seja 10/11 dígitos)
            clienteRepository.findByNomeContainingIgnoreCase(termo)
                    .forEach(c -> resultadosUnicos.put(c.getId(), c));
        }

        return resultadosUnicos.values().stream()
                .map(ClienteResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public ClienteResponseDTO salvar(ClienteRequestDTO dto) {
        if (dto.cpf() != null && !dto.cpf().isBlank() && clienteRepository.findByCpf(dto.cpf()).isPresent()) {
            throw new BusinessRuleException("Já existe um cliente cadastrado com este CPF.");
        }

        if (dto.email() != null && !dto.email().isBlank() && clienteRepository.findByEmail(dto.email()).isPresent()) {
            throw new BusinessRuleException("Já existe um cliente cadastrado com este e-mail.");
        }

        Cliente cliente = new Cliente();
        copiarDtoParaEntidade(dto, cliente);
        return new ClienteResponseDTO(clienteRepository.save(cliente));
    }

    @Transactional
    public ClienteResponseDTO atualizar(UUID id, ClienteRequestDTO dto) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado para atualização."));

        if (dto.cpf() != null && !dto.cpf().isBlank() && clienteRepository.existsByCpfAndIdNot(dto.cpf(), id)) {
            throw new BusinessRuleException("Este CPF já está sendo usado por outro cliente.");
        }

        if (dto.email() != null && !dto.email().isBlank() && clienteRepository.existsByEmailAndIdNot(dto.email(), id)) {
            throw new BusinessRuleException("Este e-mail já está sendo usado por outro cliente.");
        }

        copiarDtoParaEntidade(dto, cliente);
        return new ClienteResponseDTO(clienteRepository.save(cliente));
    }

    @Transactional
    public void excluir(UUID id) {
        if (!clienteRepository.existsById(id)) {
            throw new ResourceNotFoundException("Cliente não encontrado para exclusão.");
        }
        try {
            clienteRepository.deleteById(id);
        } catch (DataIntegrityViolationException e) {
            throw new DatabaseIntegrityException("Não é possível excluir o cliente pois ele possui históricos de pedidos vinculados.");
        }
    }

    private void copiarDtoParaEntidade(ClienteRequestDTO dto, Cliente cliente) {
        cliente.setNome(dto.nome());
        cliente.setCpf(dto.cpf());
        cliente.setEmail(dto.email());
        cliente.setNumero(dto.numero());
        cliente.setDataNascimento(dto.dataNascimento());
        cliente.setStatus(dto.status());

        cliente.getEnderecos().clear();
        if (dto.enderecos() != null) {
            List<Endereco> novosEnderecos = dto.enderecos().stream().map(endDto -> {
                Endereco endereco = new Endereco();
                endereco.setRotulo(endDto.rotulo());
                endereco.setLogradouro(endDto.logradouro());
                endereco.setNumero(endDto.numero());
                endereco.setComplemento(endDto.complemento());
                endereco.setBairro(endDto.bairro());
                endereco.setCidade(endDto.cidade()); // Ajustado de endDto.city() para mapeamento correto do record
                endereco.setUf(endDto.uf());
                endereco.setCep(endDto.cep());
                endereco.setCliente(cliente);
                return endereco;
            }).collect(Collectors.toList());

            cliente.getEnderecos().addAll(novosEnderecos);
        }
    }

    /**
     * Extrai apenas os dígitos numéricos de uma string.
     *
     * @param texto A string de entrada.
     * @return Uma string contendo apenas os dígitos numéricos.
     */
    private String extrairNumeros(String texto) {
        if (texto == null) {
            return "";
        }
        Pattern pattern = Pattern.compile("\\D"); // Expressão regular para encontrar não-dígitos
        Matcher matcher = pattern.matcher(texto);
        return matcher.replaceAll("");
    }
}