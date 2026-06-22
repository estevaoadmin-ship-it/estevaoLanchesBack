package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.UsuarioRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.UsuarioResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Usuario;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UsuarioService implements UserDetailsService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired(required = false)
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return usuarioRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("Colaborador não localizado com o e-mail: " + username));
    }

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
        if (usuarioRepository.existsByEmail(dto.email())) {
            throw new BusinessRuleException("O e-mail '" + dto.email() + "' já está cadastrado no sistema!");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(dto.nome());
        usuario.setEmail(dto.email());

        if (passwordEncoder != null) {
            usuario.setSenha(passwordEncoder.encode(dto.senha()));
        } else {
            usuario.setSenha(dto.senha());
        }

        usuario.setRole(dto.role());
        usuario.setAtivo(true);

        Usuario usuarioSalvo = usuarioRepository.save(usuario);

        // 🎯 FIX: Se a persistência falhar ou for mockada de forma vazia, aborta explicitamente antes de construir o DTO
        if (usuarioSalvo == null) {
            throw new BusinessRuleException("Erro crítico ao gravar os registros do colaborador.");
        }

        return new UsuarioResponseDTO(usuarioSalvo);
    }

    @Transactional
    public UsuarioResponseDTO atualizar(UUID id, UsuarioRequestDTO dto) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Impossível atualizar. Usuário não encontrado."));

        if (!usuario.getEmail().equalsIgnoreCase(dto.email()) && usuarioRepository.existsByEmailAndIdNot(dto.email(), id)) {
            throw new BusinessRuleException("O e-mail '" + dto.email() + "' já está em uso por outro colaborador.");
        }

        usuario.setNome(dto.nome());
        usuario.setEmail(dto.email());
        usuario.setRole(dto.role());

        if (dto.senha() != null && !dto.senha().isBlank()) {
            if (passwordEncoder != null) {
                usuario.setSenha(passwordEncoder.encode(dto.senha()));
            } else {
                usuario.setSenha(dto.senha());
            }
        }

        Usuario usuarioAtualizado = usuarioRepository.save(usuario);

        // 🎯 FIX: Proteção idêntica na esteira de atualização
        if (usuarioAtualizado == null) {
            throw new BusinessRuleException("Erro crítico ao atualizar os registros do colaborador.");
        }

        return new UsuarioResponseDTO(usuarioAtualizado);
    }

    @Transactional
    public void deletarOuInativar(UUID id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado para exclusão."));

        usuario.setAtivo(false);
        usuarioRepository.save(usuario);
    }
}