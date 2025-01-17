package uk.gov.hmcts.reform.managecase.api.controller;

import com.google.common.collect.Lists;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.managecase.ApplicationParams;
import uk.gov.hmcts.reform.managecase.api.errorhandling.ApiError;
import uk.gov.hmcts.reform.managecase.api.errorhandling.AuthError;
import uk.gov.hmcts.reform.managecase.api.errorhandling.BadRequestException;
import uk.gov.hmcts.reform.managecase.api.errorhandling.CaseRoleAccessException;
import uk.gov.hmcts.reform.managecase.api.payload.CaseAssignedUserRole;
import uk.gov.hmcts.reform.managecase.api.payload.CaseAssignedUserRolesRequest;
import uk.gov.hmcts.reform.managecase.api.payload.CaseAssignedUserRolesResource;
import uk.gov.hmcts.reform.managecase.api.payload.CaseAssignedUserRolesResponse;
import uk.gov.hmcts.reform.managecase.api.payload.CaseAssignedUserRoleWithOrganisation;
import uk.gov.hmcts.reform.managecase.security.SecurityUtils;
import uk.gov.hmcts.reform.managecase.service.cau.CaseAssignedUserRolesOperation;
import uk.gov.hmcts.reform.managecase.service.common.UIDService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import static uk.gov.hmcts.reform.managecase.api.errorhandling.AuthError.AUTHENTICATION_TOKEN_INVALID;
import static uk.gov.hmcts.reform.managecase.api.errorhandling.AuthError.UNAUTHORISED_S2S_SERVICE;
import static uk.gov.hmcts.reform.managecase.api.errorhandling.ValidationError.CASE_ID_INVALID;
import static uk.gov.hmcts.reform.managecase.api.errorhandling.ValidationError.CASE_NOT_FOUND;
import static uk.gov.hmcts.reform.managecase.api.errorhandling.ValidationError.CASE_ROLE_FORMAT_INVALID;
import static uk.gov.hmcts.reform.managecase.api.errorhandling.ValidationError.CLIENT_SERVICE_NOT_AUTHORISED_FOR_OPERATION;
import static uk.gov.hmcts.reform.managecase.api.errorhandling.ValidationError.EMPTY_CASE_ID_LIST;
import static uk.gov.hmcts.reform.managecase.api.errorhandling.ValidationError.EMPTY_CASE_USER_ROLE_LIST;
import static uk.gov.hmcts.reform.managecase.api.errorhandling.ValidationError.ORGANISATION_ID_INVALID;
import static uk.gov.hmcts.reform.managecase.api.errorhandling.ValidationError.USER_ID_INVALID;
import static uk.gov.hmcts.reform.managecase.security.SecurityUtils.SERVICE_AUTHORIZATION;

@RestController
@RequestMapping(path = "/")
public class CaseAssignedUserRolesController {
    
    public static final String ADD_SUCCESS_MESSAGE = "Case-User-Role assignments created successfully";
    public static final String REMOVE_SUCCESS_MESSAGE = "Case-User-Role assignments removed successfully";

    final Pattern caseRolePattern = Pattern.compile("^\\[.+]$");

    private final ApplicationParams applicationParams;
    private final UIDService caseReferenceService;
    private final CaseAssignedUserRolesOperation caseAssignedUserRolesOperation;
    private final SecurityUtils securityUtils;

    @Autowired
    public CaseAssignedUserRolesController(ApplicationParams applicationParams,
                                           UIDService caseReferenceService,
                                           @Qualifier("authorised") CaseAssignedUserRolesOperation
                                               caseAssignedUserRolesOperation,
                                           SecurityUtils securityUtils) {
        this.applicationParams = applicationParams;
        this.caseReferenceService = caseReferenceService;
        this.caseAssignedUserRolesOperation = caseAssignedUserRolesOperation;
        this.securityUtils = securityUtils;
    }



    @PostMapping(
        path = "/case-users"
    )
    @ResponseStatus(HttpStatus.CREATED)
    @ApiOperation(
        value = "Add Case-Assigned Users and Roles"
    )
    @ApiResponses({
        @ApiResponse(
            code = 201,
            message = ADD_SUCCESS_MESSAGE,
            response = CaseAssignedUserRolesResponse.class
        ),
        @ApiResponse(
            code = 400,
            message = "One or more of the following reasons:\n"
                + "1. " + EMPTY_CASE_USER_ROLE_LIST + ", \n"
                + "2. " + CASE_ID_INVALID + ": has to be a valid 16-digit Luhn number, \n"
                + "3. " + USER_ID_INVALID + ": has to be a string of length > 0, \n"
                + "4. " + CASE_ROLE_FORMAT_INVALID + ": has to be a none-empty string in square brackets, \n"
                + "5. " + ORGANISATION_ID_INVALID + ": has to be a non-empty string, when present."
        ),
        @ApiResponse(
            code = 401,
            message = AUTHENTICATION_TOKEN_INVALID
        ),
        @ApiResponse(
            code = 403,
            message = "One of the following reasons:\n"
                + "1. " + UNAUTHORISED_S2S_SERVICE + "\n"
                + "2. " + CLIENT_SERVICE_NOT_AUTHORISED_FOR_OPERATION + "."
        ),
        @ApiResponse(
            code = 404,
            message = CASE_NOT_FOUND
        )
    })

