package ca.bc.gov.nrs.fta.tenure.tab.tlblocks;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The TL blocks tab's request and response shapes (legacy FTA980). */
public final class TlBlockDtos {

  private TlBlockDtos() {}

  /**
   * One TL block — a row of {@code THE.TL_BLOCK_AREA} as {@code FTA_980_TLBLOCK.GET} lists it.
   *
   * @param tlBlockId      TL_BLOCK_ID
   * @param grossHa        TL_BLOCK_GROSS_HA
   * @param eliminHa       TL_BLOCK_ELIMIN_HA
   * @param netHa          TL_BLOCK_NET_HA (gross less eliminated)
   * @param retirementDate RETIREMENT_DATE; null while the block is active
   * @param revisionCount  REVISION_COUNT, for the optimistic lock of every write
   */
  public record TlBlock(
      String tlBlockId,
      BigDecimal grossHa,
      BigDecimal eliminHa,
      BigDecimal netHa,
      LocalDate retirementDate,
      Long revisionCount) {}

  /**
   * The tab: the tenure's blocks, their totals ({@code FTA_980_TLBLOCK.getsum}, every block,
   * retired too) and what may be done.
   */
  public record TlBlocksResponse(
      TlBlockRules rules,
      List<TlBlock> blocks,
      BigDecimal totalGrossHa,
      BigDecimal totalEliminHa,
      BigDecimal totalNetHa) {}

  /**
   * A block to add, or a block's new areas. {@code tlBlockId} is read on add only (the block
   * is the key); {@code revisionCount} on update only.
   */
  public record TlBlockSaveRequest(
      String tlBlockId, BigDecimal grossHa, BigDecimal eliminHa, Long revisionCount) {}

  /** The block's revision count, for retire and un-retire. */
  public record TlBlockRevisionRequest(Long revisionCount) {}
}
