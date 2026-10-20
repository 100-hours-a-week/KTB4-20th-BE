# Open Chat Delivery Recovery Design

## Purpose

Redis Pub/Sub remains the low-latency fan-out path for open chat, while the relational database remains the source of truth. When Redis publication, subscription, or WebSocket delivery is missed, an active room member must be able to recover persisted messages without duplicating messages already rendered.

This design follows the supplied reference's separation of responsibilities—WebSocket/STOMP for real-time delivery, Redis Pub/Sub for cross-instance broadcast, and RDBMS for persistence and history—but adds the ACK and reconnect recovery behavior that the reference does not implement.

## Scope

- Track one delivery checkpoint per user account and regional chat room.
- Expose authenticated APIs to acknowledge a checkpoint and retrieve messages after it.
- Automatically reconnect and resubscribe the chat WebSocket client.
- Recover persisted messages after every successful room subscription.
- Merge live, history, and recovered messages by `messageId`.
- Preserve the existing sender-side `clientMessageId` idempotency behavior.

Out of scope:

- DB Outbox or Redis Streams.
- Device-specific read/delivery state.
- Per-message read receipts or unread counts.
- Messages that failed before the chat-message transaction committed.

## Chosen Approach

Add a nullable `last_delivered_message_id` foreign key to `regional_chat_room_members`. The existing unique user/room membership row is the delivery-checkpoint aggregate, so a separate table and per-recipient receipt rows are unnecessary.

The checkpoint is cumulative and account-scoped. ACK for an older or equal message is an idempotent no-op. ACK for a newer visible message in the same room advances the checkpoint atomically; it can never move backward. A message from another room, a hidden message, or an ACK from an inactive member is rejected.

## Backend Data Model

`regional_chat_room_members.last_delivered_message_id` references `chat_messages.id` and is nullable for members who have never acknowledged a message.

`RegionalChatRoomMember` owns the checkpoint relationship. Repository operations must load an active membership by user public ID and room ID and perform a conditional checkpoint update so concurrent browser sessions cannot regress the stored value.

Rejoining keeps the account-level checkpoint. Messages remain available through ordinary history, while pending recovery resumes after the last acknowledged message. This matches the existing behavior in which an active member can read room history.

## HTTP Contract

### Acknowledge delivery

`PUT /api/regional-chat-rooms/{roomId}/delivery-ack`

Request:

```json
{
  "messageId": "10001"
}
```

Success is idempotent and returns an empty response body through the standard `ApiResponse`. The server derives the user from `Authentication`; it never trusts a client-supplied user ID.

### Retrieve pending deliveries

`GET /api/regional-chat-rooms/{roomId}/pending-deliveries`

The first request starts after the membership checkpoint. When the checkpoint is null, it starts at the membership's current `joined_at`. Results are visible messages in ascending `(created_at, id)` order and use the existing page size of 20.

An optional `afterMessageId` continues from the final message of the preceding page:

`GET /api/regional-chat-rooms/{roomId}/pending-deliveries?afterMessageId=10020`

The response reuses the existing chat message item shape and includes `nextAfterMessageId` plus `hasNext`. Continuation messages must belong to the requested room and must not precede the persisted checkpoint.

## Client Recovery Flow

1. Connect STOMP and subscribe to the room with a receipt header.
2. Buffer live room events until the subscription receipt is received.
3. On the initial room entry, load the existing latest-history page and render it.
4. ACK the newest message in that initial page to establish a bootstrap checkpoint.
5. After every successful subscription, request pending-delivery pages until `hasNext` is false.
6. Merge history, pending messages, and buffered live events by `messageId`, then order them by `createdAt` and `messageId`.
7. ACK the last message after the merged batch has been committed to React state.
8. After recovery completes, merge each live event and ACK it after it is placed in state.
9. Reconnect after five seconds and repeat subscription plus recovery.

If an ACK fails, the UI keeps the message and retries implicitly on the next recovery. If pending recovery fails, the socket remains usable and the next successful subscription retries recovery.

## Correctness and Limitations

- Persisted chat messages are recoverable even when Redis publication or WebSocket delivery is missed.
- Duplicate live/recovery delivery is expected and removed using `messageId`.
- Sender retries remain deduplicated using `clientMessageId`.
- Concurrent ACKs are monotonic, so a delayed older ACK cannot overwrite a newer checkpoint.
- The checkpoint represents account-level delivery, matching the notification feature; it does not guarantee every device has displayed every message.
- This is an at-least-once recovery layer, not durable Redis publication. A connected client may not recover a missed publication until recovery runs again, normally on reconnect or room re-entry. An Outbox, Redis Streams, or periodic reconciliation would be a separate enhancement.

## Error Handling and Observability

- Invalid room/message associations and inactive membership use existing chat authorization/not-found error conventions.
- Backend exceptions caused by recovery processing are captured through the existing global/Sentry path where applicable.
- Frontend malformed WebSocket payloads are ignored; DB recovery remains authoritative.
- Recovery and ACK requests do not display repeated toast errors during reconnect loops.

## Verification

Backend tests cover:

- checkpoint bootstrap and advancement;
- stale ACK idempotency and concurrent monotonic updates;
- rejection of another room's message and inactive membership;
- first and continued pending-delivery pages in ascending order;
- Pub/Sub loss followed by DB recovery;
- controller request and response contracts.

Frontend tests cover:

- reconnect and resubscribe behavior;
- pending-page iteration;
- buffering live events during recovery;
- deduplication across initial history, recovery, and live events;
- ACK only after state integration;
- failed ACK being retried by a later recovery.

Final verification runs the full backend test suite and frontend test, typecheck, lint, and production build commands.