    public ResponseEntity<CaseAssignedUserRolesResponse> addCaseUserRoles(
        @ApiParam(value = "Valid Service-to-Service JWT token for an approved micro-service", required = true)
        @RequestHeader(SERVICE_AUTHORIZATION) String clientS2SToken,
        @ApiParam(value = "List of Case-User-Role assignments to add", required = true)
        @RequestBody CaseAssignedUserRolesRequest caseAssignedUserRolesRequest
    ) {
        validateRequest(clientS2SToken, caseAssignedUserRolesRequest);
        this.caseAssignedUserRolesOperation.addCaseUserRoles(caseAssignedUserRolesRequest.getCaseAssignedUserRoles());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CaseAssignedUserRolesResponse(ADD_SUCCESS_MESSAGE));
    }



    @DeleteMapping(
        path = "/case-users"
    )
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation(
        value = "Remove Case-Assigned Users and Roles"
    )
    @ApiResponses({
        @ApiResponse(
            code = 200,
            message = REMOVE_SUCCESS_MESSAGE,
            response = CaseAssignedUserRolesResponse.class
        ),
        @ApiResponse(
            code = 400,
            message = "One or more of the following reasons:\n"
                + "1. " + EMPTY_CASE_USER_ROLE_LIST + ", \n"
                + "2. " + CASE_ID_INVALID + ": has to be a valid 16-digit Luhn number, \n"
                + "3. " + USER_ID_INVALID + ": has to be a string of length > 0, \n"
                + "4. " + CASE_ROLE_FORMAT_INVALID + ": has to be a none-empty string in square "
                + "brackets, \n"
                + "5. " + ORGANISATION_ID_INVALID + ": has to be a non-empty string, when present."
        ),
        @ApiResponse(
            code = 401,
            message = "Authentication failure due to invalid / expired tokens (IDAM / S2S)."
        ),
        @ApiResponse(
            code = 403,
            message = "One of the following reasons:\n"
                + "1. Unauthorised S2S service \n"
                + "2. " + CLIENT_SERVICE_NOT_AUTHORISED_FOR_OPERATION + "."
        ),
        @ApiResponse(
            code = 404,
            message = CASE_NOT_FOUND
        )
    })
    public ResponseEntity<CaseAssignedUserRolesResponse> removeCaseUserRoles(
        @ApiParam(value = "Valid Service-to-Service JWT token for an approved micro-service", required = true)
        @RequestHeader(SERVICE_AUTHORIZATION) String clientS2SToken,
        @ApiParam(value = "List of Case-User-Role assignments to add", required = true)
        @RequestBody CaseAssignedUserRolesRequest caseAssignedUserRolesRequest
    ) {
        validateRequest(clientS2SToken, caseAssignedUserRolesRequest);
        this.caseAssignedUserRolesOperation.removeCaseUserRoles(caseAssignedUserRolesRequest
                                                                    .getCaseAssignedUserRoles());
        return ResponseEntity.status(HttpStatus.OK).body(new CaseAssignedUserRolesResponse(REMOVE_SUCCESS_MESSAGE));
    }

    @GetMapping(path = "/case-users")
    @ApiOperation(value = "Get Case-Assigned Users and Roles")
    @ApiResponses({
        @ApiResponse(
            code = 200,
            message = "Get Case-Assigned Users and Roles",
            response = CaseAssignedUserRolesResource.class
        ),
        @ApiResponse(
            code = 400,
            message = "One or more of the following reasons:"
                + "\n1) " + CASE_ID_INVALID
                + "\n2) " + EMPTY_CASE_ID_LIST
                + "\n3) " + USER_ID_INVALID,
            response = ApiError.class
        ),
        @ApiResponse(
            code = 401,
            message = AUTHENTICATION_TOKEN_INVALID
        ),
        @ApiResponse(
            code = 403,
            message = "One of the following reasons:"
                + "\n1) " + AuthError.OTHER_USER_CASE_ROLE_ACCESS_NOT_GRANTED
                + "\n2) " + UNAUTHORISED_S2S_SERVICE
        )
    })
    public ResponseEntity<CaseAssignedUserRolesResource> getCaseUserRoles(@RequestParam("case_ids")
                                                                              List<String> caseIds,
                                                                          @RequestParam(value = "user_ids",
                                                                              required = false)
                                                                              Optional<List<String>> optionalUserIds) {
        List<String> userIds = optionalUserIds.orElseGet(Lists::newArrayList);
        validateRequestParams(caseIds, userIds);
        List<CaseAssignedUserRole> caseAssignedUserRoles = this.caseAssignedUserRolesOperation.findCaseUserRoles(caseIds
                                                               .stream()
                                                               .map(Long::valueOf)
                                                               .collect(Collectors
                                                                            .toCollection(ArrayList::new)), userIds);
        return ResponseEntity.ok(new CaseAssignedUserRolesResource(caseAssignedUserRoles));
    }

    private void validateRequestParams(List<String> caseIds, List<String> userIds) {

        List<String> errorMessages =
            Lists.newArrayList("Invalid data provided for the following inputs to the request:");
        if (caseIds == null || caseIds.isEmpty()) {
            errorMessages.add(EMPTY_CASE_ID_LIST);
        } else {
            caseIds.forEach(caseId -> {
                if (!caseReferenceService.validateUID(caseId)) {
                    errorMessages.add(CASE_ID_INVALID);
                }
            });
        }

        userIds.forEach(userId -> {
            if (StringUtils.isAllBlank(userId)) {
                errorMessages.add(USER_ID_INVALID);
            }
        });

        if (errorMessages.size() > 1) {
            var message = String.join("\n", errorMessages);
            throw new BadRequestException(message);
        }
    }

    private void validateRequestParams(CaseAssignedUserRolesRequest addCaseAssignedUserRolesRequest) {

        List<String> errorMessages =
            Lists.newArrayList("Invalid data provided for the following inputs to the request:");

        List<CaseAssignedUserRoleWithOrganisation> caseUserRoles =
            caseAssignedUserRolesToStream(addCaseAssignedUserRolesRequest).collect(Collectors.toList());

        /// case-users: must be none empty
        if (caseUserRoles.isEmpty()) {
            errorMessages.add(EMPTY_CASE_USER_ROLE_LIST);
        } else {
            caseUserRoles.forEach(caseRole -> validateCaseAssignedUserRoleRequest(caseRole, errorMessages));
        }

        if (errorMessages.size() > 1) {
            String message = String.join("\n", errorMessages);
            throw new BadRequestException(message);
        }
    }


    private void validateCaseAssignedUserRoleRequest(CaseAssignedUserRoleWithOrganisation caseRole,
                                                     List<String> errorMessages) {
        // case_id: has to be a valid 16-digit Luhn number
        if (!caseReferenceService.validateUID(caseRole.getCaseDataId())) {
            errorMessages.add(CASE_ID_INVALID);
        }
        // user_id: has to be a string of length > 0
        if (StringUtils.isAllBlank(caseRole.getUserId())) {
            errorMessages.add(USER_ID_INVALID);
        }
        // case_role: has to be a none-empty string in square brackets
        if (caseRole.getCaseRole() == null || !caseRolePattern.matcher(caseRole.getCaseRole()).matches()) {
            errorMessages.add(CASE_ROLE_FORMAT_INVALID);
        }
        // organisation_id: has to be a non-empty string, when present
        if (caseRole.getOrganisationId() != null && caseRole.getOrganisationId().isEmpty()) {
            errorMessages.add(ORGANISATION_ID_INVALID);
        }
    }

    private static Stream<CaseAssignedUserRoleWithOrganisation> caseAssignedUserRolesToStream(
        CaseAssignedUserRolesRequest addCaseAssignedUserRolesRequest) {
        return addCaseAssignedUserRolesRequest != null
            ? Optional.ofNullable(addCaseAssignedUserRolesRequest.getCaseAssignedUserRoles())
            .map(Collection::stream)
            .orElseGet(Stream::empty)
            : Stream.empty();
    }

    private void validateRequest(String clientS2SToken, CaseAssignedUserRolesRequest request) {

        String clientServiceName = securityUtils.getServiceNameFromS2SToken(clientS2SToken);
        if (applicationParams.getAuthorisedServicesForCaseUserRoles().contains(clientServiceName)) {
            validateRequestParams(request);
        } else {
            throw new CaseRoleAccessException(CLIENT_SERVICE_NOT_AUTHORISED_FOR_OPERATION);
        }
    }
}
