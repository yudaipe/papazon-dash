import * as assert from "node:assert/strict";
import { describe, it } from "node:test";
import type { TokenMessage } from "firebase-admin/messaging";
import {
  buildDataMessage,
  displayItemName,
  recipientForCompletedItem,
  recipientForCreatedItem,
  recipientForReminder,
} from "./notifications";

describe("notification helpers", () => {
  it("routes new master-created items to the member", () => {
    assert.equal(
      recipientForCreatedItem(
        { master_uid: "master-1", member_uid: "member-1" },
        { created_by: "master-1", name: "牛乳" },
      ),
      "member-1",
    );
  });

  it("routes member-created items back to the master to avoid self-notifying", () => {
    assert.equal(
      recipientForCreatedItem(
        { master_uid: "master-1", member_uid: "member-1" },
        { created_by: "member-1", name: "卵" },
      ),
      "master-1",
    );
  });

  it("routes completed items to the master", () => {
    assert.equal(recipientForCompletedItem({ master_uid: "master-1", member_uid: "member-1" }), "master-1");
  });

  it("routes reminders to the member", () => {
    assert.equal(recipientForReminder({ master_uid: "master-1", member_uid: "member-1" }), "member-1");
  });

  it("does not route reminders without a member uid", () => {
    assert.equal(recipientForReminder({ master_uid: "master-1" }), null);
  });

  it("builds Android-compatible data and notification payloads", () => {
    const message = buildDataMessage({
      token: "token-1",
      type: "item_created",
      itemId: "item-1",
      pairId: "pair-1",
      itemName: "牛乳",
      priority: "high",
    }) as TokenMessage;

    assert.equal(message.token, "token-1");
    assert.deepEqual(message.data, {
      type: "item_created",
      itemId: "item-1",
      itemName: "牛乳",
      pairId: "pair-1",
    });
    assert.equal(message.android?.priority, "high");
  });

  it("uses a stable fallback item name", () => {
    assert.equal(displayItemName({ name: "   " }), "おつかい");
  });
});
