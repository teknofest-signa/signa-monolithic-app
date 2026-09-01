package teknofest.signa.producer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import teknofest.signa.producer.config.OprfProperties;
import teknofest.signa.producer.crypto.OprfProtocolException;
import teknofest.signa.producer.enums.ScreeningStatus;
import teknofest.signa.producer.handler.exception.ApplicationException;
import teknofest.signa.producer.model.dto.screening.ScreeningRequest;
import teknofest.signa.producer.model.dto.screening.ScreeningResponse;
import teknofest.signa.producer.model.entity.ScreeningCheck;
import teknofest.signa.producer.repository.CustomerRepository;
import teknofest.signa.producer.repository.ScreeningCheckRepository;
import teknofest.signa.producer.security.BankPrincipal;

/**
 * Screening is the call that turns a pseudonym into a fact about a person, so
 * what it discloses, what it refuses, and what it writes down are all worth
 * pinning.
 */
class ScreeningServiceTest {

    private static final String KEY_ID = "415cde0523b0611a";
    private static final String PSEUDONYM =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private OprfService oprfService;
    private CustomerRepository customerRepository;
    private ScreeningCheckRepository screeningCheckRepository;
    private ScreeningService screeningService;

    private final BankPrincipal bank =
            new BankPrincipal(UUID.randomUUID(), "bank_test", "Test Bank");

    @BeforeEach
    void setUp() {
        oprfService = mock(OprfService.class);
        customerRepository = mock(CustomerRepository.class);
        screeningCheckRepository = mock(ScreeningCheckRepository.class);

        when(oprfService.isKnownKeyId(anyString())).thenReturn(true);

        screeningService = new ScreeningService(
                oprfService,
                new OprfRateLimiter(new OprfProperties()),
                customerRepository,
                screeningCheckRepository);
    }

    private ScreeningRequest request() {
        return ScreeningRequest.builder().pseudonym(PSEUDONYM).oprfKeyId(KEY_ID).build();
    }

    private void networkHolds(boolean adverse, boolean withCaller) {
        when(customerRepository.existsByPseudonymAndOprfKeyIdAndCustomerStatusIn(
                anyString(), anyString(), anyList())).thenReturn(adverse);
        when(customerRepository.existsByBankIdAndPseudonymAndOprfKeyId(
                any(), anyString(), anyString())).thenReturn(withCaller);
    }

    @Test
    @DisplayName("a person blocked or suspended anywhere in the network comes back FLAGGED")
    void flagsAdverseRecordsAnywhere() {
        networkHolds(true, false);

        ScreeningResponse response = screeningService.screen(bank, request());

        assertEquals(ScreeningStatus.FLAGGED, response.getStatus());
        assertEquals(KEY_ID, response.getOprfKeyId());

        // The caller has never enrolled this person, and still gets the
        // answer. That is the entire point of the platform.
        assertFalse(response.isEnrolledWithYou());
    }

    @Test
    @DisplayName("CLEAR does not distinguish an unknown person from a known good one")
    void clearRevealsNothingAboutEnrolmentElsewhere() {
        networkHolds(false, false);

        ScreeningResponse response = screeningService.screen(bank, request());

        assertEquals(ScreeningStatus.CLEAR, response.getStatus());

        // ScreeningResponse has no field that could carry the difference. If a
        // "known to the network" flag is ever added, it tells every member
        // bank whether a person banks elsewhere, which is not theirs to learn.
        assertFalse(response.isEnrolledWithYou());
    }

    @Test
    @DisplayName("the caller is told about its own enrolment, which it already holds")
    void reportsCallersOwnEnrolment() {
        networkHolds(true, true);

        assertTrue(screeningService.screen(bank, request()).isEnrolledWithYou());
    }

    @Test
    @DisplayName("an unknown key id is an error, never a CLEAR verdict")
    void refusesUnknownKeyRatherThanReportingClear() {
        when(oprfService.isKnownKeyId(anyString())).thenReturn(false);

        // Reporting CLEAR here would be silent and total: a bank pointed at a
        // retired key would wave through every flagged person in the network
        // and see nothing wrong anywhere.
        assertThrows(ApplicationException.class, () -> screeningService.screen(bank, request()));

        verify(customerRepository, never())
                .existsByPseudonymAndOprfKeyIdAndCustomerStatusIn(anyString(), anyString(), anyList());
    }

    @Test
    @DisplayName("a refused check is still audited, so grinding against the quota leaves a trail")
    void auditsRefusals() {
        OprfProperties properties = new OprfProperties();
        properties.getScreening().getRateLimit().setElementsPerMinute(1);

        ScreeningService limited = new ScreeningService(
                oprfService,
                new OprfRateLimiter(properties),
                customerRepository,
                screeningCheckRepository);

        networkHolds(false, false);
        limited.screen(bank, request());

        assertThrows(OprfProtocolException.class, () -> limited.screen(bank, request()));

        ArgumentCaptor<ScreeningCheck> saved = ArgumentCaptor.forClass(ScreeningCheck.class);
        verify(screeningCheckRepository, org.mockito.Mockito.times(2)).save(saved.capture());

        ScreeningCheck refusal = saved.getAllValues().get(1);
        assertFalse(refusal.isAccepted());
        assertTrue(refusal.getRejectionReason().contains("screening"));
        assertEquals(bank.bankId(), refusal.getBankId());
    }

    @Test
    @DisplayName("the audit record carries the verdict and the key, and has nowhere to put the pseudonym")
    void auditRecordsVerdictWithoutThePseudonym() {
        networkHolds(true, false);

        screeningService.screen(bank, request());

        ArgumentCaptor<ScreeningCheck> saved = ArgumentCaptor.forClass(ScreeningCheck.class);
        verify(screeningCheckRepository).save(saved.capture());

        ScreeningCheck record = saved.getValue();
        assertTrue(record.isAccepted());
        assertEquals(Boolean.TRUE, record.getFlagged());
        assertEquals(KEY_ID, record.getKeyId());
        assertEquals(bank.bankId(), record.getBankId());
    }

    @Test
    @DisplayName("matching is scoped to the key the pseudonym was derived under")
    void matchesWithinTheKeyEpoch() {
        networkHolds(true, false);

        screeningService.screen(bank, request());

        // Pseudonyms from two different keys are incomparable. A query that
        // dropped the key id would match nothing and report everyone clear.
        verify(customerRepository).existsByPseudonymAndOprfKeyIdAndCustomerStatusIn(
                eq(PSEUDONYM), eq(KEY_ID), anyList());
    }
}
