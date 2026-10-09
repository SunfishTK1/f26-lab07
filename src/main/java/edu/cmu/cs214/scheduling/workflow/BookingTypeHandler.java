package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.Booking;
import edu.cmu.cs214.scheduling.domain.BookingOutcome;
import edu.cmu.cs214.scheduling.domain.BookingRequest;
import edu.cmu.cs214.scheduling.domain.Room;

/**
 * The type-specific half of each {@link BookingWorkflow} operation. The
 * workflow does the shared lookups and early returns, then hands off to the
 * handler registered for the booking's type.
 */
interface BookingTypeHandler {

    /** Submits a request whose room has already been found. */
    BookingOutcome submit(BookingRequest request, Room room);

    /** Releases a booking that exists and is not yet cancelled. */
    boolean cancel(Booking booking, String roomName, boolean adminOverride);

    /** What the holder owes for an existing booking, in dollars. */
    double price(Booking booking);

    /** A one-line summary of an existing booking. */
    String describe(Booking booking, String roomName);
}
