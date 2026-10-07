package ca.bc.gov.nrs.fta.mark.service;

import ca.bc.gov.nrs.fta.mark.dto.MarkDetailDto;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * The private mark snapshot: everything the mark detail page shows, as a PDF stamped with when
 * it was taken and by whom — a point-in-time record of the mark, for a file or an email: its
 * Mark summary and Administration.
 *
 * <p>Built in code with OpenPDF rather than from a JRXML like the certificate
 * ({@link MarkCertificateReport}): two label/value grids are simpler in code than as a Jasper
 * layout. Labels and order follow the page's Mark summary and Administration cards, so the
 * two read alike.
 */
@Component
public class MarkSnapshotReport {

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a z", Locale.ENGLISH);
  private static final String DASH = "—";

  private static final Color MUTED = new Color(0x52, 0x52, 0x52);

  private static final Font TITLE = new Font(Font.HELVETICA, 16, Font.BOLD);
  private static final Font SUBTITLE = new Font(Font.HELVETICA, 11, Font.NORMAL);
  private static final Font STAMP = new Font(Font.HELVETICA, 9, Font.NORMAL, MUTED);
  private static final Font SECTION = new Font(Font.HELVETICA, 12, Font.BOLD);
  private static final Font LABEL = new Font(Font.HELVETICA, 8, Font.BOLD, MUTED);
  private static final Font VALUE = new Font(Font.HELVETICA, 9, Font.NORMAL);

  /** The page details a snapshot is stamped with. */
  public record Stamp(ZonedDateTime generatedAt, String generatedBy) {}

