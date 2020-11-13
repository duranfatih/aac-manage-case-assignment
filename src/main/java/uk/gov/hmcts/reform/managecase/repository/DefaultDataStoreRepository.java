package uk.gov.hmcts.reform.managecase.repository;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.managecase.client.datastore.CaseDataContent;
import uk.gov.hmcts.reform.managecase.client.datastore.CaseDetails;
import uk.gov.hmcts.reform.managecase.client.datastore.CaseResource;
import uk.gov.hmcts.reform.managecase.client.datastore.CaseSearchResponse;
import uk.gov.hmcts.reform.managecase.client.datastore.CaseUserRole;
import uk.gov.hmcts.reform.managecase.client.datastore.CaseUserRoleResource;
import uk.gov.hmcts.reform.managecase.client.datastore.CaseUserRoleWithOrganisation;
import uk.gov.hmcts.reform.managecase.client.datastore.CaseUserRolesRequest;
import uk.gov.hmcts.reform.managecase.client.datastore.ChangeOrganisationRequest;
import uk.gov.hmcts.reform.managecase.client.datastore.DataStoreApiClient;
import uk.gov.hmcts.reform.managecase.client.datastore.Event;
import uk.gov.hmcts.reform.managecase.client.datastore.StartEventResource;
import uk.gov.hmcts.reform.managecase.client.datastore.model.CaseUpdateViewEvent;
import uk.gov.hmcts.reform.managecase.client.datastore.model.CaseViewField;
import uk.gov.hmcts.reform.managecase.client.datastore.model.CaseViewResource;
import uk.gov.hmcts.reform.managecase.client.datastore.model.elasticsearch.CaseSearchResultViewResource;
import uk.gov.hmcts.reform.managecase.security.SecurityUtils;
import uk.gov.hmcts.reform.managecase.util.JacksonUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository("defaultDataStoreRepository")
@SuppressWarnings({"PMD.DataflowAnomalyAnalysis", "PMD.UseConcurrentHashMap", "PMD.AvoidDuplicateLiterals"})
public class DefaultDataStoreRepository implements DataStoreRepository {

    static final String NOC_REQUEST_DESCRIPTION = "Notice of Change Request Event";

    static final String NOT_ENOUGH_DATA_TO_SUBMIT_START_EVENT = "Failed to get enough data from start event "
        + "to submit an event for the case";

    static final String MISSING_CASE_FIELD_ID = "Failed to create ChangeOrganisationRequest because of "
        + "missing case field id";


    public static final String ES_QUERY = "{\n"
        + "   \"query\":{\n"
        + "      \"bool\":{\n"
        + "         \"filter\":{\n"
        + "            \"term\":{\n"
        + "               \"reference\":%s\n"
        + "            }\n"
        + "         }\n"
        + "      }\n"
        + "   }\n"
        + "}";

    public static final String CHANGE_ORGANISATION_REQUEST = "ChangeOrganisationRequest";

    private final DataStoreApiClient dataStoreApi;
    private final JacksonUtils jacksonUtils;
    protected final SecurityUtils securityUtils;

    @Autowired
    public DefaultDataStoreRepository(DataStoreApiClient dataStoreApi,
                                      JacksonUtils jacksonUtils,
                                      SecurityUtils securityUtils) {
        this.dataStoreApi = dataStoreApi;
        this.jacksonUtils = jacksonUtils;
        this.securityUtils = securityUtils;
    }

    @Override
    public Optional<CaseDetails> findCaseBy(String caseTypeId, String caseId) {
        CaseSearchResponse searchResponse = dataStoreApi.searchCases(caseTypeId, String.format(ES_QUERY, caseId));
        return searchResponse.getCases().stream().findFirst();
    }

    @Override
    public CaseSearchResultViewResource findCaseBy(String caseTypeId, Optional<String> useCase, String caseId) {
        return dataStoreApi.internalSearchCases(caseTypeId,null, String.format(ES_QUERY, caseId));
    }

    @Override
    public CaseViewResource findCaseByCaseId(String caseId) {
        return dataStoreApi.getCaseDetailsByCaseId(getUserAuthToken(), caseId);
    }

    protected String getUserAuthToken() {
        return securityUtils.getCaaSystemUserToken();
    }

    @Override
    public void assignCase(List<String> caseRoles, String caseId, String userId, String organisationId) {
        List<CaseUserRoleWithOrganisation> caseUserRoles = caseRoles.stream()
                .map(role -> CaseUserRoleWithOrganisation.withOrganisationBuilder()
                    .caseRole(role).caseId(caseId).userId(userId).organisationId(organisationId).build())
                .collect(Collectors.toList());
        dataStoreApi.assignCase(new CaseUserRolesRequest(caseUserRoles));
    }

    @Override
    public List<CaseUserRole> getCaseAssignments(List<String> caseIds, List<String> userIds) {
        CaseUserRoleResource response = dataStoreApi.getCaseAssignments(caseIds, userIds);
        return response.getCaseUsers();
    }

