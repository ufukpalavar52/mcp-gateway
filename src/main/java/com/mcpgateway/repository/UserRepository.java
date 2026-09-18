package com.mcpgateway.repository;

import com.mcpgateway.domain.entity.User;
import com.mcpgateway.domain.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Accounts whose name or address contains {@code term}, case insensitively.
     *
     * <p>Searched here rather than in the browser because the screen that needs it is the
     * one for choosing among many people: filtering a page the server already truncated
     * would search the first hundred accounts and quietly call that the answer.
     */
    @Query("""
            select u from User u
            where lower(u.email) like lower(concat('%', :term, '%'))
               or lower(u.fullName) like lower(concat('%', :term, '%'))
            """)
    Page<User> search(@Param("term") String term, Pageable pageable);

    /** Email uniqueness is case insensitive, matching the {@code lower(email)} index. */
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    long countByStatus(UserStatus status);
}
