package ca.bc.gov.nrs.fta.apt.service;

import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitDto;
import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitSearchCriteria;
import ca.bc.gov.nrs.fta.apt.dto.AptOrgUnitDto;
import ca.bc.gov.nrs.fta.apt.dto.AptReportParams;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * A batch of one APT PDF report, one PDF per management unit (APTR011) or region (APTR012,
 * APTR013), zipped. The units and regions are those the report's form offers, and each PDF is
 * named as a single one is when downloaded from the form. This batch is new: APT2 ran these
 * reports one unit or region at a time.
 *
 * <p>A PDF's time is almost all database round trips (each runs its own query and dozens of
 * subreport queries), so the batch renders {@code fta.apt-reports.batch-threads} of them at once,
 * each on its own pooled connection. Keep it well under the connection pool's size so the rest
 * of the application still gets connections during a batch.
 */
@Service
public class AptReportBatchService {

  private static final Logger log = LoggerFactory.getLogger(AptReportBatchService.class);

  /** The file listing the PDFs that could not be generated, when any could not. */
  static final String FAILURES_FILE = "not-generated.txt";

  /** One PDF of the batch: the report's inputs, and the unit or region it is named after. */
  public record Item(String name, AptReportParams params) {}

  private final AptReportService reports;
  private final AptManagementUnitService units;
  private final AptLookupService lookups;
  private final int threads;

  public AptReportBatchService(AptReportService reports, AptManagementUnitService units,
      AptLookupService lookups, @Value("${fta.apt-reports.batch-threads:4}") int threads) {
    this.reports = reports;
    this.units = units;
    this.lookups = lookups;
    this.threads = Math.max(1, threads);
  }

  /** Whether the report can run as a batch: those run for one management unit or region. */
  public static boolean batchable(AptReportDefinition report) {
    return report == AptReportDefinition.APTR011 || report == AptReportDefinition.APTR012
        || report == AptReportDefinition.APTR013;
  }

  /** The batch's PDFs, in the order the form lists their units or regions. */
  public List<Item> items(AptReportDefinition report, LocalDate reportDate) {
    if (report == AptReportDefinition.APTR011) {
      return tsaItems(units.search(new AptManagementUnitSearchCriteria(
          null, null, null, null, null, null, null, null, null)), reportDate);
    }
    return regionItems(lookups.orgUnits("R"), reportDate);
  }

  /**
   * APTR011's list, as its form builds it: one unit per name, sorted by name, only the TSAs
   * (the units whose name includes "TSA").
   */
  static List<Item> tsaItems(List<AptManagementUnitDto> all, LocalDate reportDate) {
    Map<String, AptManagementUnitDto> byName = new LinkedHashMap<>();
    for (AptManagementUnitDto u : all) {
      byName.putIfAbsent(u.mgmtUnitName() == null ? "#" + u.id() : u.mgmtUnitName(), u);
    }
    return byName.values().stream()
        .filter(u -> u.mgmtUnitName() != null && u.mgmtUnitName().contains("TSA"))
        .sorted(Comparator.comparing(AptManagementUnitDto::mgmtUnitName))
        .map(u -> new Item(u.mgmtUnitName(), new AptReportParams(reportDate, u.id(), null)))
        .toList();
  }

  /** APTR012's and APTR013's list: every current region, labelled as the form labels it. */
  static List<Item> regionItems(List<AptOrgUnitDto> regions, LocalDate reportDate) {
    return regions.stream()
        .filter(r -> r.orgUnitNo() != null)
        .map(r -> new Item(regionLabel(r), new AptReportParams(reportDate, null, r.orgUnitNo())))
        .toList();
  }

  private static String regionLabel(AptOrgUnitDto r) {
    if (r.displayName() != null) {
      return r.displayName();
    }
    return r.name() != null ? r.name() : r.code();
  }

  /**
   * Writes the zip: one PDF per item, in the items' order. Up to {@code threads} PDFs render at
   * once, and each is written as soon as it and those before it are done, so the response keeps
   * moving. A PDF that fails is left out and listed, with the reason, in {@value #FAILURES_FILE},
   * so one unit's problem does not lose the rest. If the caller goes away, the PDFs not yet
   * started are cancelled.
   */
  public void writeZip(AptReportDefinition report, List<Item> items, String user,
      OutputStream out) throws IOException {
    List<String> failures = new ArrayList<>();
    Set<String> used = new HashSet<>();
    AtomicInteger worker = new AtomicInteger();
    ExecutorService pool = Executors.newFixedThreadPool(
        Math.max(1, Math.min(threads, items.size())), r -> {
      Thread t = new Thread(r, "apt-batch-" + report.name() + "-" + worker.incrementAndGet());
      t.setDaemon(true);
      return t;
    });
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      List<Future<byte[]>> pdfs = new ArrayList<>(items.size());
      for (Item item : items) {
        pdfs.add(pool.submit(() -> reports.pdf(report, item.params(), user)));
      }
      for (int i = 0; i < items.size(); i++) {
        Item item = items.get(i);
        byte[] pdf;
        try {
          pdf = pdfs.get(i).get();
        } catch (ExecutionException e) {
          RuntimeException cause = e.getCause() instanceof RuntimeException re ? re
              : new IllegalStateException(e.getCause());
          log.warn("APT batch {}: could not generate the PDF for {}", report, item.name(), cause);
          failures.add(item.name() + ": " + reason(cause));
          continue;
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException("The APT report batch was interrupted.", e);
        }
        zip.putNextEntry(new ZipEntry(unique(pdfName(report.name(), item.name()), used)));
        zip.write(pdf);
        zip.closeEntry();
        zip.flush();
      }
      if (!failures.isEmpty()) {
        zip.putNextEntry(new ZipEntry(FAILURES_FILE));
        zip.write((String.join(System.lineSeparator(), failures) + System.lineSeparator())
            .getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    } finally {
      pool.shutdownNow();
    }
  }

  private static String reason(RuntimeException e) {
    if (e instanceof ResponseStatusException rse
        && rse.getReason() != null) {
      return rse.getReason();
    }
    return "the report could not be generated.";
  }

  /**
   * A PDF's name inside the zip, as the form names a single download
   * ({@code APTR011_Arrow_TSA.pdf}), with characters a file name cannot hold replaced.
   */
  static String pdfName(String code, String chosen) {
    String name = (code + " " + chosen.trim()).replaceAll("\\s+", "_")
        .replaceAll("[\\\\/:*?\"<>|]", "-");
    return name + ".pdf";
  }

  /** The name, or the name with a counter when two units share it. */
  private static String unique(String name, Set<String> used) {
    String candidate = name;
    for (int n = 2; !used.add(candidate); n++) {
      candidate = name.substring(0, name.length() - ".pdf".length()) + "_" + n + ".pdf";
    }
    return candidate;
  }

  /** The zip's name: the report code and the day ({@code APTR011_20261124.zip}). */
  public static String zipName(AptReportDefinition report, LocalDate today) {
    return report.name() + "_" + today.format(DateTimeFormatter.BASIC_ISO_DATE) + ".zip";
  }
}
