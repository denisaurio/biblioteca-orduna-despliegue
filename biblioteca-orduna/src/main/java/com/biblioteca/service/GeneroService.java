package com.biblioteca.service;

import com.biblioteca.dto.GeneroResponseDTO;
import com.biblioteca.dto.RegistroGeneroDTO;
import com.biblioteca.entity.Genero;
import com.biblioteca.exception.RecursoDuplicadoException;
import com.biblioteca.repository.GeneroRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeneroService {

    private final GeneroRepository generoRepository;

    public GeneroService(GeneroRepository generoRepository) {
        this.generoRepository = generoRepository;
    }

    /** Registrar género (Novela, Historia, Ciencia...). RecursoDuplicadoException si ya existe. */
    @Transactional
    public GeneroResponseDTO registrarGenero(RegistroGeneroDTO datos) {
        String nombre = datos.nombre().trim();
        if (generoRepository.existsByNombreIgnoreCase(nombre)) {
            throw new RecursoDuplicadoException("Ya existe el género '" + nombre + "'");
        }
        Genero g = new Genero();
        g.setNombre(nombre);
        return aDTO(generoRepository.save(g));
    }

    @Transactional(readOnly = true)
    public List<GeneroResponseDTO> listarGeneros() {
        return generoRepository.findAll().stream().map(GeneroService::aDTO)
                .sorted(Comparator.comparing(GeneroResponseDTO::nombre)).toList();
    }

    /**
     * Ids de los generos con esos nombres: reutiliza el que ya existe (sin importar tildes
     * ni mayusculas) y crea los que faltan. Lo usa el alta de libros con el escaner de ISBN.
     */
    @Transactional
    public List<Long> obtenerOCrear(List<String> nombres) {
        if (nombres == null || nombres.isEmpty()) return List.of();
        Map<String, Genero> porClave = porClave();
        List<Long> ids = new ArrayList<>();
        for (String nombre : nombres) {
            String limpio = Nombres.limpio(nombre);
            String clave = Nombres.clave(limpio);
            if (clave.isEmpty()) continue;
            Genero genero = porClave.get(clave);
            if (genero == null) {
                genero = new Genero();
                genero.setNombre(limpio.length() > 80 ? limpio.substring(0, 80).trim() : limpio);
                genero = generoRepository.save(genero);
                porClave.put(clave, genero);
            }
            if (!ids.contains(genero.getId())) ids.add(genero.getId());
        }
        return ids;
    }

    /**
     * Generos de la biblioteca que coinciden con alguno de los nombres (sugeridos o temas
     * de Open Library). Los temas sueltos NO se proponen como generos nuevos: son muchos
     * y con ruido ("Accessible book", "In library"...).
     */
    @Transactional(readOnly = true)
    public List<GeneroResponseDTO> existentesEntre(List<String> nombres) {
        Map<String, Genero> porClave = porClave();
        List<GeneroResponseDTO> encontrados = new ArrayList<>();
        for (String nombre : nombres) {
            Genero g = porClave.get(Nombres.clave(nombre));
            if (g != null && encontrados.stream().noneMatch(e -> e.id().equals(g.getId()))) {
                encontrados.add(aDTO(g));
            }
        }
        return encontrados;
    }

    /** true si ya hay un genero con ese nombre (sin importar tildes ni mayusculas). */
    @Transactional(readOnly = true)
    public boolean existe(String nombre) {
        return porClave().containsKey(Nombres.clave(nombre));
    }

    private Map<String, Genero> porClave() {
        Map<String, Genero> mapa = new HashMap<>();
        for (Genero g : generoRepository.findAll()) {
            mapa.putIfAbsent(Nombres.clave(g.getNombre()), g);
        }
        return mapa;
    }

    static GeneroResponseDTO aDTO(Genero g) {
        return new GeneroResponseDTO(g.getId(), g.getNombre());
    }
}
