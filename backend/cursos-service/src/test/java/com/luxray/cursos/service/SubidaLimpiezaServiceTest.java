package com.luxray.cursos.service;

import com.luxray.cursos.model.SubidaMultipart;
import com.luxray.cursos.repository.SubidaMultipartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubidaLimpiezaServiceTest {

    private SubidaMultipartRepository subidas;
    private StoragePresignService storage;
    private SubidaLimpiezaService service;

    @BeforeEach
    void setUp() {
        subidas = mock(SubidaMultipartRepository.class);
        storage = mock(StoragePresignService.class);
        service = new SubidaLimpiezaService(subidas, storage);
    }

    @Test
    void eliminaLasPartesYElObjetoDeLasSesionesCaducadas() {
        SubidaMultipart sesion = sesion("upload-caducado", "evidencias/x/foto.jpg");
        when(subidas.findByExpiraEnBefore(org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.of(sesion));

        service.limpiarSesionesCaducadas();

        verify(storage).abortarMultipart(sesion.getClave(), sesion.getUploadId());
        verify(storage).eliminarObjeto(sesion.getClave());
        verify(subidas).delete(sesion);
    }

    @Test
    void conservaLaSesionParaReintentarSiElAlmacenamientoFalla() {
        SubidaMultipart sesion = sesion("upload-reintento", "evidencias/x/foto.jpg");
        when(subidas.findByExpiraEnBefore(org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.of(sesion));
        doThrow(new IllegalStateException("S3 no disponible"))
                .when(storage).eliminarObjeto(sesion.getClave());

        assertDoesNotThrow(() -> service.limpiarSesionesCaducadas());

        verify(subidas, never()).delete(sesion);
    }

    private SubidaMultipart sesion(String uploadId, String clave) {
        SubidaMultipart sesion = new SubidaMultipart();
        sesion.setUploadId(uploadId);
        sesion.setUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        sesion.setLeccionId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        sesion.setClave(clave);
        sesion.setNombreArchivo("foto.jpg");
        sesion.setContentType("image/jpeg");
        sesion.setTamanoBytes(1024L);
        sesion.setTipoArchivo("FOTO");
        sesion.setPartesEsperadas(1);
        sesion.setCreadoEn(LocalDateTime.now().minusHours(3));
        sesion.setExpiraEn(LocalDateTime.now().minusHours(1));
        return sesion;
    }
}
