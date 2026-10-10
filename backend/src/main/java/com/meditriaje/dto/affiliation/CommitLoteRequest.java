package com.meditriaje.dto.affiliation;

import jakarta.validation.constraints.Pattern;

public record CommitLoteRequest(
        @Pattern(regexp = "VALID_ROWS|ATOMIC_ALL", message = "modoCommit debe ser VALID_ROWS o ATOMIC_ALL")
        String modoCommit
) {
}
