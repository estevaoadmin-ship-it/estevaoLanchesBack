package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.UsuarioRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.UsuarioResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Usuario;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    // O Spring Security vai injetar essa interface automaticamente assim que adicionarmos a dependência no pom.xml
    @Autowired(required = false)
    private PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> listarTodos() {
        return usuarioRepository.findAll().stream()
                .map(UsuarioResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> listarApenasAtivos() {
        return usuarioRepository.findByAtivoTrue().stream()
                .map(UsuarioResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO buscarPorId(UUID id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não localizado com o ID informado: " + id));
        return new UsuarioResponseDTO(usuario);
    }

    @Transactional
    public UsuarioResponseDTO salvar(UsuarioRequestDTO dto) {
        // VALIDAÇÃO 1: Impede e-mails duplicados na infraestrutura do banco (Erro de Conflito - 409)
        if (usuarioRepository.existsByEmail(dto.email())) {
            throw new BusinessRuleException("O e-mail '" + dto.email() + "' já está cadastrado no sistema!");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(dto.nome());
        usuario.setEmail(dto.email());

        // CRIPTOGRAFIA PREVENTIVA: Se o Spring Security já estiver ativo, cifra a senha. Se não, salva limpo para testes.
        if (passwordEncoder != null) {
            usuario.setSenha(passwordEncoder.encode(dto.senha()));
        } else {
            usuario.setSenha(dto.senha());
        }

        usuario.setRole(dto.role());
        usuario.setAtivo(true);

        Usuario usuarioSalvo = usuarioRepository.save(usuario);
        return new UsuarioResponseDTO(usuarioSalvo);
    }

    @Transactional
    public UsuarioResponseDTO atualizar(UUID id, UsuarioRequestDTO dto) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Impossível atualizar. Usuário não encontrado."));

        // VALIDAÇÃO 2: Se o usuário estiver alterando o e-mail, garante que o novo e-mail não pertença a outra pessoa
        if (!usuario.getEmail().equalsIgnoreCase(dto.email()) && usuarioRepository.existsByEmail(dto.email())) {
            throw new BusinessRuleException("O e-mail '" + dto.email() + "' já está em uso por outro colaborador.");
        }

        usuario.setNome(dto.nome());
        usuario.setEmail(dto.email());
        usuario.setRole(dto.role());

        // Se o operador preencheu uma nova senha no formulário, nós atualizamos com segurança
        if (dto.senha() != null && !dto.senha().isBlank()) {
            if (passwordEncoder != null) {
                usuario.setSenha(passwordEncoder.encode(dto.senha()));
            } else {
                usuario.setSenha(dto.senha());
            }
        }

        Usuario usuarioAtualizado = usuarioRepository.save(usuario);
        return new UsuarioResponseDTO(usuarioAtualizado);
    }

    @Transactional
    public void deletarOuInativar(UUID id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado para exclusão."));

        // BLINDAGEM OPERACIONAL: Em vez de apagar e quebrar o histórico de pedidos vendidos por ele,
        // nós desativamos o usuário. Ele perde o acesso imediatamente, mas o relatório financeiro fica intacto.
        usuario.setAtivo(false);
        usuarioRepository.save(usuario);
    }
}