// Reading follows the user's own rights in the service desk. The group check is here; restricted queues and the
// service account outside the user's queues are closed by the forbids the platform generates for the declared types
// (the server uses the service account, which sees every queue, so those forbids are what limits a user to the queues
// granted to them).

@id("servicedesk-read")
permit (
  principal in UserGroup::"mcp-servicedesk-read",
  action == ServiceDesk::Action::"read",
  resource
)
when { resource is ServiceDesk::Queue || resource is ServiceDesk::Ticket };
