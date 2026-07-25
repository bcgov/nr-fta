package ca.bc.gov.nrs.fta.dto;

import lombok.With;

@With
public record CodeNameDto(
    String code,
    String name
) {

}
