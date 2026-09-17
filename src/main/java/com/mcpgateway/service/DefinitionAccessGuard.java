package com.mcpgateway.service;

import com.mcpgateway.domain.entity.Definition;
import com.mcpgateway.domain.entity.DefinitionPermission;
import com.mcpgateway.domain.enums.DefinitionAccess;
import com.mcpgateway.domain.enums.UserRole;
import com.mcpgateway.repository.DefinitionPermissionRepository;
import com.mcpgateway.security.AuthenticatedUser;
import com.mcpgateway.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Who may run a definition, and who may change one.
 *
 * <p>One component because the rule has to be the same everywhere. It is consulted from
 * listings, from the tool catalogue, from execute, from the prompt path after routing has
 * chosen, and from approval — six places that would otherwise each hold their own reading
 * of it, and the one that drifts is the one nobody notices until it lets something through.
 *
 * <p>This sits <em>on top of</em> the role checks already on the controllers rather than
 * replacing them: editing still requires ADMIN or DEVELOPER, and this narrows which
 * definitions such a person may edit.
 */
@Component
@RequiredArgsConstructor
public class DefinitionAccessGuard {

    private final DefinitionPermissionRepository permissions;

    /**
     * An administrator is never shut out.
     *
     * <p>Deliberate, and worth stating rather than discovering: the alternative is an
     * administrator who restricts a definition, leaves themselves off it, and can only get
     * back in through the database. A permission system whose recovery path is psql is one
     * that will be worked around.
     */
    public boolean unrestricted() {
        return SecurityUtils.currentUser().map(AuthenticatedUser::role)
                .filter(UserRole.ADMIN::equals).isPresent();
    }

    /** Whether the current caller may run this definition. */
    public boolean mayRun(Definition definition) {
        if (definition.getAccess() == DefinitionAccess.OPEN || unrestricted()) {
            return true;
        }

        // canEdit implies canRun: somebody who may rewrite the command may obviously run
        // the command they wrote, and saying otherwise describes a restriction that is not
        // there.
        return permissionFor(definition)
                .filter(row -> row.isCanRun() || row.isCanEdit())
                .isPresent();
    }

    /**
     * Whether the current caller may change this definition.
     *
     * <p>An OPEN definition is open to <em>run</em>, not to edit: leaving it editable by
     * anyone with the role would mean the only way to protect a definition from being
     * rewritten is to restrict who can run it, which are different questions. So an
     * explicit grant is needed either way, and the role check on the controller still
     * applies on top.
     */
    public boolean mayEdit(Definition definition) {
        return unrestricted() || permissionFor(definition).filter(DefinitionPermission::isCanEdit).isPresent();
    }

    /** The definitions from {@code all} that the caller may run. */
    public List<Definition> runnable(List<Definition> all) {
        if (unrestricted()) {
            return all;
        }

        // One query for the lot rather than one per definition: a listing of forty
        // definitions was forty round trips, and the answer is the same either way.
        Map<Long, DefinitionPermission> mine = mine();

        return all.stream().filter(definition -> definition.getAccess() == DefinitionAccess.OPEN
                        || Optional.ofNullable(mine.get(definition.getId()))
                                .filter(row -> row.isCanRun() || row.isCanEdit())
                                .isPresent())
                .toList();
    }

    /** The ids of every definition the caller may run, for filtering by id elsewhere. */
    public Set<Long> runnableIds(List<Definition> all) {
        return runnable(all).stream().map(Definition::getId).collect(Collectors.toSet());
    }

    private Optional<DefinitionPermission> permissionFor(Definition definition) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> permissions.findByDefinitionIdAndUserId(definition.getId(), userId));
    }

    private Map<Long, DefinitionPermission> mine() {
        return SecurityUtils.currentUserId()
                .map(permissions::findByUserId)
                .orElseGet(List::of)
                .stream()
                .collect(Collectors.toMap(
                        row -> row.getDefinition().getId(), Function.identity(), (first, second) -> first));
    }
}
