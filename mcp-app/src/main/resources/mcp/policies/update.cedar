// Changes only inside the queues granted by the scope mapping. `principal in resource.scopes` holds when one of the
// user's groups is mapped to the scope of the resource; a ticket carries the scope of its queue.

// Creating a ticket is an update of the queue.
@id("servicedesk-update-queue-in-scope")
permit (
  principal in UserGroup::"mcp-servicedesk-write",
  action == ServiceDesk::Action::"update",
  resource is ServiceDesk::Queue
)
when { principal in resource.scopes };

@id("servicedesk-update-ticket-in-scope")
permit (
  principal in UserGroup::"mcp-servicedesk-write",
  action == ServiceDesk::Action::"update",
  resource is ServiceDesk::Ticket
)
when { principal in resource.scopes };

// Deleting a queue is not offered to agents: there is no permit for delete on ServiceDesk::Queue.
@id("servicedesk-delete-ticket-in-scope")
permit (
  principal in UserGroup::"mcp-servicedesk-destructive",
  action == ServiceDesk::Action::"delete",
  resource is ServiceDesk::Ticket
)
when { principal in resource.scopes };