  /**
   * The snapshot of {@code mark}.
   *
   * @param mark  the mark, with its lists, as the detail page reads it
   * @param stamp when, and by whom (a display name, or the user id when it doesn't resolve)
   */
  public byte[] render(MarkDetailDto mark, Stamp stamp) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Document doc = new Document(PageSize.LETTER, 40, 40, 40, 48);
    try {
      PdfWriter writer = PdfWriter.getInstance(doc, out);
      String id = mark.timberMark() != null
          ? "Timber mark " + mark.timberMark()
          : "Certificate " + mark.certificate();
      writer.setPageEvent(new Footer(id + " · snapshot " + stamp.generatedAt().format(DATE_TIME)));
      doc.addTitle("Private Mark Snapshot — " + id);
      doc.addCreator("Forest Tenures Administration");
      doc.open();

      doc.add(new Paragraph("Private Mark Snapshot", TITLE));
      doc.add(new Paragraph(
          "Timber Mark " + or(mark.timberMark()) + "  ·  Certificate " + or(mark.certificate()),
          SUBTITLE));
      Paragraph generated = new Paragraph(
          "Generated " + stamp.generatedAt().format(DATE_TIME) + " by " + stamp.generatedBy()
              + ". The mark as it stood at that time.",
          STAMP);
      generated.setSpacingAfter(10);
      doc.add(generated);

      section(doc, "Mark summary");
      doc.add(grid(List.of(
          kv("Timber Mark", mark.timberMark()),
          kv("File / Certificate", mark.certificate()),
          kv("Client Number", clientNumber(mark.clientNumber(), mark.clientLocnCode())),
          kv("Mark Holder", mark.clientName()),
          kv("District", first(mark.districtDesc(), mark.orgUnitCode(), mark.forestDistrict())),
          kv("Region", mark.regionDesc()),
          // Before issue these are the defaults the page shows too (S, H), not stored values.
          kv("Marking Requirements", desc(mark.markingMethodCode(), mark.markingMethodDesc())),
          kv("Marking Instrument",
              desc(mark.markingInstrumentCode(), mark.markingInstrumentDesc())),
          kv("Application Date", date(mark.markApplicationDate())),
          kv("Geographic Location", mark.permitBlockLocn()),
          kv("LTO PID", mark.bcaaFolioNumber()),
          kv("Area", area(mark.permitBlockArea())),
          kv("Management Unit", managementUnit(mark)),
          kv("Cascade", desc(mark.cascadeSplitCode(), mark.cascadeSplitDesc())),
          kv("Reg / Comp", regComp(mark.mapReferenceReg(), mark.mapReferenceComp())),
          kv("Legal", mark.proofOfCrownOrLegal()))));

      section(doc, "Administration");
      doc.add(grid(List.of(
          kv("Mark Type", desc(mark.fileTypeCode(), mark.fileTypeDesc())),
          kv("Timber Mark Status", desc(mark.markStatusCode(), mark.markStatusDesc())),
          kv("Initial Term",
              mark.tenureTerm() != null ? mark.tenureTerm() + " months" : null),
          kv("Issued", date(mark.markIssueDate())),
          kv("Expired", date(mark.markExpiryDate())),
          kv("Extended", date(mark.markExtendDate())),
          kv("Extension Count",
              String.valueOf(mark.markExtendCount() == null ? 0 : mark.markExtendCount())),
          kv("Cancelled", date(mark.markCancelDate())),
          kv("Crown Granted Date", date(mark.grantedAcqrdDate())),
          kv("Crown Granted Description", mark.crownGrantedAcqDesc()),
          kv("Amendment Status",
              desc(mark.outstandingAmendStatus(), mark.outstandingAmendStatusDesc())),
          kv("Amended Date", date(mark.markAmendDate())),
          kv("Amendment Count",
              String.valueOf(mark.amendments() == null ? 0 : mark.amendments().size())))));

      doc.close();
    } catch (DocumentException e) {
      throw new IllegalStateException("Could not produce the mark snapshot.", e);
    }
    return out.toByteArray();
  }

  // ─── Layout ──────────────────────────────────────────────────────────────

  private static void section(Document doc, String title) throws DocumentException {
    Paragraph p = new Paragraph(title, SECTION);
    p.setSpacingBefore(12);
    p.setSpacingAfter(4);
    doc.add(p);
  }

  private record Kv(String label, String value) {}

  private static Kv kv(String label, String value) {
    return new Kv(label, value);
  }

  /**
   * Label/value pairs, two to a row as the page's cards lay them out. A long value (Legal)
   * takes its row.
   */
  private static PdfPTable grid(List<Kv> items) {
    PdfPTable t = new PdfPTable(2);
    t.setWidthPercentage(100);
    t.setSplitLate(false);
    // completeRow() pads an odd last row with the default cell, which has a border.
    t.getDefaultCell().setBorder(Rectangle.NO_BORDER);
    int column = 0;
    for (Kv kv : items) {
      boolean wide = kv.value() != null && kv.value().length() > 120;
      if (wide && column == 1) {
        t.addCell(blank()); // finish the half-filled row first
        column = 0;
      }
      PdfPCell cell = new PdfPCell();
      cell.setBorder(Rectangle.NO_BORDER);
      cell.setPaddingBottom(6);
      cell.addElement(new Paragraph(kv.label(), LABEL));
      cell.addElement(new Paragraph(or(kv.value()), VALUE));
      if (wide) {
        cell.setColspan(2);
      }
      t.addCell(cell);
      column = wide ? 0 : (column + 1) % 2;
    }
    t.completeRow();
    return t;
  }

  private static PdfPCell blank() {
    PdfPCell c = new PdfPCell(new Phrase(""));
    c.setBorder(Rectangle.NO_BORDER);
    return c;
  }

  /** "Page n of m" and the snapshot's id at the foot of every page. */
  private static final class Footer extends PdfPageEventHelper {
    private final String text;
    private PdfTemplate total;
    private BaseFont font;

    Footer(String text) {
      this.text = text;
    }

    @Override
    public void onOpenDocument(PdfWriter writer, Document document) {
      total = writer.getDirectContent().createTemplate(30, 12);
      try {
        font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
      } catch (DocumentException | java.io.IOException e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
      PdfContentByte cb = writer.getDirectContent();
      float y = document.bottom() - 20;
      ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(text, STAMP),
          document.left(), y, 0);
      String page = "Page " + writer.getPageNumber() + " of ";
      float width = font.getWidthPoint(page, 9);
      float x = document.right() - width - 16;
      cb.beginText();
      cb.setFontAndSize(font, 9);
      cb.setColorFill(MUTED);
      cb.setTextMatrix(x, y);
      cb.showText(page);
      cb.endText();
      cb.addTemplate(total, x + width, y);
    }

    @Override
    public void onCloseDocument(PdfWriter writer, Document document) {
      total.beginText();
      total.setFontAndSize(font, 9);
      total.setColorFill(MUTED);
      total.setTextMatrix(0, 0);
      // By now the writer has moved past the last page, so its number is one too many.
      total.showText(String.valueOf(writer.getPageNumber() - 1));
      total.endText();
    }
  }

  // ─── Values, formatted as the page formats them ─────────────────────────

  private static String or(String v) {
    return v == null || v.isBlank() ? DASH : v;
  }

  private static String first(String... vs) {
    for (String v : vs) {
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return null;
  }

  /** The "CODE - description" label, or the bare code when it didn't resolve. */
  private static String desc(String code, String described) {
    return first(described, code);
  }

  static String date(LocalDate d) {
    return d == null ? null : d.format(DATE);
  }

  private static String number(BigDecimal n) {
    return new DecimalFormat("#,##0.####").format(n);
  }

  private static String area(BigDecimal a) {
    return a == null ? null : number(a) + " ha";
  }

  private static String clientNumber(String number, String locn) {
    if (number == null) {
      return null;
    }
    return locn == null ? number : number + " / " + locn;
  }

  private static String managementUnit(MarkDetailDto m) {
    if (m.mgmtUnitTypeCode() == null) {
      return null;
    }
    StringBuilder s = new StringBuilder(m.mgmtUnitTypeCode());
    if (m.mgmtUnitId() != null) {
      s.append(' ').append(m.mgmtUnitId());
    }
    if (m.mgmtUnitDesc() != null) {
      s.append(" — ").append(m.mgmtUnitDesc());
    }
    return s.toString();
  }

  private static String regComp(String reg, String comp) {
    if (reg == null && comp == null) {
      return null;
    }
    return or(reg) + " / " + or(comp);
  }

}
