package ca.bc.gov.nrs.fta.mark.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Who signs a certificate: the issuing official, read from the mounted folder. */
@DisplayName("Unit Test | CertificateSignatories")
class CertificateSignatoriesTest {

  @TempDir
  Path dir;

  private void image(String name) throws Exception {
    ImageIO.write(new BufferedImage(10, 5, BufferedImage.TYPE_INT_RGB), "png",
        dir.resolve(name).toFile());
  }

  @Test
  void matchesTheIssuingUsersIdirAsLegacyDid() throws Exception {
    image("js.png");
    image("sl.png");
    Files.writeString(dir.resolve(CertificateSignatories.INDEX), """
        JSMITH.name=Jane Smith
        JSMITH.title=Registrar of Timber Marks
        JSMITH.image=js.png
        LEE.name=Sam Lee
        LEE.title=Deputy Registrar of Timber Marks
        LEE.image=sl.png
        """);
    CertificateSignatories s = new CertificateSignatories(dir.toString());

    assertThat(s.forUser("IDIR\\JSMITH")).get()
        .extracting(CertificateSignatories.Signatory::name).isEqualTo("Jane Smith");
    // Legacy matched anywhere in the id: LEE signs for IDIR\LEEROY.
    assertThat(s.forUser("idir\\leeroy")).get()
        .extracting(CertificateSignatories.Signatory::title)
        .isEqualTo("Deputy Registrar of Timber Marks");
    assertThat(s.forUser("IDIR\\SOMEONE")).isEmpty();
    assertThat(s.forUser(null)).isEmpty();
  }

  @Test
  void unsetOrMissingFolderMeansNoSignatures() {
    assertThat(new CertificateSignatories("").forUser("IDIR\\JSMITH")).isEmpty();
    assertThat(new CertificateSignatories(dir.resolve("nope").toString())
        .forUser("IDIR\\JSMITH")).isEmpty();
  }

  @Test
  void skipsAMissingImageOrOneOutsideTheFolder() throws Exception {
    Files.writeString(dir.resolve(CertificateSignatories.INDEX), """
        GONE.name=Gone
        GONE.image=missing.png
        SNEAKY.name=Sneaky
        SNEAKY.image=../outside.png
        """);
    CertificateSignatories s = new CertificateSignatories(dir.toString());

    assertThat(s.forUser("IDIR\\GONE")).isEmpty();
    assertThat(s.forUser("IDIR\\SNEAKY")).isEmpty();
  }

  @Test
  void readsTheDeployedZipBundle() throws Exception {
    java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(10, 5, BufferedImage.TYPE_INT_RGB), "png", png);
    try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(
        Files.newOutputStream(dir.resolve(CertificateSignatories.BUNDLE)))) {
      zip.putNextEntry(new java.util.zip.ZipEntry(CertificateSignatories.INDEX));
      zip.write("""
          JSMITH.name=Jane Smith
          JSMITH.title=Registrar of Timber Marks
          JSMITH.image=js.png
          """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      zip.putNextEntry(new java.util.zip.ZipEntry("js.png"));
      zip.write(png.toByteArray());
    }

    assertThat(new CertificateSignatories(dir.toString()).forUser("IDIR\\JSMITH")).get()
        .extracting(CertificateSignatories.Signatory::name).isEqualTo("Jane Smith");
  }

  @Test
  void anEmptyBundleMeansNoSignatures() throws Exception {
    Files.write(dir.resolve(CertificateSignatories.BUNDLE), new byte[0]);

    assertThat(new CertificateSignatories(dir.toString()).forUser("IDIR\\JSMITH")).isEmpty();
  }
}
