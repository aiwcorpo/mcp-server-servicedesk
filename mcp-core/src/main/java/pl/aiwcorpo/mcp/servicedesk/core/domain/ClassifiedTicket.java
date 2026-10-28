package pl.aiwcorpo.mcp.servicedesk.core.domain;

/** A ticket together with the queue it is in right now: what the policy needs to judge it (a ticket inherits its queue's classification). */
public record ClassifiedTicket(Ticket ticket, Queue queue) {

    public ClassifiedTicket {
        if (!ticket.queue().equals(queue.id())) {
            throw new IllegalArgumentException("the queue is not the queue of the ticket");
        }
    }
}
