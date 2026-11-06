package com.biblioteca.service;

import com.biblioteca.dto.AutorResponseDTO;
import com.biblioteca.dto.RegistroAutorDTO;
import com.biblioteca.entity.Autor;
import com.biblioteca.exception.OperacionInvalidaException;
import com.biblioteca.exception.RecursoDuplicadoException;
import com.biblioteca.exception.RecursoNoEncontradoException;
import com.biblioteca.repository.AutorRepository;
import com.biblioteca.repository.LibroRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** RF03 - Autores (registrar, editar, eliminar, consultar). */
@Service
public class AutorService {

    private final AutorRepository autorRepository;
    private final LibroRepository libroRepository;

    public AutorService(AutorRepository autorRepository, LibroRepository libroRepository) {
        this.autorRepository = autorRepository;
        this.libroRepository = libroRepository;
    }

    /** RF03 - Registrar autor. */
    @Transactional
    public AutorResponseDTO registrarAutor(RegistroAutorDTO datos) {
        String nombre = datos.nombre().trim();
        if (autorRepository.existsByNombreIgnoreCase(nombre)) {
            throw new RecursoDuplicadoException("Ya existe un autor con el nombre '" + nombre + "'");
        }
        Autor autor = new Autor();
        copiar(datos, autor);
        return aDTO(autorRepository.save(autor));
    }

    /** Editar autor (botón "Editor" de la pantalla). */
    @Transactional
    public AutorResponseDTO actualizarAutor(Long idAutor, RegistroAutorDTO datos) {
        Autor autor = buscar(idAutor);
        String nombre = datos.nombre().trim();
        if (autorRepository.existsByNombreIgnoreCaseAndIdNot(nombre, idAutor)) {
            throw new RecursoDuplicadoException("Ya existe otro autor con el nombre '" + nombre + "'");
        }
        copiar(datos, autor);
        return aDTO(autorRepository.save(autor));
    }

    /** Eliminar autor. No se permite si tiene libros asociados (integridad referencial). */
    @Transactional
    public void eliminarAutor(Long idAutor) {
        Autor autor = buscar(idAutor);
        long libros = libroRepository.countByAutoresId(idAutor);
        if (libros > 0) {
            throw new OperacionInvalidaException("No se puede eliminar a " + autor.getNombre()
                    + ": tiene " + libros + " libro(s) asociado(s)");
        }
        autorRepository.delete(autor);
    }

    @Transactional(readOnly = true)
    public List<AutorResponseDTO> listarAutores() {
        return autorRepository.findAllByOrderByNombreAsc().stream().map(this::aDTO).toList();
    }

    @Transactional(readOnly = true)
    public AutorResponseDTO obtenerAutor(Long idAutor) {
        return aDTO(buscar(idAutor));
    }

    /**
     * Ids de los autores con esos nombres: reutiliza el que ya existe (sin importar tildes
     * ni mayusculas) y crea los que faltan. Lo usa el alta de libros con el escaner de ISBN.
     */
    @Transactional
    public List<Long> obtenerOCrear(List<String> nombres) {
        if (nombres == null || nombres.isEmpty()) return List.of();
        Map<String, Autor> porClave = new HashMap<>();
        for (Autor a : autorRepository.findAll()) {
            porClave.putIfAbsent(Nombres.clave(a.getNombre()), a);
        }
        List<Long> ids = new ArrayList<>();
        for (String nombre : nombres) {
            String limpio = Nombres.limpio(nombre);
            String clave = Nombres.clave(limpio);
            if (clave.isEmpty()) continue;
            Autor autor = porClave.get(clave);
            if (autor == null) {
                autor = new Autor();
                autor.setNombre(limpio.length() > 150 ? limpio.substring(0, 150).trim() : limpio);
                autor = autorRepository.save(autor);
                porClave.put(clave, autor);
            }
            if (!ids.contains(autor.getId())) ids.add(autor.getId());
        }
        return ids;
    }

    /** Separa los nombres en los autores que ya existen y los nombres que todavia no. */
    @Transactional(readOnly = true)
    public Coincidencias coincidencias(List<String> nombres) {
        Map<String, Autor> porClave = new HashMap<>();
        for (Autor a : autorRepository.findAll()) {
            porClave.putIfAbsent(Nombres.clave(a.getNombre()), a);
        }
        List<AutorResponseDTO> existentes = new ArrayList<>();
        List<String> nuevos = new ArrayList<>();
        for (String nombre : nombres == null ? List.<String>of() : nombres) {
            String clave = Nombres.clave(nombre);
            if (clave.isEmpty()) continue;
            Autor autor = porClave.get(clave);
            if (autor != null) {
                if (existentes.stream().noneMatch(e -> e.id().equals(autor.getId()))) existentes.add(aDTO(autor));
            } else if (nuevos.stream().noneMatch(n -> Nombres.clave(n).equals(clave))) {
                nuevos.add(Nombres.limpio(nombre));
            }
        }
        return new Coincidencias(existentes, nuevos);
    }

    /** Autores que ya estan en la biblioteca y nombres que no. */
    public record Coincidencias(List<AutorResponseDTO> existentes, List<String> nuevos) {}

    private Autor buscar(Long idAutor) {
        return autorRepository.findById(idAutor)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el autor con id " + idAutor));
    }

    private static void copiar(RegistroAutorDTO d, Autor a) {
        a.setNombre(d.nombre().trim());
        a.setNacionalidad(d.nacionalidad() == null || d.nacionalidad().isBlank() ? null : d.nacionalidad().trim());
        a.setBiografia(d.biografia());
    }

    AutorResponseDTO aDTO(Autor a) {
        return new AutorResponseDTO(a.getId(), a.getNombre(), a.getNacionalidad(), a.getBiografia(),
                libroRepository.countByAutoresId(a.getId()));
    }
}
