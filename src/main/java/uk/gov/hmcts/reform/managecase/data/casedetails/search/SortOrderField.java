package uk.gov.hmcts.reform.managecase.data.casedetails.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder(builderMethodName = "sortOrderWith")
public class SortOrderField {

    private String caseFieldId;
    private boolean metadata;
    private String direction;

}
