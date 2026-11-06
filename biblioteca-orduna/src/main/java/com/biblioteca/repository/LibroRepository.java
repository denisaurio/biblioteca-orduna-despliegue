package com.biblioteca.repository;

import com.biblioteca.entity.Libro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LibroRepository extends JpaRepository<Libro, Long> {
    boolean existsByIsbn(String isbn);
    boolean existsByIsbnAndIdNot(String isbn, Long id);
    Optional<Libro> findByIsbn(String isbn);

    /** Cuantos libros tiene cada autor, en una sola consulta. Filas: [idAutor, cantidad]. */
    @Query("SELECT a.id, COUNT(l) FROM Libro l JOIN l.autores a WHERE a.id IN :ids GROUP BY a.id")
    List<Object[]> contarPorAutores(@Param("ids") Collection<Long> ids);

    /** Libros de un autor (tabla libros_autores). */
    List<Libro> findByAutoresIdOrderByTituloAsc(Long idAutor);
    long countByAutoresId(Long idAutor);

    /** Busca por título, ISBN, nombre de autor o nombre de género (sin distinguir mayúsculas). */
    @Query("""
            SELECT DISTINCT l FROM Libro l
            LEFT JOIN l.autores a
            LEFT JOIN l.generos g
            WHERE LOWER(l.titulo) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(l.isbn) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(a.nombre) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(g.nombre) LIKE LOWER(CONCAT('%', :q, '%'))
            ORDER BY l.titulo
            """)
    List<Libro> buscar(@Param("q") String criterio);

    List<Libro> findTop6ByOrderByIdDesc();
}
