package com.hopr.service;

import com.hopr.config.HoprProperties;
import com.hopr.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppealStorageServiceTest {

    @TempDir
    Path tmp;

    private HoprProperties props;
    private AppealStorageService service;

    @BeforeEach
    void setUp() {
        props = new HoprProperties();
        props.getAppeals().setUploadDir(tmp.toString());
        service = new AppealStorageService(props);
    }

    private MockMultipartFile png(String name) {
        return new MockMultipartFile("screenshots", name, "image/png", new byte[]{1, 2, 3});
    }

    @Test
    void nothingToStore() {
        assertThat(service.store("T-1", null)).isEmpty();
        List<MultipartFile> empties = new ArrayList<>();
        empties.add(null);
        empties.add(new MockMultipartFile("screenshots", "a.png", "image/png", new byte[0]));
        assertThat(service.store("T-1", empties)).isEmpty();
    }

    @Test
    void storesValidFiles() {
        List<String> stored = service.store("HPR-APL-2026-0001", List.of(png("proof.png"),
                new MockMultipartFile("screenshots", "doc.pdf", "application/pdf", new byte[]{9})));
        assertThat(stored).hasSize(2);
        assertThat(stored.get(0)).startsWith("HPR-APL-2026-0001/").endsWith("_proof.png");
        assertThat(Files.exists(tmp.resolve(stored.get(0)))).isTrue();
    }

    @Test
    void rejectsTooManyFiles() {
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            files.add(png("f" + i + ".png"));
        }
        assertThatThrownBy(() -> service.store("T-2", files)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("At most");
    }

    @Test
    void rejectsUnsupportedTypes() {
        assertThatThrownBy(() -> service.store("T-3", List.of(
                new MockMultipartFile("screenshots", "a.txt", "text/plain", new byte[]{1}))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Unsupported");
        MultipartFile noType = mock(MultipartFile.class);
        when(noType.isEmpty()).thenReturn(false);
        when(noType.getContentType()).thenReturn(null);
        assertThatThrownBy(() -> service.store("T-3", List.of(noType))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsOversizedFiles() {
        props.getAppeals().setMaxFileSizeBytes(2);
        assertThatThrownBy(() -> service.store("T-4", List.of(png("big.png"))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("exceeds");
    }

    @Test
    void wrapsIoFailures() throws IOException {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getContentType()).thenReturn("image/png");
        when(broken.getSize()).thenReturn(1L);
        when(broken.getOriginalFilename()).thenReturn("x.png");
        when(broken.getInputStream()).thenThrow(new IOException("disk"));
        assertThatThrownBy(() -> service.store("T-5", List.of(broken))).isInstanceOf(IllegalStateException.class);

        Path file = Files.createFile(tmp.resolve("not-a-directory"));
        props.getAppeals().setUploadDir(file.toString());
        assertThatThrownBy(() -> service.store("T-6", List.of(png("x.png")))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sanitisesFileNames() {
        assertThat(AppealStorageService.sanitize(null)).isEqualTo("screenshot");
        assertThat(AppealStorageService.sanitize(" ")).isEqualTo("screenshot");
        assertThat(AppealStorageService.sanitize("../../evil.png")).isEqualTo("evil.png");
        assertThat(AppealStorageService.sanitize("C:\\temp\\a b$.png")).isEqualTo("a_b_.png");
        assertThat(AppealStorageService.sanitize("x".repeat(120) + ".png")).hasSize(80).endsWith(".png");
    }
}
