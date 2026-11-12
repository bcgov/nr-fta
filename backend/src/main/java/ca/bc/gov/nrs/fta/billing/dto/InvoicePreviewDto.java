package ca.bc.gov.nrs.fta.billing.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * FTA695 Invoice Preview: the range invoice a tenure would get for a year.
 *
 * @param forestFileId        the tenure
 * @param calendarYear        the billing year
 * @param fileTypeDescription its range file type, e.g. "Grazing Licence"
 * @param regionCode          the region its district rolls up to
 * @param districtCode        its administering district
 * @param clientName          its 'A' and 'B' clients, separated by ";"
 * @param address1            the 'A' client's address
 * @param address2            〃
 * @param address3            〃
 * @param city                〃
 * @param province            〃
 * @param postalCode          〃
 * @param lines               the invoice lines, in legacy order
 * @param subtotal            the sum of the line amounts
 * @param taxRate             the GST/HST percentage, 0 for a tax-exempt client
 * @param taxAmount           the tax on the subtotal
 * @param total               subtotal plus tax
 */
public record InvoicePreviewDto(
    String forestFileId,
    String calendarYear,
    String fileTypeDescription,
    String regionCode,
    String districtCode,
    String clientName,
    String address1,
    String address2,
    String address3,
    String city,
    String province,
    String postalCode,
    List<Line> lines,
    BigDecimal subtotal,
    BigDecimal taxRate,
    BigDecimal taxAmount,
    BigDecimal total) {

  /**
   * @param quantity    the quantity as legacy printed it, or null
   * @param description what is charged and at what rate
   * @param amount      the line's amount, or null when no rate is set
   */
  public record Line(String quantity, String description, BigDecimal amount) {}
}
