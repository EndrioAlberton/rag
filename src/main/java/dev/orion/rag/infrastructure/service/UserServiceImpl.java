/*
 * Copyright 2026 Orion Services.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.orion.rag.infrastructure.service;

import dev.orion.rag.domain.model.User;
import dev.orion.rag.domain.port.out.UserRepository;
import dev.orion.rag.domain.port.out.UserService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Implementation of UserService.
 */
@ApplicationScoped
public class UserServiceImpl implements UserService {
    
    /** Repository used for all user persistence operations. */
    private final UserRepository userRepository;

    /**
     * Creates a UserServiceImpl with the required user repository.
     *
     * @param userRepository repository for user CRUD operations
     */
    @Inject
    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    @Override
    public Uni<User> createUser(String username, String email) {
        // Verificar se username já existe
        return userRepository.findByUsername(username)
            .onItem().transformToUni(existingUser -> {
                if (existingUser != null) {
                    return Uni.createFrom().failure(new
                        IllegalArgumentException("Username já existe"));
                }
                
                // Verificar se email já existe
                return userRepository.findByEmail(email)
                    .onItem().transformToUni(existingEmail -> {
                        if (existingEmail != null) {
                            return Uni.createFrom().failure(new
                                IllegalArgumentException("Email já existe"));
                        }
                        
                        // Criar novo usuário
                        User user = new User();
                        user.setUsername(username);
                        user.setEmail(email);
                        return userRepository.persist(user)
                            .onItem().transformToUni(persisted -> 
                                userRepository.flush().replaceWith(persisted));
                    });
            });
    }
    
    @Override
    public Uni<User> getUserById(String userId) {
        return userRepository.findById(userId)
            .onItem().ifNull().failWith(() -> new
                IllegalArgumentException("Usuário não encontrado"));
    }
    
    @Override
    public Uni<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username)
            .onItem().ifNull().failWith(() -> new
                IllegalArgumentException("Usuário não encontrado"));
    }
    
    @Override
    public Uni<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email)
            .onItem().ifNull().failWith(() -> new
                IllegalArgumentException("Usuário não encontrado"));
    }
    
    @Override
    public Uni<Void> updateUser(User user) {
        return userRepository.findById(user.getId())
            .onItem().ifNull().failWith(() -> new
                IllegalArgumentException("Usuário não encontrado"))
            .onItem().transformToUni(existingUser -> {
                return userRepository.persist(user)
                    .onItem().transformToUni(u -> userRepository.flush());
            });
    }
    
    @Override
    public Uni<Void> deleteUser(String userId) {
        return userRepository.findById(userId)
            .onItem().ifNull().failWith(() -> new
                IllegalArgumentException("Usuário não encontrado"))
            .onItem().transformToUni(existingUser -> 
                userRepository.deleteById(userId)
                    .onItem().transformToUni(deleted -> {
                        if (!deleted) {
                            return Uni.createFrom().failure(new
                                IllegalArgumentException("Usuário não encontrado"));
                        }
                        return userRepository.flush();
                    }));
    }
    
    @Override
    public Uni<List<User>> listUsers() {
        return userRepository.listAll();
    }
}

