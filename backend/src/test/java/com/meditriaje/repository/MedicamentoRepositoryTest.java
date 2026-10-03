package com.meditriaje.repository;

import com.meditriaje.dto.prescription.MedicamentoResponse;
import com.meditriaje.model.Medicamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicamentoRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MedicamentoRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MedicamentoRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("buscarPorPublicId - Retorna medicamento si existe")
    void buscarPorPublicId_existe_retornaMedicamento() {
        Medicamento med = new Medicamento(
                1L,
                "med-uuid-1",
                "MED-ACE-500",
                "Acetaminofen",
                "Acetaminofen",
                "Tableta",
                "500 mg",
                "ACTIVO"
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("med-uuid-1")))
                .thenReturn(List.of(med));

        Optional<Medicamento> resultado = repository.buscarPorPublicId("med-uuid-1");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().publicId()).isEqualTo("med-uuid-1");
        assertThat(resultado.get().codigo()).isEqualTo("MED-ACE-500");
        assertThat(resultado.get().estaActivo()).isTrue();
    }

    @Test
    @DisplayName("buscarPorPublicId - Retorna vacío si no existe")
    void buscarPorPublicId_noExiste_retornaVacio() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("med-inexistente")))
                .thenReturn(List.of());

        Optional<Medicamento> resultado = repository.buscarPorPublicId("med-inexistente");

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("buscarPorPublicId - Retorna vacío con publicId nulo o en blanco sin consultar BD")
    void buscarPorPublicId_nuloOBlanco_retornaVacio() {
        assertThat(repository.buscarPorPublicId(null)).isEmpty();
        assertThat(repository.buscarPorPublicId("   ")).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("buscarPorId - Retorna medicamento si existe")
    void buscarPorId_existe_retornaMedicamento() {
        Medicamento med = new Medicamento(
                2L,
                "med-uuid-2",
                "MED-IBU-400",
                "Ibuprofeno",
                "Ibuprofeno",
                "Tableta",
                "400 mg",
                "ACTIVO"
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(2L)))
                .thenReturn(List.of(med));

        Optional<Medicamento> resultado = repository.buscarPorId(2L);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().id()).isEqualTo(2L);
        assertThat(resultado.get().nombreComercial()).isEqualTo("Ibuprofeno");
    }

    @Test
    @DisplayName("buscarPorId - Retorna vacío si id es nulo")
    void buscarPorId_nulo_retornaVacio() {
        assertThat(repository.buscarPorId(null)).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("listarActivos - Con filtro y paginación")
    void listarActivos_conFiltro_retornaLista() {
        MedicamentoResponse item = new MedicamentoResponse(
                "med-uuid-1",
                "MED-ACE-500",
                "Acetaminofen",
                "Acetaminofen",
                "Tableta",
                "500 mg",
                "ACTIVO"
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(item));

        List<MedicamentoResponse> resultado = repository.listarActivos("aceta", 0, 10);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).codigo()).isEqualTo("MED-ACE-500");
    }

    @Test
    @DisplayName("contarActivos - Retorna conteo total de activos")
    void contarActivos_conFiltro_retornaTotal() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(16);

        int total = repository.contarActivos("med");

        assertThat(total).isEqualTo(16);
    }

    @Test
    @DisplayName("contarActivos - Retorna 0 si queryForObject retorna null")
    void contarActivos_null_retornaCero() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(null);

        int total = repository.contarActivos(null);

        assertThat(total).isZero();
    }
}
