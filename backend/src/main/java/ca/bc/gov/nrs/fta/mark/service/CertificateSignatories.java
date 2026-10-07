package ca.bc.gov.nrs.fta.mark.service;

import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Whose signature goes on an FTA402 certificate — legacy's rule: the official who issued
 * ("activated") the mark, matched on their IDIR, signs it with their scanned signature, name
 * and title.
 *
 * <p>Legacy hard-coded twelve officials and their images into the report. Here they live
 * outside the code, in {@code fta.certificate.signatures-dir}: the signature images are
 * personal and would let anyone forge a certificate, and this repository is public. The deploy
 * mounts a {@value #BUNDLE} there (an OpenShift Secret, filled from GitHub secrets — see the
 * backend README); a plain folder works too, for local runs. Either holds the images and a
 * {@code signatories.properties}:
 *
 * <pre>
 * # key: an IDIR username, found anywhere in the issuing user's id, as legacy matched it
 * JSMITH.name=Jane Smith
 * JSMITH.title=Registrar of Timber Marks
 * JSMITH.image=js_signature.png
 * </pre>
 *
 * <p>Read once, at the first print; a change to the folder takes a restart. Anything missing
 * or unreadable is logged and skipped, and the certificate prints with a blank signature line.
 */
@Component
public class CertificateSignatories {

  private static final Logger LOG = LoggerFactory.getLogger(CertificateSignatories.class);

  static final String INDEX = "signatories.properties";

  /** The folder as the deploy supplies it: one zip of the index and images (an OpenShift Secret). */
  static final String BUNDLE = "signatures.zip";

  /** A sanity cap on what a bundle may unpack to; a few signatures are well under 1 MB. */
  private static final int MAX_BYTES = 5 * 1024 * 1024;

  /** One official: their scanned signature, name and title as the certificate prints them. */
  public record Signatory(String key, String name, String title, Image signature) {}

  private final String dir;
  private volatile List<Signatory> loaded;

  public CertificateSignatories(@Value("${fta.certificate.signatures-dir:}") String dir) {
    this.dir = dir;
  }

  /** The signatory for a mark issued by {@code activatedUserId}, if one is set up. */
  public Optional<Signatory> forUser(String activatedUserId) {
    if (activatedUserId == null || activatedUserId.isBlank()) {
      return Optional.empty();
    }
    String id = activatedUserId.toUpperCase(Locale.ROOT);
    // Longest key first, so a short key that happens to sit inside another's id can't win.
    return signatories().stream().filter(s -> id.contains(s.key())).findFirst();
  }

  private List<Signatory> signatories() {
    if (loaded == null) {
      synchronized (this) {
        if (loaded == null) {
          loaded = load();
        }
      }
    }
    return loaded;
  }

  private List<Signatory> load() {
    if (dir == null || dir.isBlank()) {
      return List.of();
    }
    Map<String, byte[]> files;
    try {
      files = files(Path.of(dir));
    } catch (IOException e) {
      LOG.warn("Certificate signatures: could not read {}: {}", dir, e.getMessage());
      return List.of();
    }
    byte[] index = files.get(INDEX);
    if (index == null) {
      LOG.warn("Certificate signatures: no {} in {}; certificates print unsigned.", INDEX, dir);
      return List.of();
    }
    Properties props = new Properties();
    try (Reader r = new InputStreamReader(
        new ByteArrayInputStream(index), StandardCharsets.UTF_8)) {
      props.load(r);
    } catch (IOException e) {
      LOG.warn("Certificate signatures: could not read {}: {}", INDEX, e.getMessage());
      return List.of();
    }
    List<Signatory> out = new ArrayList<>();
    props.stringPropertyNames().stream()
        .filter(k -> k.endsWith(".image"))
        .map(k -> k.substring(0, k.length() - ".image".length()))
        .forEach(key -> {
          String file = props.getProperty(key + ".image").trim();
          byte[] bytes = files.get(file);
          if (bytes == null) {
            LOG.warn("Certificate signatures: {} not found; {} skipped.", file, key);
            return;
          }
          try {
            Image img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) {
              LOG.warn("Certificate signatures: {} is not an image; skipped.", file);
              return;
            }
            out.add(new Signatory(
                key.trim().toUpperCase(Locale.ROOT),
                props.getProperty(key + ".name", "").trim(),
                props.getProperty(key + ".title", "").trim(),
                img));
          } catch (IOException e) {
            LOG.warn("Certificate signatures: could not read {}: {}", file, e.getMessage());
          }
        });
    out.sort(Comparator.comparingInt((Signatory s) -> s.key().length()).reversed());
    LOG.info("Certificate signatures: {} signatories loaded.", out.size());
    return List.copyOf(out);
  }

  /**
   * The folder's files by name: the entries of {@link #BUNDLE} when it is there (as the deploy
   * mounts it), else the folder's own files (a local folder). Only plain names — no paths.
   */
  private static Map<String, byte[]> files(Path folder) throws IOException {
    Map<String, byte[]> out = new HashMap<>();
    Path bundle = folder.resolve(BUNDLE);
    if (Files.isRegularFile(bundle)) {
      if (Files.size(bundle) == 0) {
        return out; // deployed without signatures
      }
      try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(bundle))) {
        for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
          String name = Path.of(e.getName()).getFileName().toString();
          if (!e.isDirectory() && size(out) < MAX_BYTES) {
            out.put(name, zip.readNBytes(MAX_BYTES));
          }
        }
      }
      return out;
    }
    if (Files.isDirectory(folder)) {
      try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
        for (Path p : entries) {
          if (Files.isRegularFile(p)) {
            out.put(p.getFileName().toString(), Files.readAllBytes(p));
          }
        }
      }
    }
    return out;
  }

  private static int size(Map<String, byte[]> files) {
    return files.values().stream().mapToInt(b -> b.length).sum();
  }
}