    @Override
    public void removeCaseUserRoles(List<CaseUserRole> caseUserRoles, String organisationId) {
        List<CaseUserRoleWithOrganisation> caseUsers = caseUserRoles.stream()
            .map(caseUserRole -> CaseUserRoleWithOrganisation.withOrganisationBuilder()
                .caseRole(caseUserRole.getCaseRole())
                .caseId(caseUserRole.getCaseId())
                .userId(caseUserRole.getUserId())
                .organisationId(organisationId)
                .build())
            .collect(Collectors.toList());
        dataStoreApi.removeCaseUserRoles(new CaseUserRolesRequest(caseUsers));
    }

    @Override
    public CaseUpdateViewEvent getStartEventTrigger(String caseId, String eventId) {
        String userAuthToken = getUserAuthToken();
        return dataStoreApi.getStartEventTrigger(userAuthToken, caseId, eventId);
    }

    @Override
    public StartEventResource getExternalStartEventTrigger(String caseId, String eventId) {
        String userAuthToken = getUserAuthToken();
        return dataStoreApi.getExternalStartEventTrigger(userAuthToken, caseId, eventId);
    }

    @Override
    public CaseResource submitEventForCaseOnly(String caseId, CaseDataContent caseDataContent) {
        String userAuthToken = getUserAuthToken();
        return dataStoreApi.submitEventForCase(userAuthToken, caseId, caseDataContent);
    }

    @Override
    public CaseResource submitEventForCase(String caseId,
                                           String eventId,
                                           ChangeOrganisationRequest changeOrganisationRequest) {

        CaseResource caseResource = null;
        String userAuthToken = getUserAuthToken();
        CaseUpdateViewEvent caseUpdateViewEvent = dataStoreApi.getStartEventTrigger(userAuthToken, caseId, eventId);

        if (caseUpdateViewEvent != null) {

            if (caseUpdateViewEvent.getEventToken() == null) {
                throw new IllegalStateException(NOT_ENOUGH_DATA_TO_SUBMIT_START_EVENT);
            }

            Optional<CaseViewField> caseViewField = getCaseViewField(caseUpdateViewEvent);

            if (caseViewField.isPresent()) {
                String caseFieldId = caseViewField.get().getId();

                Event event = Event.builder()
                    .eventId(eventId)
                    .description(NOC_REQUEST_DESCRIPTION)
                    .build();

                StartEventResource startEventResource =
                    dataStoreApi.getExternalStartEventTrigger(userAuthToken, caseId, eventId);
                Map<String, JsonNode> caseData = startEventResource.getCaseDetails().getData();

                setApprovalStatus(changeOrganisationRequest, caseFieldId, caseData);

                CaseDataContent caseDataContent = CaseDataContent.builder()
                    .token(caseUpdateViewEvent.getEventToken())
                    .event(event)
                    .data(getCaseDataContentData(caseFieldId, changeOrganisationRequest, caseData))
                    .build();

                caseResource = dataStoreApi.submitEventForCase(userAuthToken, caseId, caseDataContent);
            } else {
                throw new IllegalStateException(MISSING_CASE_FIELD_ID);
            }
        }
        return caseResource;
    }

    private void setApprovalStatus(ChangeOrganisationRequest changeOrganisationRequest,
                                   String caseFieldId,
                                   Map<String, JsonNode> caseData) {
        JsonNode defaultApprovalStatusValue = caseData.get(caseFieldId);

        if (defaultApprovalStatusValue == null
            || defaultApprovalStatusValue.isMissingNode()
            || defaultApprovalStatusValue.isEmpty()) {
            changeOrganisationRequest.setApprovalStatus("0");
        }
    }

    private Optional<CaseViewField> getCaseViewField(CaseUpdateViewEvent caseUpdateViewEvent) {
        Optional<CaseViewField> caseViewField = Optional.empty();

        if (caseUpdateViewEvent.getCaseFields() != null) {
            caseViewField = caseUpdateViewEvent.getCaseFields().stream()
                .filter(cvf -> cvf.getFieldTypeDefinition().getId().equals(CHANGE_ORGANISATION_REQUEST))
                .findFirst();
        }
        return caseViewField;
    }

    @Override
    public CaseResource findCaseByCaseIdExternalApi(String caseId) {
        return dataStoreApi.getCaseDetailsByCaseIdViaExternalApi(caseId);
    }

    private Map<String, JsonNode> getCaseDataContentData(String caseFieldId,
                                                         ChangeOrganisationRequest changeOrganisationRequest,
                                                         Map<String, JsonNode> caseDetailsData) {
        Map<String, JsonNode> data = new HashMap<>();

        data.put(caseFieldId, jacksonUtils.convertValue(changeOrganisationRequest, JsonNode.class));

        JacksonUtils.merge(caseDetailsData, data);
        return data;
    }

}
