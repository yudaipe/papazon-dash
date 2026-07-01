import type { Message } from "firebase-admin/messaging";

export type ItemStatus = "open" | "done" | string;

export interface PairData {
  master_uid?: string;
  member_uid?: string;
}

export interface ItemData {
  name?: string;
  status?: ItemStatus;
  created_by?: string;
}

export type NotificationType = "item_created" | "item_completed" | "reminder";

interface BuildMessageParams {
  token: string;
  type: NotificationType;
  itemId: string;
  pairId: string;
  itemName?: string;
  priority: "high" | "normal";
}

export function displayItemName(item: ItemData): string {
  const name = item.name?.trim();
  return name && name.length > 0 ? name : "おつかい";
}

export function recipientForCreatedItem(pair: PairData, item: ItemData): string | null {
  if (!pair.member_uid) {
    return null;
  }

  if (item.created_by && item.created_by === pair.member_uid && pair.master_uid) {
    return pair.master_uid;
  }

  return pair.member_uid;
}

export function recipientForCompletedItem(pair: PairData): string | null {
  return pair.master_uid ?? null;
}

export function recipientForReminder(pair: PairData): string | null {
  return pair.member_uid ?? null;
}

export function buildDataMessage(params: BuildMessageParams): Message {
  const itemName = params.itemName?.trim() || "おつかい";

  return {
    token: params.token,
    data: {
      type: params.type,
      itemId: params.itemId,
      itemName,
      pairId: params.pairId,
    },
    notification: {
      title: notificationTitle(params.type),
      body: itemName,
    },
    android: {
      priority: params.priority,
      notification: {
        channelId: "papazon_dash_default",
        priority: params.priority === "high" ? "high" : "default",
      },
    },
  };
}

function notificationTitle(type: NotificationType): string {
  switch (type) {
  case "item_created":
    return "新しいおつかい";
  case "item_completed":
    return "おつかい完了";
  case "reminder":
    return "おつかいリマインダー";
  }
}
