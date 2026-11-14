package com.hopr.service;

import com.hopr.config.HoprProperties;
import com.hopr.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Stores appeal screenshots on disk and returns relative paths ("TICKET/uuid_name.png"). */
@Service
@RequiredArgsConstructor
public class AppealStorageService {

    private static final Set<String> ALLOWED = Set.of("image/png", "image/jpeg", "image/jpg", "image/gif",
            "image/webp", "application/pdf");

    private final HoprProperties props;

    public List<String> store(String ticket, List<MultipartFile> files) {
        List<MultipartFile> real = new ArrayList<>();
        if (files != null) {
            for (MultipartFile f : files) {
                if (f != null && !f.isEmpty()) {
                    real.add(f);
                }
            }
        }
        if (real.isEmpty()) {
            return new ArrayList<>();
        }
        HoprProperties.Appeals cfg = props.getAppeals();
        if (real.size() > cfg.getMaxAttachments()) {
            throw new BadRequestException("At most " + cfg.getMaxAttachments() + " screenshots can be attached");
        }
        Path dir = Paths.get(cfg.getUploadDir()).toAbsolutePath().normalize().resolve(ticket);
        try {
            Files.createDirectories(dir);
            List<String> stored = new ArrayList<>();
            for (MultipartFile f : real) {
                String type = f.getContentType() == null ? "" : f.getContentType().toLowerCase(Locale.ROOT);
                if (!ALLOWED.contains(type)) {
                    throw new BadRequestException("Unsupported attachment type '" + type + "' (use PNG, JPG, GIF, WEBP or PDF)");
                }
                if (f.getSize() > cfg.getMaxFileSizeBytes()) {
                    throw new BadRequestException("Attachment '" + f.getOriginalFilename() + "' exceeds "
                            + (cfg.getMaxFileSizeBytes() / (1024 * 1024)) + " MB");
                }
                String name = UUID.randomUUID().toString().substring(0, 8) + "_" + sanitize(f.getOriginalFilename());
                try (InputStream in = f.getInputStream()) {
                    Files.copy(in, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
                stored.add(ticket + "/" + name);
            }
            return stored;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not store appeal attachments", ex);
        }
    }

    static String sanitize(String original) {
        String base = original == null || original.isBlank() ? "screenshot" : original;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        String clean = base.replaceAll("[^A-Za-z0-9._-]", "_");
        return clean.length() > 80 ? clean.substring(clean.length() - 80) : clean;
    }
}
