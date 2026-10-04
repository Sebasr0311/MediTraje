package com.meditriaje.repository;

import com.meditriaje.model.MfaBackupCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para códigos de respaldo de un solo uso en MFA (ADR-014, F2.1.4).
 */
@Repository
public class MfaBackupCodeRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<MfaBackupCode> backupCodeRowMapper = (rs, rowNum) -> {
        Timestamp tsUsado = rs.getTimestamp("USADO_AT");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        return new MfaBackupCode(
                rs.getLong("ID"),
                rs.getLong("USUARIO_ID"),
                rs.getString("CODE_HASH"),
                rs.getInt("USADO") == 1,
                tsUsado != null ? tsUsado.toInstant() : null,
                tsCreated != null ? tsCreated.toInstant() : null
        );
    };

    public MfaBackupCodeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate no puede ser nulo");
    }

    public void guardarLote(Long usuarioId, List<String> hashes) {
        if (hashes == null || hashes.isEmpty()) {
            return;
        }

        final String sql = "INSERT INTO MFA_BACKUP_CODE (USUARIO_ID, CODE_HASH, USADO) VALUES (?, ?, 0)";
        List<Object[]> batchArgs = hashes.stream()
                .map(hash -> new Object[] { usuarioId, hash })
                .toList();

        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    public void eliminarPorUsuario(Long usuarioId) {
        final String sql = "DELETE FROM MFA_BACKUP_CODE WHERE USUARIO_ID = ?";
        jdbcTemplate.update(sql, usuarioId);
    }

    public boolean consumirCodigo(Long usuarioId, String codeHash) {
        final String sql = """
            UPDATE MFA_BACKUP_CODE
            SET USADO = 1, USADO_AT = CURRENT_TIMESTAMP
            WHERE USUARIO_ID = ? AND CODE_HASH = ? AND USADO = 0
            """;
        int filasAfectadas = jdbcTemplate.update(sql, usuarioId, codeHash);
        return filasAfectadas > 0;
    }

    public int contarDisponibles(Long usuarioId) {
        final String sql = "SELECT COUNT(*) FROM MFA_BACKUP_CODE WHERE USUARIO_ID = ? AND USADO = 0";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, usuarioId);
        return count != null ? count : 0;
    }

    public List<MfaBackupCode> listarPorUsuario(Long usuarioId) {
        final String sql = """
            SELECT ID, USUARIO_ID, CODE_HASH, USADO, USADO_AT, CREATED_AT
            FROM MFA_BACKUP_CODE
            WHERE USUARIO_ID = ?
            ORDER BY ID ASC
            """;
        return jdbcTemplate.query(sql, backupCodeRowMapper, usuarioId);
    }
}
