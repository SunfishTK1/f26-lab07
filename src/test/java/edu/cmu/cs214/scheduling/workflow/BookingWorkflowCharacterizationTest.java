package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.Booking;
import edu.cmu.cs214.scheduling.domain.BookingOutcome;
import edu.cmu.cs214.scheduling.domain.BookingRequest;
import edu.cmu.cs214.scheduling.domain.BookingStore;
import edu.cmu.cs214.scheduling.domain.Member;
import edu.cmu.cs214.scheduling.domain.MembershipTier;
import edu.cmu.cs214.scheduling.domain.Room;
import edu.cmu.cs214.scheduling.domain.TimeSlot;
import edu.cmu.cs214.scheduling.notify.NotificationHub;
import edu.cmu.cs214.scheduling.pricing.PriceCalculator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for BookingWorkflow. Each one pins what the shipped
 * code does today, written before the refactor, whether or not it is what the
 * code ought to do.
 */
class BookingWorkflowCharacterizationTest {

    private static final LocalDateTime MON_8AM = LocalDateTime.of(2026, 10, 5, 8, 0);
    private static final LocalDateTime MON_9AM = LocalDateTime.of(2026, 10, 5, 9, 0);
    private static final LocalDateTime MON_10AM = LocalDateTime.of(2026, 10, 5, 10, 0);

    private BookingStore store;
    private NotificationHub hub;
    private BookingWorkflow workflow;

    @BeforeEach
    void setUp() {
        store = new BookingStore();
        store.addRoom(new Room("C-200", "Cedar Hall", 20));
        store.addMember(new Member("m-1", "Ada", "ada@rooms.example.edu", MembershipTier.BASIC));
        store.addMember(new Member("m-2", "Grace", "grace@rooms.example.edu",
                MembershipTier.PREMIER));
        hub = new NotificationHub();
        workflow = new BookingWorkflow(store, new PriceCalculator(), hub);
    }

    /**
     * A regular booking may start the minute another ends (shipped test
     * regularSubmitAcceptsASlotThatStartsWhenAnotherEnds). A recurring series
     * does not get the same treatment: a week whose slot only touches an
     * existing booking counts as taken and is skipped.
     */
    @Test
    void recurringSubmitSkipsAWeekThatOnlyTouchesAnExistingBooking() {
        workflow.submit(BookingRequest.regular("C-200", "m-2",
                MON_8AM.plusWeeks(1), MON_9AM.plusWeeks(1), 4));

        BookingOutcome outcome = workflow.submit(
                BookingRequest.recurring("C-200", "m-1", MON_9AM, MON_10AM, 3, 6));

        assertTrue(outcome.isAccepted());
        assertEquals(List.of(new TimeSlot(MON_9AM.plusWeeks(1), MON_10AM.plusWeeks(1))),
                outcome.getSkipped());
        assertEquals(List.of(1, 3),
                outcome.getBooked().stream().map(Booking::getOccurrenceIndex).toList());
        assertEquals("series S-1: 2 booked, 1 skipped", outcome.getMessage());
        assertEquals(3, hub.getOutbox().size());
    }
}
